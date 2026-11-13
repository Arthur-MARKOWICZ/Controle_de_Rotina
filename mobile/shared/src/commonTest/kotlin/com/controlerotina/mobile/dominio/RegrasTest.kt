package com.controlerotina.mobile.dominio

import com.controlerotina.mobile.api.SlotExecucaoHabito
import com.controlerotina.mobile.api.HabitoDoDia
import com.controlerotina.mobile.api.StatusExecucao
import com.controlerotina.mobile.api.StatusPlano
import com.controlerotina.mobile.api.StatusTarefa
import com.controlerotina.mobile.estado.tarefa
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class RegrasTest {
    @Test
    fun devePermitirSessaoAvulsaQuandoTarefaPendenteComRestante() {
        assertTrue(podeSessaoAvulsa(tarefa("1", restante = 60)))
        assertTrue(podeSessaoAvulsa(tarefa("1", status = StatusTarefa.ATRASADA)))
        assertTrue(podeSessaoAvulsa(tarefa("1", status = StatusTarefa.EM_PROGRESSO)))
    }

    @Test
    fun naoDevePermitirSessaoAvulsaQuandoBloqueada() {
        assertFalse(podeSessaoAvulsa(tarefa("1", bloqueada = true)))
    }

    @Test
    fun naoDevePermitirSessaoAvulsaQuandoDeletada() {
        assertFalse(podeSessaoAvulsa(tarefa("1", deletada = true)))
    }

    @Test
    fun naoDevePermitirSessaoAvulsaQuandoTemFilhos() {
        assertFalse(podeSessaoAvulsa(tarefa("1", temFilhos = true)))
    }

    @Test
    fun naoDevePermitirSessaoAvulsaQuandoRestanteZero() {
        assertFalse(podeSessaoAvulsa(tarefa("1", restante = 0)))
    }

    @Test
    fun naoDevePermitirSessaoAvulsaQuandoFinalizadaOuCancelada() {
        assertFalse(podeSessaoAvulsa(tarefa("1", status = StatusTarefa.FINALIZADA)))
        assertFalse(podeSessaoAvulsa(tarefa("1", status = StatusTarefa.CANCELADA)))
    }

    @Test
    fun deveOferecerIniciarQuandoPlanoAprovadoSemExecucao() {
        assertEquals(setOf(AcaoBloco.INICIAR), acoesDoBloco(StatusPlano.APROVADO, null))
        assertEquals(setOf(AcaoBloco.INICIAR), acoesDoBloco(StatusPlano.APROVADO, StatusExecucao.ABORTADO))
    }

    @Test
    fun naoDeveOferecerAcoesQuandoPlanoEmRascunho() {
        assertEquals(emptySet(), acoesDoBloco(StatusPlano.RASCUNHO, null))
        assertEquals(emptySet(), acoesDoBloco(StatusPlano.RASCUNHO, StatusExecucao.EM_ANDAMENTO))
    }

    @Test
    fun deveOferecerConcluirEAbortarQuandoBlocoEmAndamento() {
        assertEquals(
            setOf(AcaoBloco.CONCLUIR, AcaoBloco.ABORTAR),
            acoesDoBloco(StatusPlano.APROVADO, StatusExecucao.EM_ANDAMENTO),
        )
    }

    @Test
    fun naoDeveOferecerAcoesQuandoBlocoConcluido() {
        assertEquals(emptySet(), acoesDoBloco(StatusPlano.APROVADO, StatusExecucao.CONCLUIDO))
    }

    @Test
    fun devePermitirAgirNoHabitoQuandoNaoCumprido() {
        assertTrue(podeAgirNoHabito(HabitoDoDia(id = "h1", titulo = "Ler")))
    }

    @Test
    fun naoDevePermitirAgirNoHabitoQuandoCumprido() {
        assertFalse(podeAgirNoHabito(HabitoDoDia(id = "h1", titulo = "Ler", cumprido = true)))
    }

    @Test
    fun naoDevePermitirAgirNoHabitoQuandoEmAndamento() {
        val habito = HabitoDoDia(
            id = "h1",
            titulo = "Ler",
            execucaoAvulsa = SlotExecucaoHabito(status = StatusExecucao.EM_ANDAMENTO, execucaoId = "e1"),
        )
        assertFalse(podeAgirNoHabito(habito))
    }

    @Test
    fun deveHabilitarPresetDentroDoRestante() {
        assertEquals(listOf(15, 25, 30, 45, 60), PRESETS_DURACAO)
        assertTrue(presetHabilitado(30, 30))
        assertEquals(1, duracaoValida(1, 30))
        assertEquals(30, duracaoValida(30, 30))
    }

    @Test
    fun naoDeveHabilitarPresetAcimaDoRestante() {
        assertFalse(presetHabilitado(45, 30))
    }

    @Test
    fun deveRecusarDuracaoZeroNulaOuAcimaDoRestante() {
        assertNull(duracaoValida(0, 30))
        assertNull(duracaoValida(31, 30))
        assertNull(duracaoValida(null, 30))
        assertNull(duracaoValida(1, 0))
    }

    @Test
    fun deveCalcularMinutosRestantesEExcedidosComClockFixo() {
        val inicio = "2026-11-12T10:00:00Z"
        val agora = Instant.parse("2026-11-12T10:10:30Z")
        assertEquals(15, minutosRestantes(inicio, 25, agora))
        assertEquals(0, minutosExcedidos(inicio, 25, agora))
        val depois = Instant.parse("2026-11-12T10:40:00Z")
        assertEquals(0, minutosRestantes(inicio, 25, depois))
        assertEquals(15, minutosExcedidos(inicio, 25, depois))
    }

    @Test
    fun deveDevolverZeroQuandoInicioDaSessaoEInvalido() {
        val agora = Instant.parse("2026-11-12T10:10:30Z")
        assertEquals(0, minutosRestantes("lixo", 25, agora))
        assertEquals(0, minutosExcedidos("lixo", 25, agora))
    }

    @Test
    fun deveFormatarMinutosETratarNegativoComoZero() {
        assertEquals("45min", formatarMinutos(45))
        assertEquals("1h", formatarMinutos(60))
        assertEquals("1h05", formatarMinutos(65))
        assertEquals("0min", formatarMinutos(-3))
    }

    @Test
    fun deveCalcularPrazoComoFimDoDiaNoFusoBRT() {
        val prazo = prazoFimDoDia(
            kotlinx.datetime.LocalDate(2026, 11, 20),
            kotlinx.datetime.TimeZone.of("America/Sao_Paulo"),
        )
        assertEquals("2026-11-21T02:59:00Z", prazo)
    }
}
