package com.controlerotina.mobile.dominio

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
    fun elegibilidadeDeSessaoAvulsa() {
        assertTrue(podeSessaoAvulsa(tarefa("1", restante = 60)))
        assertTrue(podeSessaoAvulsa(tarefa("1", status = StatusTarefa.ATRASADA)))
        assertFalse(podeSessaoAvulsa(tarefa("1", bloqueada = true)), "bloqueada")
        assertFalse(podeSessaoAvulsa(tarefa("1", temFilhos = true)), "pai com filhos")
        assertFalse(podeSessaoAvulsa(tarefa("1", restante = 0)), "sem restante")
        assertFalse(podeSessaoAvulsa(tarefa("1", deletada = true)), "deletada")
        assertFalse(podeSessaoAvulsa(tarefa("1", status = StatusTarefa.FINALIZADA)), "finalizada")
        assertFalse(podeSessaoAvulsa(tarefa("1", status = StatusTarefa.CANCELADA)), "cancelada")
    }

    @Test
    fun acoesPorEstadoDoBloco() {
        assertEquals(emptySet(), acoesDoBloco(StatusPlano.RASCUNHO, null))
        assertEquals(setOf(AcaoBloco.INICIAR), acoesDoBloco(StatusPlano.APROVADO, null))
        assertEquals(setOf(AcaoBloco.INICIAR), acoesDoBloco(StatusPlano.APROVADO, StatusExecucao.ABORTADO))
        assertEquals(setOf(AcaoBloco.CONCLUIR, AcaoBloco.ABORTAR), acoesDoBloco(StatusPlano.APROVADO, StatusExecucao.EM_ANDAMENTO))
        assertEquals(emptySet(), acoesDoBloco(StatusPlano.APROVADO, StatusExecucao.CONCLUIDO))
    }

    @Test
    fun pickerDeDuracaoRespeitaLimites() {
        assertEquals(listOf(15, 25, 30, 45, 60), PRESETS_DURACAO)
        assertTrue(presetHabilitado(30, 30))
        assertFalse(presetHabilitado(45, 30))
        assertEquals(1, duracaoValida(1, 30))
        assertEquals(30, duracaoValida(30, 30))
        assertNull(duracaoValida(0, 30))
        assertNull(duracaoValida(31, 30))
        assertNull(duracaoValida(null, 30))
        assertNull(duracaoValida(1, 0))
    }

    @Test
    fun tempoDaSessaoAtiva() {
        val inicio = "2026-11-12T10:00:00Z"
        val agora = Instant.parse("2026-11-12T10:10:30Z")
        assertEquals(15, minutosRestantes(inicio, 25, agora))
        assertEquals(0, minutosExcedidos(inicio, 25, agora))
        val depois = Instant.parse("2026-11-12T10:40:00Z")
        assertEquals(0, minutosRestantes(inicio, 25, depois))
        assertEquals(15, minutosExcedidos(inicio, 25, depois))
        assertEquals(0, minutosRestantes("lixo", 25, agora))
    }

    @Test
    fun formatacao() {
        assertEquals("45min", formatarMinutos(45))
        assertEquals("1h", formatarMinutos(60))
        assertEquals("1h05", formatarMinutos(65))
        assertEquals("0min", formatarMinutos(-3))
    }

    @Test
    fun prazoEFimDoDiaNoFuso() {
        val prazo = prazoFimDoDia(kotlinx.datetime.LocalDate(2026, 11, 20), kotlinx.datetime.TimeZone.of("America/Sao_Paulo"))
        assertEquals("2026-11-21T02:59:00Z", prazo)
    }
}
