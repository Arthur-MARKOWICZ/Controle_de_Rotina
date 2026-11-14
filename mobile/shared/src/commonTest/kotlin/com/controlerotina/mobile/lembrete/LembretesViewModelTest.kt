package com.controlerotina.mobile.lembrete

import com.controlerotina.mobile.dominio.TipoLembrete
import com.controlerotina.mobile.sessao.PreferenciasEmMemoria
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class LembretesViewModelTest {
    private val memoria = PreferenciasEmMemoria()
    private val prefs = PreferenciasLembretes(memoria)
    private val agendador = FakeAgendador()
    private val permissao = FakePermissao()

    // 13/11/2026 07:00 BRT
    private val relogio = RelogioFixo(Instant.parse("2026-11-13T10:00:00Z"))
    private val vm = LembretesViewModel(prefs, agendador, permissao, relogio) { BRT }

    @Test
    fun deveComecarComOsDoisDesligadosNosHorariosPadrao() = runTest {
        vm.carregar()
        val e = vm.estado.value
        assertTrue(e.carregado)
        assertEquals(EstadoLembrete(false, LocalTime(8, 0)), e[TipoLembrete.RESUMO])
        assertEquals(EstadoLembrete(false, LocalTime(19, 0)), e[TipoLembrete.PRAZO])
        assertFalse(e.bloqueado)
        assertTrue(agendador.agendados.isEmpty())
    }

    @Test
    fun deveLigarPedindoPermissaoEAgendarHoje() = runTest {
        vm.carregar()
        vm.ligar(TipoLembrete.RESUMO)
        assertEquals(1, permissao.pedidos)
        assertTrue(vm.estado.value[TipoLembrete.RESUMO].ligado)
        assertTrue(prefs.ligado(TipoLembrete.RESUMO))
        assertEquals(Instant.parse("2026-11-13T11:00:00Z"), agendador.agendados[TipoLembrete.RESUMO])
    }

    @Test
    fun naoDevePedirPermissaoDeNovoQuandoJaConcedida() = runTest {
        permissao.concedida = true
        vm.ligar(TipoLembrete.PRAZO)
        assertEquals(0, permissao.pedidos)
        assertEquals(Instant.parse("2026-11-13T22:00:00Z"), agendador.agendados[TipoLembrete.PRAZO])
    }

    @Test
    fun mudarHorarioDeveSubstituirOAgendamentoAnterior() = runTest {
        permissao.concedida = true
        vm.ligar(TipoLembrete.RESUMO)
        vm.mudarHorario(TipoLembrete.RESUMO, LocalTime(7, 30))
        assertEquals(Instant.parse("2026-11-13T10:30:00Z"), agendador.agendados[TipoLembrete.RESUMO])
        assertEquals(LocalTime(7, 30), prefs.horario(TipoLembrete.RESUMO))
        assertEquals(LocalTime(7, 30), vm.estado.value[TipoLembrete.RESUMO].horario)
    }

    @Test
    fun mudarHorarioDesligadoSoSalvaSemAgendar() = runTest {
        vm.mudarHorario(TipoLembrete.PRAZO, LocalTime(20, 0))
        assertTrue(agendador.agendados.isEmpty())
        assertEquals(LocalTime(20, 0), prefs.horario(TipoLembrete.PRAZO))
    }

    @Test
    fun desligarUmTipoNaoAfetaOOutro() = runTest {
        permissao.concedida = true
        vm.ligar(TipoLembrete.RESUMO)
        vm.ligar(TipoLembrete.PRAZO)
        vm.desligar(TipoLembrete.PRAZO)
        assertNull(agendador.agendados[TipoLembrete.PRAZO])
        assertTrue(TipoLembrete.RESUMO in agendador.agendados)
        assertFalse(prefs.ligado(TipoLembrete.PRAZO))
        assertTrue(vm.estado.value[TipoLembrete.RESUMO].ligado)
    }

    @Test
    fun permissaoNegadaDeveManterDesligadoEBloqueado() = runTest {
        permissao.respostaDoUsuario = false
        vm.carregar()
        vm.ligar(TipoLembrete.RESUMO)
        assertTrue(vm.estado.value.bloqueado)
        assertFalse(vm.estado.value[TipoLembrete.RESUMO].ligado)
        assertFalse(prefs.ligado(TipoLembrete.RESUMO))
        assertTrue(agendador.agendados.isEmpty())
    }

    @Test
    fun permissaoRevogadaDepoisDeveAparecerComoBloqueado() = runTest {
        permissao.concedida = true
        vm.ligar(TipoLembrete.RESUMO)
        permissao.concedida = false
        vm.carregar()
        assertTrue(vm.estado.value.bloqueado)
        assertTrue(vm.estado.value[TipoLembrete.RESUMO].ligado)
    }

    @Test
    fun reagendarLigadosSoAgendaOsLigados() = runTest {
        prefs.salvarLigado(TipoLembrete.PRAZO, true)
        vm.reagendarLigados()
        assertEquals(setOf(TipoLembrete.PRAZO), agendador.agendados.keys)
    }

    @Test
    fun horarioInvalidoNasPreferenciasDeveCairNoPadrao() = runTest {
        memoria.salvar("lembrete.resumo.horario", "25:99")
        vm.carregar()
        assertEquals(LocalTime(8, 0), vm.estado.value[TipoLembrete.RESUMO].horario)
    }

    @Test
    fun recusaDeveContinuarBloqueadaQuandoTelaRecarregaAoVoltarDoDialogo() = runTest {
        permissao.respostaDoUsuario = false
        vm.carregar()
        vm.ligar(TipoLembrete.RESUMO)
        // O diálogo do sistema pausa a Activity; o onResume recarrega a tela.
        vm.carregar()
        assertTrue(vm.estado.value.bloqueado)
        assertFalse(vm.estado.value[TipoLembrete.RESUMO].ligado)
    }

    @Test
    fun liberarNasConfiguracoesDepoisDaRecusaDeveTirarOBloqueio() = runTest {
        permissao.respostaDoUsuario = false
        vm.ligar(TipoLembrete.RESUMO)
        permissao.concedida = true
        vm.carregar()
        assertFalse(vm.estado.value.bloqueado)
    }
}
