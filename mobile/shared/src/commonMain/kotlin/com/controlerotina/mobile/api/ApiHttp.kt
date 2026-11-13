package com.controlerotina.mobile.api

import com.controlerotina.mobile.sessao.MudancaRefresh
import com.controlerotina.mobile.sessao.SecureTokenStore
import com.controlerotina.mobile.sessao.lerRefreshDeSetCookie
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

/** Falhas que as telas tratam. Nenhuma mensagem carrega token, cookie ou senha. */
sealed class ApiErro(mensagem: String, causa: Throwable? = null) : Exception(mensagem, causa) {
    class Http(val status: Int, val problema: ProblemDetail?) :
        ApiErro(problema?.detail ?: problema?.title ?: "Erro $status")

    class Rede(causa: Throwable) : ApiErro("Falha de conexão", causa)

    /** Nem o access nem o refresh valem mais: é preciso entrar de novo. */
    class SessaoExpirada : ApiErro("Sessão expirada")
}

val jsonApi: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

/** Operações de sessão que a tela de login e o shell usam (fakes nos testes). */
interface Autenticador {
    val sessaoExpirada: SharedFlow<Unit>
    suspend fun temSessaoGuardada(): Boolean
    suspend fun login(email: String, senha: String)
    suspend fun restaurar(): Boolean
    suspend fun logout()
}

/**
 * Cliente HTTP da API com a sessão do app (ADR-0002, design D3):
 * - access só em memória, enviado como Bearer fora de `/auth/login|refresh`;
 * - refresh guardado cifrado pelo [SecureTokenStore] e enviado como Cookie só em `/auth/ *`;
 * - em 401, uma renovação serializada por [Mutex] e uma repetição da chamada.
 */
