package com.controlerotina.mobile.api

import com.controlerotina.mobile.sessao.SecureTokenStoreEmMemoria
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val JSON = "application/json"

private fun MockRequestHandleScope.json(
    corpo: String,
    status: HttpStatusCode = HttpStatusCode.OK,
    setCookie: String? = null,
): HttpResponseData {
    val pares = buildList {
        add(HttpHeaders.ContentType to listOf(JSON))
        if (setCookie != null) add(HttpHeaders.SetCookie to listOf(setCookie))
    }
    return respond(corpo, status, headersOf(*pares.toTypedArray()))
}

private fun HttpRequestData.caminho() = url.encodedPath

class ApiHttpTest {

    private val requisicoes = mutableListOf<HttpRequestData>()

    private fun cliente(
        store: SecureTokenStoreEmMemoria = SecureTokenStoreEmMemoria(),
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): ApiHttp = ApiHttp(
        MockEngine { req ->
            requisicoes += req
            handler(req)
        },
        "http://api.test/",
        store,
    )

    @Test
    fun loginGuardaRefreshESoEnviaCookieEmAuth() = runTest {
        val store = SecureTokenStoreEmMemoria()
        val http = cliente(store) { req ->
            when (req.caminho()) {
                "/auth/login" -> json("""{"accessToken":"a1"}""", setCookie = "refresh_token=r1; Path=/auth; HttpOnly")
                "/me" -> json("""{"email":"x@y.z","nome":"ignorado"}""")
                else -> error("rota inesperada ${req.caminho()}")
            }
        }
        http.login("x@y.z", "segredo")
        val me = KtorRotinaApi(http).me()

        assertEquals("x@y.z", me.email)
        assertEquals("refresh_token=r1", store.ler())
        val login = requisicoes.first { it.caminho() == "/auth/login" }
        assertNull(login.headers[HttpHeaders.Authorization])
        val chamadaMe = requisicoes.first { it.caminho() == "/me" }
        assertEquals("Bearer a1", chamadaMe.headers[HttpHeaders.Authorization])
        assertNull(chamadaMe.headers[HttpHeaders.Cookie], "cookie de refresh não pode sair de /auth")
    }

    @Test
    fun loginInvalidoViraErroHttp401() = runTest {
        val http = cliente {
            json("""{"status":401,"title":"Unauthorized","detail":"Credenciais inválidas"}""", HttpStatusCode.Unauthorized)
        }
        val erro = assertFailsWith<ApiErro.Http> { http.login("x@y.z", "errada") }
        assertEquals(401, erro.status)
    }

    @Test
    fun accessExpiradoRenovaERepeteUmaVez() = runTest {
        val store = SecureTokenStoreEmMemoria("refresh_token=r1")
        val http = cliente(store) { req ->
            when (req.caminho()) {
                "/auth/refresh" -> json("""{"accessToken":"a2"}""", setCookie = "refresh_token=r2; Path=/auth")
                "/me" -> if (req.headers[HttpHeaders.Authorization] == "Bearer a2") {
                    json("""{"email":"x@y.z"}""")
                } else {
                    json("{}", HttpStatusCode.Unauthorized)
                }
                else -> error("rota inesperada")
            }
        }
        assertEquals("x@y.z", KtorRotinaApi(http).me().email)
        assertEquals("refresh_token=r2", store.ler())
        val refresh = requisicoes.single { it.caminho() == "/auth/refresh" }
        assertEquals("refresh_token=r1", refresh.headers[HttpHeaders.Cookie])
    }

    @Test
    fun duas401SimultaneasGeramUmSoRefresh() = runTest {
        val store = SecureTokenStoreEmMemoria("refresh_token=r1")
        val http = cliente(store) { req ->
            when (req.caminho()) {
                "/auth/refresh" -> {
                    delay(50)
                    json("""{"accessToken":"novo"}""", setCookie = "refresh_token=r2; Path=/auth")
                }
                else -> if (req.headers[HttpHeaders.Authorization] == "Bearer novo") {
                    json("""{"email":"x@y.z"}""")
                } else {
                    json("{}", HttpStatusCode.Unauthorized)
                }
            }
        }
        val api = KtorRotinaApi(http)
        val resultados = listOf(async { api.me() }, async { api.me() }).awaitAll()

        assertTrue(resultados.all { it.email == "x@y.z" })
        assertEquals(1, requisicoes.count { it.caminho() == "/auth/refresh" })
    }

    @Test
    fun refreshRejeitadoLimpaSessaoEAvisa() = runTest {
        val store = SecureTokenStoreEmMemoria("refresh_token=revogado")
        val http = cliente(store) { req ->
            when (req.caminho()) {
                "/auth/refresh" -> json("{}", HttpStatusCode.Unauthorized)
                else -> json("{}", HttpStatusCode.Unauthorized)
            }
        }
        val aviso = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { http.sessaoExpirada.first() }
        assertFailsWith<ApiErro.SessaoExpirada> { KtorRotinaApi(http).me() }
        aviso.await()
        assertNull(store.ler())
    }

