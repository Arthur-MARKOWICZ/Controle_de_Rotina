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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

private fun MockRequestHandleScope.json(
    corpo: String,
    status: HttpStatusCode = HttpStatusCode.OK,
    setCookie: String? = null,
): HttpResponseData {
    val pares = buildList {
        add(HttpHeaders.ContentType to listOf("application/json"))
        if (setCookie != null) add(HttpHeaders.SetCookie to listOf(setCookie))
    }
    return respond(corpo, status, headersOf(*pares.toTypedArray()))
}

/** `GET /tarefas` com filtro de prazo e status (lembrete D-1, change mobile-lembretes-locais). */
class FiltroPrazoApiTest {
    private val requisicoes = mutableListOf<HttpRequestData>()
    private val store = SecureTokenStoreEmMemoria("refresh_token=r1")

    private fun api(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        KtorRotinaApi(
            ApiHttp(
                MockEngine { req ->
                    requisicoes += req
                    if (req.url.encodedPath == "/auth/refresh") {
                        json("""{"accessToken":"a2"}""", setCookie = "refresh_token=r2; Path=/auth")
                    } else {
                        handler(req)
                    }
                },
                "http://api.test",
                store,
            ),
        )

    private val filtroAmanha = FiltroTarefas(
        status = listOf(StatusTarefa.PENDENTE, StatusTarefa.EM_PROGRESSO),
        prazoDe = "2026-11-14T03:00:00Z",
        prazoAte = "2026-11-15T02:59:59.999Z",
    )

    private fun ultimaTarefas() = requisicoes.last { it.url.encodedPath == "/tarefas" }

    @Test
    fun deveEnviarPrazoEStatusELerTotalElements() = runTest {
        val api = api { json(PAGINA_COM_TOTAL) }
        val pagina = api.tarefas(0, 50, filtroAmanha)

        val req = ultimaTarefas()
        assertEquals("2026-11-14T03:00:00Z", req.url.parameters["prazoDe"])
        assertEquals("2026-11-15T02:59:59.999Z", req.url.parameters["prazoAte"])
        assertEquals(listOf("PENDENTE", "EM_PROGRESSO"), req.url.parameters.getAll("status"))
        assertEquals(5L, pagina.totalElements)
        assertEquals("Relatório", pagina.content.first().titulo)
    }

    @Test
    fun naoDeveEnviarPrazoQuandoNulo() = runTest {
        val api = api { json(PAGINA_VAZIA) }
        api.tarefas(0, 20, FiltroTarefas())
        val req = ultimaTarefas()
        assertNull(req.url.parameters["prazoDe"])
        assertNull(req.url.parameters["prazoAte"])
        assertEquals(listOf("PENDENTE", "EM_PROGRESSO", "ATRASADA"), req.url.parameters.getAll("status"))
    }

    @Test
    fun deveTratarTotalElementsAusenteComoNulo() = runTest {
        val pagina = api { json(PAGINA_VAZIA) }.tarefas(0, 50, filtroAmanha)
        assertNull(pagina.totalElements)
        assertEquals(emptyList(), pagina.content)
    }

    @Test
    fun deveRenovarEm401ERepetirComOMesmoFiltro() = runTest {
        val api = api { req ->
            if (req.headers[HttpHeaders.Authorization] == "Bearer a2") json(PAGINA_COM_TOTAL) else json("{}", HttpStatusCode.Unauthorized)
        }
        val pagina = api.tarefas(0, 50, filtroAmanha)
        assertEquals(5L, pagina.totalElements)
        val chamadas = requisicoes.filter { it.url.encodedPath == "/tarefas" }
        assertEquals(2, chamadas.size)
        assertEquals("2026-11-14T03:00:00Z", chamadas.last().url.parameters["prazoDe"])
        assertEquals("refresh_token=r2", store.ler())
    }

    @Test
    fun deveLancarHttp400ComTitleQuandoPrazoInvalido() = runTest {
        val api = api {
            json("""{"status":400,"title":"Parâmetro inválido","detail":"prazoDe inválido"}""", HttpStatusCode.BadRequest)
        }
        val erro = assertFailsWith<ApiErro.Http> { api.tarefas(0, 50, filtroAmanha) }
        assertEquals(400, erro.status)
        assertEquals("Parâmetro inválido", erro.problema?.title)
        assertEquals("refresh_token=r1", store.ler())
    }

    @Test
    fun deveLancarHttp404ENaoTratarComoListaVazia() = runTest {
        val api = api { json("""{"status":404,"title":"Not Found"}""", HttpStatusCode.NotFound) }
        val erro = assertFailsWith<ApiErro.Http> { api.tarefas(0, 50, filtroAmanha) }
        assertEquals(404, erro.status)
        assertEquals("Not Found", erro.problema?.title)
    }

    @Test
    fun deveLancarHttp500SemAlterarSessao() = runTest {
        val api = api { json("""{"status":500,"title":"Internal Server Error"}""", HttpStatusCode.InternalServerError) }
        val erro = assertFailsWith<ApiErro.Http> { api.tarefas(0, 50, filtroAmanha) }
        assertEquals(500, erro.status)
        assertEquals("refresh_token=r1", store.ler())
    }

    @Test
    fun deveLancarSerializationQuandoProxyDevolveHtml() = runTest {
        val api = api { respond("<!doctype html><html></html>", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html")) }
        assertFailsWith<kotlinx.serialization.SerializationException> { api.tarefas(0, 50, filtroAmanha) }
        assertEquals("refresh_token=r1", store.ler())
    }

    @Test
    fun deveLancarSessaoExpiradaQuandoRefreshRejeitado() = runTest {
        val api = KtorRotinaApi(
            ApiHttp(
                MockEngine { req ->
                    if (req.url.encodedPath == "/auth/refresh") json("{}", HttpStatusCode.Unauthorized) else json("{}", HttpStatusCode.Unauthorized)
                },
                "http://api.test",
                store,
            ),
        )
        assertFailsWith<ApiErro.SessaoExpirada> { api.tarefas(0, 50, filtroAmanha) }
        assertNull(store.ler())
    }
}

private val PAGINA_VAZIA = """{"content":[],"number":0,"totalPages":0,"last":true}"""

private val PAGINA_COM_TOTAL = """
{"content":[{"id":"t1","titulo":"Relatório","prioridade":"ALTA","prazo":"2026-11-15T02:59:00Z","status":"PENDENTE",
 "duracaoMinutos":30,"restanteMinutos":30,"temFilhos":false,"bloqueada":false}],
 "number":0,"totalPages":1,"last":true,"totalElements":5,"size":50}
""".trimIndent()
