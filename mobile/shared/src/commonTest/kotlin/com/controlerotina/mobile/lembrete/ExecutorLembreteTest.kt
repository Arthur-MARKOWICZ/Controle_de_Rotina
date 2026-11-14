package com.controlerotina.mobile.lembrete

import com.controlerotina.mobile.api.ApiErro
import com.controlerotina.mobile.api.StatusExecucao
import com.controlerotina.mobile.api.StatusPlano
import com.controlerotina.mobile.api.StatusTarefa
import com.controlerotina.mobile.api.Tarefa
import com.controlerotina.mobile.dominio.DestinoLembrete
import com.controlerotina.mobile.dominio.TipoLembrete
import com.controlerotina.mobile.estado.FakeRotinaApi
import com.controlerotina.mobile.estado.bloco
import com.controlerotina.mobile.estado.erroHttp
import com.controlerotina.mobile.estado.plano
import com.controlerotina.mobile.sessao.PreferenciasEmMemoria
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalTime
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class ExecutorLembreteTest {
    private val api = FakeRotinaApi()
    private val prefs = PreferenciasLembretes(PreferenciasEmMemoria())
    private val notificador = FakeNotificador()
    private val agendador = FakeAgendador()

    // 13/11/2026 08:03 BRT
    private val relogio = RelogioFixo(Instant.parse("2026-11-13T11:03:00Z"))
    private val executor = ExecutorLembrete(api, prefs, notificador, agendador, relogio) { BRT }

    private val amanhaAsOito = Instant.parse("2026-11-14T11:00:00Z")

    private suspend fun ligar(tipo: TipoLembrete, horario: LocalTime = tipo.horarioPadrao) {
        prefs.salvarLigado(tipo, true)
        prefs.salvarHorario(tipo, horario)
    }

    private suspend fun assertSemEfeito(tipo: TipoLembrete) {
        assertTrue(notificador.exibidas.isEmpty(), "não pode exibir notificação")
        assertNull(prefs.ultimoDia(tipo), "não pode marcar o dia como disparado")
    }

    // ---- caminho feliz ----

    @Test
    fun deveExibirResumoDoPlanoAprovadoEReagendarParaAmanha() = runTest {
        ligar(TipoLembrete.RESUMO)
        api.planoAtual = plano(StatusPlano.APROVADO, bloco("b1", 1, "Escrever"), bloco("b2", 2))

        assertEquals(ResultadoLembrete.Exibida, executor.executar(TipoLembrete.RESUMO))

        val n = notificador.exibidas.single()
        assertEquals("2 blocos (1h) pendentes. Próximo: Escrever", n.texto)
        assertEquals(DestinoLembrete.PLANO, n.destino)
        assertEquals(listOf("plano:2026-11-13", "execucoes:2026-11-13"), api.chamadas)
        assertEquals("2026-11-13", prefs.ultimoDia(TipoLembrete.RESUMO))
        assertEquals(amanhaAsOito, agendador.agendados[TipoLembrete.RESUMO])
    }

    @Test
    fun deveExibirAvisoDePrazoComFiltroDeAmanha() = runTest {
        // 19:10 BRT
        relogio.agora = Instant.parse("2026-11-13T22:10:00Z")
        ligar(TipoLembrete.PRAZO)
        api.paginas = listOf(listOf(Tarefa(id = "t1", titulo = "Relatório", prazo = "2026-11-15T02:59:00Z", status = StatusTarefa.PENDENTE)))

        assertEquals(ResultadoLembrete.Exibida, executor.executar(TipoLembrete.PRAZO))

        assertEquals("1 tarefa vence amanhã", notificador.exibidas.single().titulo)
        val filtro = api.filtros.single()
        assertEquals("2026-11-14T03:00:00Z", filtro.prazoDe)
        assertEquals("2026-11-15T02:59:59.999Z", filtro.prazoAte)
        assertEquals(listOf(StatusTarefa.PENDENTE, StatusTarefa.EM_PROGRESSO), filtro.status)
        assertEquals(Instant.parse("2026-11-14T22:00:00Z"), agendador.agendados[TipoLembrete.PRAZO])
    }

    @Test
    fun naoDeveBuscarExecucoesQuandoPlanoEmRascunho() = runTest {
        ligar(TipoLembrete.RESUMO)
        api.planoAtual = plano(StatusPlano.RASCUNHO, bloco("b1", 1))
        executor.executar(TipoLembrete.RESUMO)
        assertEquals(listOf("plano:2026-11-13"), api.chamadas)
        assertEquals("Plano pronto para aprovar", notificador.exibidas.single().titulo)
    }

    // ---- anomalias ----

    @Test
    fun naoDeveConsultarApiNemReagendarQuandoDesligado() = runTest {
        assertEquals(ResultadoLembrete.Pulada(MotivoPulo.DESLIGADO), executor.executar(TipoLembrete.RESUMO))
        assertTrue(api.chamadas.isEmpty())
        assertTrue(agendador.agendados.isEmpty())
        assertSemEfeito(TipoLembrete.RESUMO)
    }

    @Test
    fun naoDeveExibirDuasVezesNoMesmoDia() = runTest {
        ligar(TipoLembrete.RESUMO)
        api.planoAtual = null
        executor.executar(TipoLembrete.RESUMO)
        relogio.agora = Instant.parse("2026-11-13T11:30:00Z")

        assertEquals(ResultadoLembrete.Pulada(MotivoPulo.JA_DISPAROU), executor.executar(TipoLembrete.RESUMO))
        assertEquals(1, notificador.exibidas.size)
        assertEquals(listOf("plano:2026-11-13"), api.chamadas)
        assertEquals(amanhaAsOito, agendador.agendados[TipoLembrete.RESUMO])
    }

    @Test
    fun devePularForaDaToleranciaEReagendarParaAmanha() = runTest {
        ligar(TipoLembrete.RESUMO)
        // 10:30 BRT: 2h30 de atraso
        relogio.agora = Instant.parse("2026-11-13T13:30:00Z")

        assertEquals(ResultadoLembrete.Pulada(MotivoPulo.FORA_DA_JANELA), executor.executar(TipoLembrete.RESUMO))
        assertTrue(api.chamadas.isEmpty())
        assertSemEfeito(TipoLembrete.RESUMO)
        assertEquals(amanhaAsOito, agendador.agendados[TipoLembrete.RESUMO])
    }

    @Test
    fun execucaoAdiantadaNaoDeveReagendarParaHoje() = runTest {
        ligar(TipoLembrete.RESUMO)
        // 07:58 BRT, dentro dos 5 min de adiantamento aceitos
        relogio.agora = Instant.parse("2026-11-13T10:58:00Z")
        executor.executar(TipoLembrete.RESUMO)
        assertEquals(1, notificador.exibidas.size)
        assertEquals(amanhaAsOito, agendador.agendados[TipoLembrete.RESUMO])
    }

    @Test
    fun naoDeveNotificarQuandoNadaAAvisar() = runTest {
        ligar(TipoLembrete.RESUMO)
        api.planoAtual = plano(StatusPlano.APROVADO, bloco("b1", 1))
        api.itensExecucao = listOf(
            com.controlerotina.mobile.api.ItemExecucao(
                com.controlerotina.mobile.api.BlocoExecucao("b1", 30, 1),
                execucao = com.controlerotina.mobile.estado.execucao("e1", StatusExecucao.CONCLUIDO, "b1"),
            ),
        )
        assertEquals(ResultadoLembrete.Pulada(MotivoPulo.NADA_A_AVISAR), executor.executar(TipoLembrete.RESUMO))
        assertSemEfeito(TipoLembrete.RESUMO)
        assertEquals(amanhaAsOito, agendador.agendados[TipoLembrete.RESUMO])
    }

    @Test
    fun naoDeveConsultarApiSemPermissaoDeNotificacao() = runTest {
        ligar(TipoLembrete.RESUMO)
        notificador.permite = false
        assertEquals(ResultadoLembrete.Pulada(MotivoPulo.SEM_PERMISSAO), executor.executar(TipoLembrete.RESUMO))
        assertTrue(api.chamadas.isEmpty())
        assertSemEfeito(TipoLembrete.RESUMO)
        assertEquals(amanhaAsOito, agendador.agendados[TipoLembrete.RESUMO])
    }

    // ---- erros esperados ----

    @Test
    fun devePularSemRedeEManterAgendamento() = runTest {
        ligar(TipoLembrete.RESUMO)
        api.falhaEm = mapOf("plano" to ApiErro.Rede(RuntimeException("offline")))
        assertEquals(ResultadoLembrete.Pulada(MotivoPulo.FALHA_API), executor.executar(TipoLembrete.RESUMO))
        assertSemEfeito(TipoLembrete.RESUMO)
        assertEquals(amanhaAsOito, agendador.agendados[TipoLembrete.RESUMO])
    }

    @Test
    fun devePularEm500EManterAgendamento() = runTest {
        ligar(TipoLembrete.PRAZO)
        relogio.agora = Instant.parse("2026-11-13T22:00:00Z")
        api.falhaEm = mapOf("tarefas" to erroHttp(500))
        assertEquals(ResultadoLembrete.Pulada(MotivoPulo.FALHA_API), executor.executar(TipoLembrete.PRAZO))
        assertSemEfeito(TipoLembrete.PRAZO)
        assertEquals(Instant.parse("2026-11-14T22:00:00Z"), agendador.agendados[TipoLembrete.PRAZO])
    }

    @Test
    fun devePularQuandoRespostaNaoEJson() = runTest {
        ligar(TipoLembrete.RESUMO)
        api.falhaEm = mapOf("plano" to SerializationException("<!doctype html>"))
        assertEquals(ResultadoLembrete.Pulada(MotivoPulo.FALHA_API), executor.executar(TipoLembrete.RESUMO))
        assertSemEfeito(TipoLembrete.RESUMO)
    }

    @Test
    fun naoDeveReagendarQuandoSessaoExpirou() = runTest {
        ligar(TipoLembrete.RESUMO)
        api.falhaEm = mapOf("plano" to ApiErro.SessaoExpirada())
        assertEquals(ResultadoLembrete.Pulada(MotivoPulo.SEM_SESSAO), executor.executar(TipoLembrete.RESUMO))
        assertSemEfeito(TipoLembrete.RESUMO)
        assertTrue(agendador.agendados.isEmpty(), "o logout cancela; o próximo login reagenda")
    }
}