class ApiHttp(
    engine: HttpClientEngine,
    baseUrl: String,
    private val store: SecureTokenStore,
) : Autenticador {
    private val base = baseUrl.trimEnd('/')
    private val client = HttpClient(engine) {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 20_000
        }
    }
    private val mutexRenovacao = Mutex()
    private var access: String? = null
    private val _sessaoExpirada = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emite quando a sessão acaba durante o uso e o app precisa voltar ao login. */
    override val sessaoExpirada: SharedFlow<Unit> = _sessaoExpirada.asSharedFlow()

    override suspend fun temSessaoGuardada(): Boolean = store.ler() != null

    override suspend fun login(email: String, senha: String) {
        val corpo = jsonApi.encodeToString(LoginRequest.serializer(), LoginRequest(email, senha))
        val resposta = executar(HttpMethod.Post, "/auth/login", emptyMap(), corpo, bearer = null)
        if (!resposta.status.isSuccess()) throw erroHttp(resposta)
        aplicarSetCookie(resposta)
        access = jsonApi.decodeFromString(AccessTokenResponse.serializer(), resposta.bodyAsText()).accessToken
    }

    /** Renova a sessão guardada no boot. `false` se não há sessão válida. Erro de rede propaga. */
    override suspend fun restaurar(): Boolean {
        if (store.ler() == null) return false
        return renovar(tokenQueFalhou = null, forcar = true)
    }

    /** Sempre apaga a sessão local, mesmo sem rede ou com erro no servidor. */
    override suspend fun logout() {
        try {
            executar(HttpMethod.Post, "/auth/logout", emptyMap(), null, bearer = access)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Sem rede: a sessão local é apagada do mesmo jeito.
        } finally {
            mutexRenovacao.withLock {
                access = null
                store.limpar()
            }
        }
    }

    suspend fun <T> requisitar(
        metodo: HttpMethod,
        caminho: String,
        resposta: DeserializationStrategy<T>,
        query: Map<String, Any?> = emptyMap(),
        corpo: String? = null,
    ): T {
        val r = autenticada(metodo, caminho, query, corpo)
        return jsonApi.decodeFromString(resposta, r.bodyAsText())
    }

    /** Chamada autenticada sem corpo de resposta (ex.: `DELETE` com 204). */
    suspend fun enviar(
        metodo: HttpMethod,
        caminho: String,
        query: Map<String, Any?> = emptyMap(),
        corpo: String? = null,
    ) {
        autenticada(metodo, caminho, query, corpo)
    }

    /** Como [requisitar], mas devolve `null` em 404 (ex.: dia sem plano). */
    suspend fun <T> requisitarOuNulo(
        metodo: HttpMethod,
        caminho: String,
        resposta: DeserializationStrategy<T>,
        query: Map<String, Any?> = emptyMap(),
    ): T? = try {
        requisitar(metodo, caminho, resposta, query)
    } catch (e: ApiErro.Http) {
        if (e.status == HttpStatusCode.NotFound.value) null else throw e
    }

    private suspend fun autenticada(
        metodo: HttpMethod,
        caminho: String,
        query: Map<String, Any?>,
        corpo: String?,
    ): HttpResponse {
        val tokenUsado = access
        var resposta = executar(metodo, caminho, query, corpo, bearer = tokenUsado)
        if (resposta.status == HttpStatusCode.Unauthorized) {
            if (!renovar(tokenQueFalhou = tokenUsado, forcar = false)) {
                sessaoTerminou()
                throw ApiErro.SessaoExpirada()
            }
            resposta = executar(metodo, caminho, query, corpo, bearer = access)
            if (resposta.status == HttpStatusCode.Unauthorized) {
                mutexRenovacao.withLock {
                    access = null
                    store.limpar()
                }
                sessaoTerminou()
                throw ApiErro.SessaoExpirada()
            }
        }
        if (!resposta.status.isSuccess()) throw erroHttp(resposta)
        return resposta
    }

    /**
     * Uma renovação por vez. Se outra chamada já trocou o access enquanto esta esperava,
     * reaproveita o novo em vez de gastar o refresh rotacionado de novo (reuso revoga a família).
     * O novo refresh é gravado antes de liberar o lock.
     */
    private suspend fun renovar(tokenQueFalhou: String?, forcar: Boolean): Boolean = mutexRenovacao.withLock {
        val atual = access
        if (!forcar && atual != null && atual != tokenQueFalhou) return@withLock true
        if (store.ler() == null) {
            access = null
            return@withLock false
        }
        val resposta = executar(HttpMethod.Post, "/auth/refresh", emptyMap(), null, bearer = null)
        when {
            resposta.status.isSuccess() -> {
                aplicarSetCookie(resposta)
                access = jsonApi.decodeFromString(AccessTokenResponse.serializer(), resposta.bodyAsText()).accessToken
                true
            }
            resposta.status == HttpStatusCode.Unauthorized || resposta.status == HttpStatusCode.Forbidden -> {
                access = null
                store.limpar()
                false
            }
            else -> throw erroHttp(resposta)
        }
    }

    private fun sessaoTerminou() {
        _sessaoExpirada.tryEmit(Unit)
    }

    private suspend fun executar(
        metodo: HttpMethod,
        caminho: String,
        query: Map<String, Any?>,
        corpo: String?,
        bearer: String?,
    ): HttpResponse {
        val rotaAuth = caminho.startsWith("/auth/")
        val cookie = if (rotaAuth) store.ler() else null
        return try {
            client.request(base + caminho) {
                method = metodo
                query.forEach { (chave, valor) ->
                    when (valor) {
                        null -> Unit
                        is Iterable<*> -> valor.forEach { parameter(chave, it) }
                        else -> parameter(chave, valor)
                    }
                }
                header(HttpHeaders.Accept, "application/json, application/problem+json")
                val publica = caminho == "/auth/login" || caminho == "/auth/refresh"
                if (bearer != null && !publica) header(HttpHeaders.Authorization, "Bearer $bearer")
                // O cookie de refresh tem Path=/auth: nunca sai para as outras rotas.
                if (cookie != null) header(HttpHeaders.Cookie, cookie)
                if (corpo != null) {
                    contentType(ContentType.Application.Json)
                    setBody(corpo)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ApiErro.Rede(e)
        }
    }

    private suspend fun aplicarSetCookie(resposta: HttpResponse) {
        when (val mudanca = lerRefreshDeSetCookie(resposta.headers.getAll(HttpHeaders.SetCookie).orEmpty())) {
            is MudancaRefresh.Novo -> store.salvar(mudanca.cookie.cabecalho)
            MudancaRefresh.Limpo -> store.limpar()
            null -> Unit
        }
    }

    private suspend fun erroHttp(resposta: HttpResponse): ApiErro.Http {
        val problema = runCatching {
            jsonApi.decodeFromString(ProblemDetail.serializer(), resposta.bodyAsText())
        }.getOrNull()
        return ApiErro.Http(resposta.status.value, problema)
    }
}