    @Test
    fun restaurarSemSessaoGuardadaNaoChamaApi() = runTest {
        val http = cliente { error("não deveria chamar") }
        assertEquals(false, http.restaurar())
        assertTrue(requisicoes.isEmpty())
    }

    @Test
    fun logoutSemRedeApagaSessaoLocal() = runTest {
        val store = SecureTokenStoreEmMemoria("refresh_token=r1")
        val http = cliente(store) { throw IOException("sem rede") }
        http.logout()
        assertNull(store.ler())
    }

    @Test
    fun logoutComRedeEnviaCookieELimpa() = runTest {
        val store = SecureTokenStoreEmMemoria("refresh_token=r1")
        val http = cliente(store) { json("", HttpStatusCode.NoContent, setCookie = "refresh_token=; Path=/auth; Max-Age=0") }
        http.logout()
        assertEquals("refresh_token=r1", requisicoes.single().headers[HttpHeaders.Cookie])
        assertNull(store.ler())
    }

    @Test
    fun erroDeRedeViraApiErroRede() = runTest {
        val http = cliente { throw IOException("timeout") }
        assertFailsWith<ApiErro.Rede> { http.login("a@b.c", "x") }
    }

    @Test
    fun planoInexistenteDevolveNulo() = runTest {
        val store = SecureTokenStoreEmMemoria()
        val http = cliente(store) { req ->
            if (req.caminho() == "/auth/login") json("""{"accessToken":"a"}""") else json("{}", HttpStatusCode.NotFound)
        }
        http.login("a@b.c", "x")
        assertNull(KtorRotinaApi(http).plano("2026-11-12"))
        assertEquals("2026-11-12", requisicoes.last().url.parameters["data"])
    }

    @Test
    fun desserializaRespostasReaisDoBackend() = runTest {
        val http = cliente { req ->
            when (req.caminho()) {
                "/auth/login" -> json("""{"accessToken":"a"}""")
                "/planos" -> json(PLANO_REAL)
                "/tarefas" -> json(PAGINA_REAL)
                "/habitos/do-dia" -> json(HABITOS_REAIS)
                "/tarefas" -> json(PAGINA_REAL)
                else -> json(EXECUCAO_CONCLUIDA)
            }
        }
        http.login("a@b.c", "x")
        val api = KtorRotinaApi(http)

        val plano = api.plano("2026-11-12")!!
        assertEquals(StatusPlano.RASCUNHO, plano.status)
        assertEquals("Estudar Kotlin", plano.blocos.first().titulo)
        assertEquals("Ler", plano.blocos[1].titulo)
        assertEquals(1, plano.itensNaoAlocados.size)

        val pagina = api.tarefas(0, 20)
        assertTrue(pagina.ultima)
        assertEquals(45, pagina.content.single().restanteMinutos)
        assertEquals(listOf("PENDENTE", "EM_PROGRESSO", "ATRASADA"), requisicoes.last().url.parameters.getAll("status"))

        val habito = api.habitosDoDia("2026-11-12").single()
        assertEquals(StatusExecucao.EM_ANDAMENTO, habito.emAndamento?.status)

        val conclusao = api.concluirExecucao("e1")
        assertEquals(20, conclusao.xp?.ganhoNesteRequest)
    }

    @Test
    fun problemDetailComErrosDeCampo() = runTest {
        val http = cliente { req ->
            if (req.caminho() == "/auth/login") {
                json("""{"accessToken":"a"}""")
            } else {
                json(
                    """{"type":"about:blank","title":"Dados inválidos","status":400,"detail":"Um ou mais campos estão inválidos.","instance":"/tarefas","errors":[{"property":"titulo","message":"não deve estar em branco"}]}""",
                    HttpStatusCode.BadRequest,
                )
            }
        }
        http.login("a@b.c", "x")
        val erro = assertFailsWith<ApiErro.Http> { KtorRotinaApi(http).criarTarefa(CriarTarefaRequest(" ")) }
        assertEquals("titulo", erro.problema?.errors?.single()?.property)
    }

    @Test
    fun conflito409PropagaStatusETitleSemAlterarSessao() = runTest {
        val store = SecureTokenStoreEmMemoria()
        val http = cliente(store) { req ->
            when (req.caminho()) {
                "/auth/login" -> json("""{"accessToken":"a1"}""", setCookie = "refresh_token=r1; Path=/auth")
                else -> json(
                    """{"status":409,"title":"Plano já aprovado","detail":"O plano já foi aprovado"}""",
                    HttpStatusCode.Conflict,
                )
            }
        }
        http.login("x@y.z", "segredo")
        val cookieAntes = store.ler()
        val erro = assertFailsWith<ApiErro.Http> { KtorRotinaApi(http).aprovarPlano("p1") }
        assertEquals(409, erro.status)
        assertEquals("Plano já aprovado", erro.problema?.title)
        assertEquals("O plano já foi aprovado", erro.problema?.detail)
        assertEquals(cookieAntes, store.ler())
    }

