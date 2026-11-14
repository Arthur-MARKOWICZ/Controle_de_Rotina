package com.controlerotina.mobile.lembrete

import com.controlerotina.mobile.AppDependencias
import com.controlerotina.mobile.dominio.DestinoLembrete
import com.controlerotina.mobile.dominio.Notificacao
import com.controlerotina.mobile.dominio.TipoLembrete
import com.controlerotina.mobile.sessao.PreferenciasEmMemoria
import com.controlerotina.mobile.sessao.SecureTokenStoreEmMemoria
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Lembretes ligados ao ciclo da sessão no grafo real (design L2, L8), com relógio fixo. */
class LembretesNaSessaoTest {
    private val notificador = FakeNotificador()
    private val agendador = FakeAgendador()
    private val permissao = FakePermissao(concedida = true)
    private val preferencias = PreferenciasEmMemoria()

    // 13/11/2026 08:03 BRT: dentro da janela do resumo das 08:00.
    private val relogio = RelogioFixo(Instant.parse("2026-11-13T11:03:00Z"))

    private var refreshValido = true
    private var refreshes = 0
    private var reusosDeRefresh = 0
    private var cookieAtual = "refresh_token=r1"

    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    /**
     * Backend falso que rotaciona o refresh e, como o real, recusa um refresh já usado
     * (o backend revogaria a família inteira).
     */
    private val engine = MockEngine { req ->
        val autorizado = req.headers[HttpHeaders.Authorization] == "Bearer a2"
        when (req.url.encodedPath) {
            "/auth/login" -> respond(
                """{"accessToken":"a1"}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.SetCookie to listOf("refresh_token=r1; Path=/auth")),
            )
            "/auth/refresh" -> when {
                !refreshValido -> respond("{}", HttpStatusCode.Unauthorized, json)
                req.headers[HttpHeaders.Cookie] != cookieAtual -> {
                    reusosDeRefresh++
                    respond("{}", HttpStatusCode.Unauthorized, json)
                }
                else -> {
                    refreshes++
                    cookieAtual = "refresh_token=r${refreshes + 1}"
                    respond(
                        """{"accessToken":"a2"}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.SetCookie to listOf("$cookieAtual; Path=/auth")),
                    )
                }
            }
            "/auth/logout" -> respond("", HttpStatusCode.NoContent)
            "/me" -> if (autorizado) respond("""{"email":"x@y.z"}""", HttpStatusCode.OK, json) else respond("{}", HttpStatusCode.Unauthorized, json)
            "/planos" -> if (autorizado) {
                respond("""{"status":404,"title":"Not Found"}""", HttpStatusCode.NotFound, json)
            } else {
                respond("{}", HttpStatusCode.Unauthorized, json)
            }
            else -> respond("{}", HttpStatusCode.Unauthorized, json)
        }
    }

    private fun TestScope.deps(store: SecureTokenStoreEmMemoria = SecureTokenStoreEmMemoria()) = AppDependencias(
        engine,
        "http://api.test",
        store,
        preferencias,
        notificador,
        agendador,
        permissao,
        clock = relogio,
        fuso = { BRT },
        escopo = backgroundScope,
    )

    private suspend fun AppDependencias.entrar() {
        sessao.alterarEmail("x@y.z")
        sessao.alterarSenha("s")
        sessao.entrar()
    }

    @Test
    fun loginDeveReagendarOsLembretesLigados() = runTest {
        val deps = deps()
        preferencias.salvar("lembrete.resumo.ligado", "true")
        deps.entrar()
        assertEquals(mapOf(TipoLembrete.RESUMO to Instant.parse("2026-11-14T11:00:00Z")), agendador.agendados)
    }

    @Test
    fun logoutDeveCancelarAgendamentosELimparBandejaMantendoPreferencias() = runTest {
        val deps = deps()
        deps.entrar()
        deps.lembretesVm.ligar(TipoLembrete.PRAZO)
        notificador.exibir(Notificacao(TipoLembrete.PRAZO, "1 tarefa vence amanhã", "X", DestinoLembrete.TAREFAS))

        deps.sessao.sair()

        assertTrue(agendador.agendados.isEmpty())
        assertEquals(1, agendador.cancelamentosTotais)
        assertTrue(notificador.exibidas.isEmpty())
        assertEquals("true", preferencias.ler("lembrete.prazo.ligado"))
    }

    @Test
    fun sessaoExpiradaDuranteOLembreteDeveCancelarTudoSemNotificar() = runTest {
        val deps = deps()
        deps.entrar()
        deps.lembretesVm.ligar(TipoLembrete.RESUMO)
        refreshValido = false
        runCurrent()

        // No horário: a API responde 401, o refresh falha, a sessão expira.
        deps.executorLembrete.executar(TipoLembrete.RESUMO)
        runCurrent()

        assertTrue(notificador.exibidas.isEmpty())
        assertTrue(agendador.agendados.isEmpty())
        assertEquals(1, agendador.cancelamentosTotais)
    }

    @Test
    fun uiELembreteAoMesmoTempoDevemRenovarUmaVezSoSemDeslogar() = runTest {
        // Processo frio (o worker acordou o app): refresh guardado, nenhum access em memória.
        val store = SecureTokenStoreEmMemoria("refresh_token=r1")
        val deps = deps(store)
        preferencias.salvar("lembrete.resumo.ligado", "true")

        val (email, resultado) = listOf(
            async { deps.api.me().email },
            async { deps.executorLembrete.executar(TipoLembrete.RESUMO) },
        ).awaitAll()

        assertEquals("x@y.z", email)
        assertEquals(ResultadoLembrete.Exibida, resultado)
        assertEquals(1, refreshes, "a UI e o lembrete compartilham o Mutex de renovação")
        assertEquals(0, reusosDeRefresh, "nenhum refresh rotacionado pode ser reenviado")
        assertEquals("refresh_token=r2", store.ler())
        assertEquals("Seu dia ainda não tem plano", notificador.exibidas.single().titulo)
    }
}
