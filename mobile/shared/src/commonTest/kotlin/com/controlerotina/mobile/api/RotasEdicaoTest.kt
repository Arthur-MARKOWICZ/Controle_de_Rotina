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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun MockRequestHandleScope.json(corpo: String, status: HttpStatusCode = HttpStatusCode.OK): HttpResponseData =
    respond(corpo, status, headersOf(HttpHeaders.ContentType, "application/json"))

private fun HttpRequestData.corpo(): String = (body as? TextContent)?.text.orEmpty()

/** Rotas de edição, subtarefas, projetos e Decidir contra o formato real do backend. */
class RotasEdicaoTest {
    private val requisicoes = mutableListOf<HttpRequestData>()

    private suspend fun api(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): KtorRotinaApi {
        val http = ApiHttp(
            MockEngine { req ->
                requisicoes += req
                if (req.url.encodedPath == "/auth/login") json("""{"accessToken":"a"}""") else handler(req)
            },
            "http://api.test",
            SecureTokenStoreEmMemoria(),
        )
        http.login("a@b.c", "x")
        return KtorRotinaApi(http)
    }

    @Test
    fun filtrosDeTarefasViramQuery() = runTest {
        val api = api { json(PAGINA_VAZIA) }
        api.tarefas(0, 20, FiltroTarefas(somenteRaizes = true))
        assertEquals("true", requisicoes.last().url.parameters["somenteRaizes"])
        assertNull(requisicoes.last().url.parameters["parentId"])

        api.tarefas(0, 100, FiltroTarefas(parentId = "pai"))
        assertEquals("pai", requisicoes.last().url.parameters["parentId"])
        assertNull(requisicoes.last().url.parameters["somenteRaizes"])

        api.tarefas(0, 100, FiltroTarefas(projetoId = "p1"))
        assertEquals("p1", requisicoes.last().url.parameters["projetoId"])
        assertEquals(listOf("PENDENTE", "EM_PROGRESSO", "ATRASADA"), requisicoes.last().url.parameters.getAll("status"))
    }

    @Test
    fun patchDeTarefaEnviaSoOCorpoDado() = runTest {
        val api = api { json(TAREFA_REAL) }
        val corpo = JsonObject(mapOf("titulo" to JsonPrimitive("Novo"), "projetoId" to JsonNull))
        val tarefa = api.atualizarTarefa("t1", corpo)
        val req = requisicoes.last()
        assertEquals(HttpMethod.Patch, req.method)
        assertEquals("/tarefas/t1", req.url.encodedPath)
        assertEquals("""{"titulo":"Novo","projetoId":null}""", req.corpo())
        assertEquals("pai1", tarefa.parentId)
        assertEquals("Pai", tarefa.parentTitulo)
        assertEquals("Detalhes", tarefa.descricao)
    }

    @Test
    fun criarSubtarefaEnviaParentId() = runTest {
        val api = api { json(TAREFA_REAL) }
        api.criarTarefa(CriarTarefaRequest("Filha", parentId = "pai1"))
        assertEquals("""{"titulo":"Filha","parentId":"pai1"}""", requisicoes.last().corpo())
    }

    @Test
    fun desserializaProjetosReais() = runTest {
        val api = api { json(PROJETOS_REAIS) }
        val projetos = api.projetos(StatusProjeto.ATIVO)
        assertEquals("ATIVO", requisicoes.last().url.parameters["status"])
        assertEquals("Casa", projetos.single().nome)
        assertEquals(StatusProjeto.ATIVO, projetos.single().status)
    }

    @Test
    fun desserializaDecidirReal() = runTest {
        val api = api { json(DECIDIR_REAL) }
        val itens = api.decidir(promovidos = false)
        assertEquals("false", requisicoes.last().url.parameters["promovidos"])
        assertEquals("2026-11-20T02:59:00Z", itens.single().decidirAte)
        assertTrue(!itens.single().promovido)
    }

    @Test
    fun descartarDecidirAceita204SemCorpo() = runTest {
        val api = api { respond("", HttpStatusCode.NoContent) }
        api.excluirDecidir("d1")
        assertEquals(HttpMethod.Delete, requisicoes.last().method)
        assertEquals("/decidir/d1", requisicoes.last().url.encodedPath)
    }

    @Test
    fun promoverEnviaSoCamposPreenchidos() = runTest {
        val api = api { json("""{"id":"d1","titulo":"X","tarefaId":"t9","tags":[]}""") }
        val item = api.promoverDecidir("d1", PromoverDecidirRequest(duracaoMinutos = 30))
        assertEquals("""{"duracaoMinutos":30}""", requisicoes.last().corpo())
        assertTrue(item.promovido)
    }
}

private val PAGINA_VAZIA = """{"content":[],"number":0,"totalPages":0,"last":true}"""

private val TAREFA_REAL = """
{"id":"t2","titulo":"Novo","descricao":"Detalhes","prioridade":"ALTA","prazo":null,"status":"PENDENTE",
 "duracaoMinutos":30,"restanteMinutos":30,"ordem":1,"projetoId":null,"projetoNome":null,"tags":[],"tagMatchScore":null,
 "createdAt":"2026-11-01T00:00:00Z","updatedAt":"2026-11-01T00:00:00Z","deletedAt":null,"deletada":false,
 "parentId":"pai1","parentTitulo":"Pai","temFilhos":false,"esperas":[],"bloqueada":false}
""".trimIndent()

private val PROJETOS_REAIS = """
{"content":[{"id":"p1","nome":"Casa","descricao":null,"status":"ATIVO","ordem":0,"tags":[],"tagMatchScore":null,
 "createdAt":"2026-11-01T00:00:00Z","updatedAt":"2026-11-01T00:00:00Z","esperas":[],"bloqueada":false}],
 "number":0,"totalPages":1,"last":true,"size":100}
""".trimIndent()

private val DECIDIR_REAL = """
{"content":[{"id":"d1","titulo":"Trocar de plano de celular?","descricao":null,"decidirAte":"2026-11-20T02:59:00Z",
 "projetoId":null,"tarefaId":null,"tags":[],"tagMatchScore":null,"createdAt":"2026-11-01T00:00:00Z",
 "updatedAt":"2026-11-01T00:00:00Z"}],"number":0,"totalPages":1,"last":true}
""".trimIndent()
