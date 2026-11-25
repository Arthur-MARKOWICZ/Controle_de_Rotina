package com.controlerotina.mobile.api

import com.controlerotina.mobile.sessao.SecureTokenStoreEmMemoria
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun MockRequestHandleScope.problema(status: HttpStatusCode, titulo: String, detalhe: String): HttpResponseData =
    respond(
        """{"status":${status.value},"title":"$titulo","detail":"$detalhe"}""",
        status,
        headersOf(HttpHeaders.ContentType, "application/problem+json"),
    )

private fun MockRequestHandleScope.json(corpo: String, status: HttpStatusCode = HttpStatusCode.OK): HttpResponseData =
    respond(corpo, status, headersOf(HttpHeaders.ContentType, "application/json"))

class ApiHttpContaExcluidaTest {

    private val requisicoes = mutableListOf<HttpRequestData>()

    private fun cliente(
        store: SecureTokenStoreEmMemoria,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = ApiHttp(
        MockEngine { req ->
            requisicoes += req
            handler(req)
        },
        "http://api.test/",
        store,
    )

    private fun HttpRequestData.caminho() = url.encodedPath

    @Test
    fun contaExcluidaNaoTentaRefreshApagaSessaoEAvisa() = runTest {
        val store = SecureTokenStoreEmMemoria("refresh_token=r1")
        val http = cliente(store) { problema(HttpStatusCode.Unauthorized, TITULO_CONTA_EXCLUIDA, "Esta conta foi excluída.") }
        val aviso = async { http.contaExcluida.first() }

        assertFailsWith<ApiErro.ContaExcluida> { KtorRotinaApi(http).me() }

        aviso.await()
        assertNull(store.ler())
        assertTrue(requisicoes.none { it.caminho() == "/auth/refresh" })
    }

    @Test
    fun duasChamadasConcorrentesComContaExcluidaNuncaRenovam() = runTest {
        val store = SecureTokenStoreEmMemoria("refresh_token=r1")
        val http = cliente(store) { problema(HttpStatusCode.Unauthorized, TITULO_CONTA_EXCLUIDA, "x") }
        val api = KtorRotinaApi(http)

        val resultados = listOf(
            async { runCatching { api.me() } },
            async { runCatching { api.me() } },
        ).awaitAll()

        assertTrue(resultados.all { it.exceptionOrNull() is ApiErro.ContaExcluida })
        assertEquals(0, requisicoes.count { it.caminho() == "/auth/refresh" })
    }

    @Test
    fun contaExcluidaDescobertaDepoisDoRefreshTambemEncerra() = runTest {
        val store = SecureTokenStoreEmMemoria("refresh_token=r1")
        val http = cliente(store) { req ->
            when (req.caminho()) {
                "/auth/refresh" -> respond(
                    """{"accessToken":"a2"}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.SetCookie to listOf("refresh_token=r2; Path=/auth")),
                )
                else -> if (req.headers[HttpHeaders.Authorization] == "Bearer a2") {
                    problema(HttpStatusCode.Unauthorized, TITULO_CONTA_EXCLUIDA, "x")
                } else {
                    json("{}", HttpStatusCode.Unauthorized)
                }
            }
        }

        assertFailsWith<ApiErro.ContaExcluida> { KtorRotinaApi(http).me() }
        assertNull(store.ler())
    }

    @Test
    fun senhaErradaNaExclusaoNaoRenovaNemDerrubaSessao() = runTest {
        val store = SecureTokenStoreEmMemoria("refresh_token=r1")
        val http = cliente(store) { problema(HttpStatusCode.Unauthorized, "Não autorizado", "Credenciais inválidas.") }

        val erro = assertFailsWith<ApiErro.Http> { KtorRotinaApi(http).excluirConta("errada") }

        assertEquals(401, erro.status)
        assertEquals("refresh_token=r1", store.ler())
        assertTrue(requisicoes.none { it.caminho() == "/auth/refresh" })
    }

    @Test
    fun corpoVazioNo401SegueORefreshNormal() = runTest {
        val store = SecureTokenStoreEmMemoria("refresh_token=r1")
        val http = cliente(store) { req ->
            when (req.caminho()) {
                "/auth/refresh" -> json("{}", HttpStatusCode.Unauthorized)
                else -> respond("", HttpStatusCode.Unauthorized)
            }
        }

        assertFailsWith<ApiErro.SessaoExpirada> { KtorRotinaApi(http).me() }
        assertEquals(1, requisicoes.count { it.caminho() == "/auth/refresh" })
    }

    @Test
    fun excluirContaEnviaDeleteComSenhaNoCorpo() = runTest {
        val http = cliente(SecureTokenStoreEmMemoria()) { respond("", HttpStatusCode.NoContent) }

        KtorRotinaApi(http).excluirConta("Senha@123")

        val req = requisicoes.single()
        assertEquals(HttpMethod.Delete, req.method)
        assertEquals("/me", req.caminho())
        assertEquals("""{"senhaAtual":"Senha@123"}""", (req.body as TextContent).text)
    }

    @Test
    fun exportarDadosDevolveOCorpoCru() = runTest {
        val http = cliente(SecureTokenStoreEmMemoria()) { json("""{"versao":1}""") }

        val conteudo = KtorRotinaApi(http).exportarDados()

        assertEquals("""{"versao":1}""", conteudo.decodeToString())
        assertEquals("/me/export", requisicoes.single().caminho())
    }

    @Test
    fun exportarComErroDeServidorViraHttp500() = runTest {
        val http = cliente(SecureTokenStoreEmMemoria()) {
            problema(HttpStatusCode.InternalServerError, "Erro interno", "Falhou.")
        }

        val erro = assertFailsWith<ApiErro.Http> { KtorRotinaApi(http).exportarDados() }

        assertEquals(500, erro.status)
    }
}