    @Test
    fun jsonInvalidoEm2xxNaoAlteraSessao() = runTest {
        val store = SecureTokenStoreEmMemoria()
        val http = cliente(store) { req ->
            when (req.caminho()) {
                "/auth/login" -> json("""{"accessToken":"a1"}""", setCookie = "refresh_token=r1; Path=/auth")
                else -> respond(
                    "{nao-e-json",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, listOf(JSON)),
                )
            }
        }
        http.login("x@y.z", "segredo")
        assertFailsWith<kotlinx.serialization.SerializationException> { KtorRotinaApi(http).me() }
        assertEquals("refresh_token=r1", store.ler())
    }

    @Test
    fun corpoVazioEm2xxNaoAlteraSessaoNemViraPlanoNulo() = runTest {
        val store = SecureTokenStoreEmMemoria()
        val http = cliente(store) { req ->
            when (req.caminho()) {
                "/auth/login" -> json("""{"accessToken":"a1"}""", setCookie = "refresh_token=r1; Path=/auth")
                else -> respond("", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, listOf(JSON)))
            }
        }
        http.login("x@y.z", "segredo")
        assertFailsWith<kotlinx.serialization.SerializationException> { KtorRotinaApi(http).plano("2026-11-12") }
        assertEquals("refresh_token=r1", store.ler())
    }

    @Test
    fun timeoutDeRedeViraApiErroRedeSemLimparSessao() = runTest {
        val store = SecureTokenStoreEmMemoria("refresh_token=r1")
        val http = cliente(store) { throw IOException("timeout") }
        val erro = assertFailsWith<ApiErro.Rede> { KtorRotinaApi(http).me() }
        assertEquals("Falha de conexão", erro.message)
        assertEquals("refresh_token=r1", store.ler())
    }
}

private val PLANO_REAL = """
{"id":"p1","data":"2026-11-12","versao":1,"status":"RASCUNHO","temConflito":true,
 "mensagem":"Nem tudo coube","propostaId":null,"propostaPendente":false,"geradoEm":"2026-11-12T10:00:00Z",
 "createdAt":"2026-11-12T10:00:00Z","updatedAt":"2026-11-12T10:00:00Z",
 "blocos":[
  {"id":"b1","duracaoMinutos":30,"ordem":1,"origem":"TAREFA","tarefaId":"t1","habitoId":null,
   "tarefa":{"id":"t1","titulo":"Estudar Kotlin","prioridade":"ALTA","status":"PENDENTE","duracaoMinutos":60,
     "prazo":null,"projetoId":null,"projetoNome":null,"esperas":[],"bloqueada":false},"habito":null},
  {"id":"b2","duracaoMinutos":15,"ordem":2,"origem":"HABITO","tarefaId":null,"habitoId":"h1","tarefa":null,
   "habito":{"id":"h1","titulo":"Ler","horarioPreferencial":"07:00:00","streakAtual":3,"diasSemana":["SEGUNDA"]}}
 ],
 "itensNaoAlocados":[{"tipo":"TAREFA","id":"t9","titulo":"Grande","duracaoMinutos":300,"motivo":"SEM_CAPACIDADE"}]}
""".trimIndent()

private val PAGINA_REAL = """
{"content":[{"id":"t1","titulo":"Estudar","descricao":null,"prioridade":"MEDIA","prazo":"2026-11-20T03:00:00Z",
 "status":"PENDENTE","duracaoMinutos":60,"restanteMinutos":45,"ordem":0,"projetoId":null,"projetoNome":null,"tags":[],
 "tagMatchScore":null,"createdAt":"2026-11-01T00:00:00Z","updatedAt":"2026-11-01T00:00:00Z","deletedAt":null,
 "deletada":false,"parentId":null,"parentTitulo":null,"temFilhos":false,"esperas":[],"bloqueada":false}],
 "pageable":{"pageNumber":0,"pageSize":20},"last":true,"totalElements":1,"totalPages":1,"size":20,"number":0,
 "first":true,"numberOfElements":1,"empty":false}
""".trimIndent()

private val HABITOS_REAIS = """
[{"id":"h1","titulo":"Ler","duracaoMinutos":15,"horarioPreferencial":"07:00:00","streakAtual":3,"cumprido":false,
  "noPlanoAprovado":false,
  "execucaoAvulsa":{"blocoId":"b9","execucaoId":"e9","status":"EM_ANDAMENTO","iniciadoEm":"2026-11-12T10:00:00Z"},
  "execucaoNoPlano":null}]
""".trimIndent()

private val EXECUCAO_CONCLUIDA = """
{"id":"e1","blocoPlanoId":"b1","status":"CONCLUIDO","iniciadoEm":"2026-11-12T10:00:00Z",
 "concluidoEm":"2026-11-12T10:30:00Z","abortadoEm":null,
 "xp":{"ganhoNesteRequest":20,"eventos":[{"tipo":"BLOCO_CONCLUIDO","quantidade":20}],
   "progresso":{"xpTotal":120,"nivel":2,"xpNoNivel":20,"xpParaProximoNivel":80}}}
""".trimIndent()
