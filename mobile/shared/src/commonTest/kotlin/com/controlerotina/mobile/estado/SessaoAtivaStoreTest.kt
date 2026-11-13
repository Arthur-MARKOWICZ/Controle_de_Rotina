package com.controlerotina.mobile.estado

import com.controlerotina.mobile.api.ApiErro
import com.controlerotina.mobile.api.BlocoExecucao
import com.controlerotina.mobile.api.HabitoDoDia
import com.controlerotina.mobile.api.ItemExecucao
import com.controlerotina.mobile.api.SlotExecucaoHabito
import com.controlerotina.mobile.api.StatusExecucao
import com.controlerotina.mobile.api.TarefaResumo
import com.controlerotina.mobile.sessao.PreferenciasEmMemoria
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessaoAtivaStoreTest {
    private val api = FakeRotinaApi()

    @Test
    fun recuperaExecucaoEmAndamentoDoPlano() = runTest {
        api.itensExecucao = listOf(
            ItemExecucao(BlocoExecucao("b1", 25, 1), TarefaResumo("t1", "Estudar"), null, execucao("e1", StatusExecucao.EM_ANDAMENTO)),
            ItemExecucao(BlocoExecucao("b2", 30, 2), TarefaResumo("t2", "Outra"), null, execucao("e2", StatusExecucao.CONCLUIDO)),
        )
        val store = sessaoStore(api)
        store.recarregar()
        val s = store.sessao.value!!
        assertEquals("e1", s.execucaoId)
        assertEquals("Estudar", s.titulo)
        assertEquals(25, s.duracaoMinutos)
        assertEquals("2026-11-12T10:00:00Z", s.iniciadoEm)
    }

    @Test
    fun recuperaSessaoAvulsaDeHabito() = runTest {
        api.habitos = listOf(
            HabitoDoDia(
                id = "h1", titulo = "Ler", duracaoMinutos = 15,
                execucaoAvulsa = SlotExecucaoHabito("b9", "eh1", StatusExecucao.EM_ANDAMENTO, "2026-11-12T09:00:00Z"),
            ),
        )
        val store = sessaoStore(api)
        store.recarregar()
        assertEquals("eh1", store.sessao.value?.execucaoId)
    }

    @Test
    fun sessaoAvulsaDeTarefaSobreviveAoProcessoMortoPeloResumoLocal() = runTest {
        val prefs = PreferenciasEmMemoria()
        sessaoStore(api, prefs).registrarInicio("Tarefa", 30, execucao("et", StatusExecucao.EM_ANDAMENTO), avulsaDeTarefa = true)

        val novoProcesso = sessaoStore(api, prefs)
        novoProcesso.recarregar()
        assertEquals("et", novoProcesso.sessao.value?.execucaoId)
    }

    @Test
    fun sessaoDoPlanoConcluidaEmOutroLugarNaoRessuscitaDoResumoLocal() = runTest {
        val prefs = PreferenciasEmMemoria()
        sessaoStore(api, prefs).registrarInicio("Bloco", 30, execucao("e1", StatusExecucao.EM_ANDAMENTO))
        // API já não tem nada em andamento.
        val novoProcesso = sessaoStore(api, prefs)
        novoProcesso.recarregar()
        assertNull(novoProcesso.sessao.value)
    }

    @Test
    fun concluirMostraXpELimpa() = runTest {
        api.xpConclusao = 20
        val store = sessaoStore(api)
        store.registrarInicio("Bloco", 30, execucao("e1", StatusExecucao.EM_ANDAMENTO))
        val aviso = async(start = CoroutineStart.UNDISPATCHED) { store.avisos.first() }
        store.concluir()
        assertEquals("Concluído! +20 XP", aviso.await())
        assertNull(store.sessao.value)
        assertEquals("concluir:e1", api.chamadas.last())
    }

    @Test
    fun concluirRecusadoPorqueJaTerminouLimpaABarra() = runTest {
        api.falhaEm = mapOf("concluir" to erroHttp(409, "Execução não está em andamento"))
        val store = sessaoStore(api)
        store.registrarInicio("Bloco", 30, execucao("e1", StatusExecucao.EM_ANDAMENTO))
        store.concluir()
        assertNull(store.sessao.value)
    }

    @Test
    fun concluirSemRedeMantemABarra() = runTest {
        api.falhaEm = mapOf("concluir" to ApiErro.Rede(RuntimeException()))
        val store = sessaoStore(api)
        store.registrarInicio("Bloco", 30, execucao("e1", StatusExecucao.EM_ANDAMENTO))
        store.concluir()
        assertEquals("e1", store.sessao.value?.execucaoId)
    }

    @Test
    fun abortarLimpa() = runTest {
        val store = sessaoStore(api)
        store.registrarInicio("Bloco", 30, execucao("e1", StatusExecucao.EM_ANDAMENTO))
        store.abortar()
        assertNull(store.sessao.value)
        assertEquals("abortar:e1", api.chamadas.last())
    }
}
