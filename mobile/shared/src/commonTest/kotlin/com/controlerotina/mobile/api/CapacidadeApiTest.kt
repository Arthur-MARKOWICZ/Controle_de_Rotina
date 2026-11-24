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
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
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

private fun HttpRequestData.corpo(): String = (body as? TextContent)?.text.orEmpty()

private val SEMANA_REAL = """
[{"id":"c1","diaSemana":"SEGUNDA","horas":4,"ativo":true,
  "createdAt":"2026-11-01T00:00:00Z","updatedAt":"2026-11-01T00:00:00Z"}]
""".trimIndent()

/** Matriz padrão de cliente de API para as rotas de capacidade (T12). */
class CapacidadeApiTest {
    private val requisicoes = mutableListOf<HttpRequestData>()
    private val store = SecureTokenStoreEmMemoria()

    private suspend fun api(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): KtorRotinaApi {
        val http = ApiHttp(
            MockEngine { req ->
                requisicoes += req
                when (req.url.encodedPath) {
                    "/auth/login" -> json("""{"accessToken":"a1"}""", setCookie = "refresh_token=r1; Path=/auth")
                    "/auth/refresh" -> json("""{"accessToken":"a2"}""", setCookie = "refresh_token=r2; Path=/auth")
                    else -> handler(req)
                }
            },
            "http://api.test",
            store,
        )
        http.login("a@b.c", "x")
        return KtorRotinaApi(http)
    }

    @Test
    fun listarSemanaDesserializaFormatoReal() = runTest {
        val semana = api { json(SEMANA_REAL) }.capacidadesSemanais()
        assertEquals(listOf(CapacidadeSemanal("c1", DiaSemana.SEGUNDA, 4, true)), semana)
        assertEquals("/capacidades/semanal", requisicoes.last().url.encodedPath)
    }

    @Test
    fun criarEnviaItensEmLoteNoFormatoDoContrato() = runTest {
        val api = api { json(SEMANA_REAL, HttpStatusCode.Created) }
        api.criarCapacidadesSemanais(listOf(CriarCapacidadeSemanalItem(DiaSemana.SEGUNDA, 4)))
        val req = requisicoes.last()
        assertEquals(HttpMethod.Post, req.method)
        assertEquals("""{"itens":[{"diaSemana":"SEGUNDA","horas":4}]}""", req.corpo())
    }

    @Test
    fun atualizarEnviaPatchSoComHoras() = runTest {
        val api = api { json(SEMANA_REAL.removePrefix("[").removeSuffix("]")) }
        val atualizada = api.atualizarCapacidadeSemanal("c1", 6)
        val req = requisicoes.last()
        assertEquals(HttpMethod.Patch, req.method)
        assertEquals("/capacidades/semanal/c1", req.url.encodedPath)
        assertEquals("""{"horas":6}""", req.corpo())
        assertEquals("c1", atualizada.id)
    }

    @Test
    fun resolvidaEnviaIntervaloEAceitaHorasNulas() = runTest {
        val api = api { json("""[{"data":"2026-11-15","horas":null,"origem":"VAZIO"}]""") }
        val dias = api.capacidadeResolvida("2026-11-15", "2026-11-15")
        assertEquals("2026-11-15", requisicoes.last().url.parameters["dataDe"])
        assertEquals("2026-11-15", requisicoes.last().url.parameters["dataAte"])
        assertNull(dias.single().horas)
        assertEquals(OrigemCapacidade.VAZIO, dias.single().origem)
    }

    @Test
    fun erro400TrazErrosDeCampo() = runTest {
        val api = api {
            json(
                """{"status":400,"title":"Dados inválidos","errors":[{"property":"itens[0].horas","message":"must be >= 0"}]}""",
                HttpStatusCode.BadRequest,
            )
        }
        val erro = assertFailsWith<ApiErro.Http> { api.criarCapacidadesSemanais(listOf(CriarCapacidadeSemanalItem(DiaSemana.SEGUNDA, -1))) }
        assertEquals(400, erro.status)
        assertEquals("itens[0].horas", erro.problema?.errors?.single()?.property)
    }

    @Test
    fun erro401RenovaERepeteUmaVez() = runTest {
        var tentativas = 0
        val api = api {
            tentativas++
            if (tentativas == 1) json("{}", HttpStatusCode.Unauthorized) else json(SEMANA_REAL)
        }
        val semana = api.capacidadesSemanais()
        assertEquals(1, semana.size)
        assertEquals(2, tentativas)
        assertEquals(1, requisicoes.count { it.url.encodedPath == "/auth/refresh" })
        assertEquals("refresh_token=r2", store.ler())
    }

    @Test
    fun erro404NoPatchViraHttpComTitle() = runTest {
        val api = api {
            json("""{"status":404,"title":"Capacidade não encontrada"}""", HttpStatusCode.NotFound)
        }
        val erro = assertFailsWith<ApiErro.Http> { api.atualizarCapacidadeSemanal("x", 4) }
        assertEquals(404, erro.status)
        assertEquals("Capacidade não encontrada", erro.problema?.title)
    }

    @Test
    fun erro409DeConflitoPropagaTitleSemAlterarSessao() = runTest {
        val api = api {
            json(
                """{"status":409,"title":"Capacidade semanal já existe","detail":"Já existe capacidade ativa para SEGUNDA"}""",
                HttpStatusCode.Conflict,
            )
        }
        val erro = assertFailsWith<ApiErro.Http> { api.criarCapacidadesSemanais(listOf(CriarCapacidadeSemanalItem(DiaSemana.SEGUNDA, 4))) }
        assertEquals(409, erro.status)
        assertEquals("Capacidade semanal já existe", erro.problema?.title)
        assertEquals("refresh_token=r1", store.ler())
    }

    @Test
    fun erro500SemCorpoViraHttpComStatus() = runTest {
        val api = api { respond("", HttpStatusCode.InternalServerError) }
        val erro = assertFailsWith<ApiErro.Http> { api.capacidadeResolvida("2026-11-12", "2026-11-12") }
        assertEquals(500, erro.status)
        assertEquals("Erro 500", erro.message)
    }

    @Test
    fun timeoutViraErroDeRede() = runTest {
        val api = api { throw IOException("timeout") }
        assertFailsWith<ApiErro.Rede> { api.capacidadesSemanais() }
        assertEquals("refresh_token=r1", store.ler())
    }

    @Test
    fun htmlDoProxyEm2xxFalhaNaLeituraSemAlterarSessao() = runTest {
        val api = api {
            respond("<!doctype html><html></html>", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html"))
        }
        assertFailsWith<SerializationException> { api.capacidadesSemanais() }
        assertEquals("refresh_token=r1", store.ler())
    }
}
