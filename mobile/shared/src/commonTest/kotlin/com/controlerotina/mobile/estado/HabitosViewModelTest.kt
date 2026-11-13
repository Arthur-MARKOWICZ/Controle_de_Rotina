package com.controlerotina.mobile.estado

import com.controlerotina.mobile.api.ApiErro
import com.controlerotina.mobile.api.HabitoDoDia
import com.controlerotina.mobile.api.SlotExecucaoHabito
import com.controlerotina.mobile.api.StatusExecucao
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HabitosViewModelTest {
    private val api = FakeRotinaApi()
    private val sessao = sessaoStore(api)

    private fun vm(scope: kotlinx.coroutines.CoroutineScope) = HabitosViewModel(api, sessao, { HOJE }, scope)

    private val ler = HabitoDoDia(id = "h1", titulo = "Ler", duracaoMinutos = 15, streakAtual = 3)

    @Test
    fun deveListarHabitosDoDia() = runTest {
        api.habitos = listOf(ler)
        val vm = vm(backgroundScope)
        vm.carregar()
        assertEquals(listOf("Ler"), vm.estado.value.habitos.map { it.titulo })
        assertNull(vm.estado.value.erro)
        assertTrue("habitos:$HOJE" in api.chamadas)
    }

    @Test
    fun deveManterListaVaziaQuandoNaoHaHabitos() = runTest {
        val vm = vm(backgroundScope)
        vm.carregar()
        assertTrue(vm.estado.value.habitos.isEmpty())
        assertFalse(vm.estado.value.carregando)
        assertNull(vm.estado.value.erro)
    }

    @Test
    fun deveAbrirSessaoAtivaAoIniciar() = runTest {
        api.habitos = listOf(ler)
        val vm = vm(backgroundScope)
        vm.carregar()
        vm.iniciar(ler)
        assertEquals("Ler", sessao.sessao.value?.titulo)
    }

    @Test
    fun deveMarcarCumpridoComStreakEXpAoConcluir() = runTest {
        api.habitos = listOf(ler)
        api.xpConclusao = 10
        val vm = vm(backgroundScope)
        vm.carregar()
        val aviso = async(start = CoroutineStart.UNDISPATCHED) { sessao.avisos.first() }
        vm.concluir(ler)
        val depois = vm.estado.value.habitos.single()
        assertTrue(depois.cumprido)
        assertEquals(4, depois.streakAtual)
        assertEquals("Concluído! +10 XP", aviso.await())
        assertFalse(vm.podeAgir(depois))
    }

    @Test
    fun naoDeveChamarApiQuandoHabitoJaCumprido() = runTest {
        val cumprido = ler.copy(cumprido = true)
        val vm = vm(backgroundScope)
        vm.concluir(cumprido)
        vm.iniciar(cumprido)
        assertTrue(api.chamadas.none { it.startsWith("concluirHabito") || it.startsWith("iniciarHabito") })
        assertTrue(vm.estado.value.habitos.isEmpty())
    }

    @Test
    fun naoDeveChamarApiQuandoHabitoEmAndamento() = runTest {
        val emAndamento = ler.copy(
            execucaoAvulsa = SlotExecucaoHabito(status = StatusExecucao.EM_ANDAMENTO, execucaoId = "e1"),
        )
        val vm = vm(backgroundScope)
        vm.iniciar(emAndamento)
        vm.concluir(emAndamento)
        assertTrue(api.chamadas.none { it.startsWith("iniciarHabito") || it.startsWith("concluirHabito") })
    }

    @Test
    fun deveLimparHabitosSemChamarApiDeNovo() = runTest {
        api.habitos = listOf(ler)
        val vm = vm(backgroundScope)
        vm.carregar()
        val chamadasAntes = api.chamadas.toList()
        vm.limpar()
        assertTrue(vm.estado.value.habitos.isEmpty())
        assertEquals(chamadasAntes, api.chamadas)
    }

    @Test
    fun deveExporMensagem404AoCarregarEManterListaVazia() = runTest {
        api.falhaEm = mapOf("habitos" to erroHttp(404, "Hábitos não encontrados"))
        val vm = vm(backgroundScope)
        vm.carregar()
        assertTrue(vm.estado.value.habitos.isEmpty())
        assertEquals("Hábitos não encontrados", vm.estado.value.erro)
        assertFalse(vm.estado.value.carregando)
    }

    @Test
    fun deveManterListaAnteriorQuandoRedeFalhaNoReload() = runTest {
        api.habitos = listOf(ler)
        val vm = vm(backgroundScope)
        vm.carregar()
        api.falhaEm = mapOf("habitos" to ApiErro.Rede(Exception("timeout")))
        vm.carregar()
        assertEquals(listOf("Ler"), vm.estado.value.habitos.map { it.titulo })
        assertEquals(MSG_SEM_CONEXAO, vm.estado.value.erro)
        assertFalse(vm.estado.value.carregando)
        assertFalse(vm.estado.value.atualizando)
    }

    @Test
    fun deveAvisar409AoConcluirSemMarcarCumprido() = runTest {
        api.habitos = listOf(ler)
        api.falhaEm = mapOf("concluirHabito" to erroHttp(409, "Hábito não elegível"))
        val vm = vm(backgroundScope)
        vm.carregar()
        val aviso = async(start = CoroutineStart.UNDISPATCHED) { sessao.avisos.first() }
        vm.concluir(ler)
        assertEquals("Hábito não elegível", aviso.await())
        assertFalse(vm.estado.value.habitos.single().cumprido)
        assertEquals(3, vm.estado.value.habitos.single().streakAtual)
        assertNull(vm.estado.value.ocupado)
    }

    @Test
    fun deveExporSessaoExpiradaAoCarregarSemInventarHabitos() = runTest {
        api.falhaEm = mapOf("habitos" to ApiErro.SessaoExpirada())
        val vm = vm(backgroundScope)
        vm.carregar()
        assertTrue(vm.estado.value.habitos.isEmpty())
        assertEquals(MSG_SESSAO_EXPIRADA, vm.estado.value.erro)
    }

    @Test
    fun habitoEmAndamentoNaoAge() = runTest {
        val emAndamento = ler.copy(
            execucaoAvulsa = SlotExecucaoHabito(
                blocoId = "b1",
                execucaoId = "eh1",
                status = StatusExecucao.EM_ANDAMENTO,
            ),
        )
        val vm = vm(backgroundScope)
        vm.iniciar(emAndamento)
        vm.concluir(emAndamento)
        assertTrue(api.chamadas.none { it.startsWith("iniciarHabito") || it.startsWith("concluirHabito") })
    }

    @Test
    fun erroAoCarregarExpoeMensagemENaoAlteraLista() = runTest {
        api.falhaEm = mapOf("habitos" to erroHttp(500, "Falha interna"))
        val vm = vm(backgroundScope)
        vm.carregar()
        assertEquals("Falha interna", vm.estado.value.erro)
        assertTrue(vm.estado.value.habitos.isEmpty())
        assertFalse(vm.estado.value.carregando)
    }

    @Test
    fun recusaDaApiAoIniciarAvisaERecarrega() = runTest {
        api.habitos = listOf(ler)
        api.falhaEm = mapOf("iniciarHabito" to erroHttp(409, "Já existe sessão em andamento"))
        val vm = vm(backgroundScope)
        vm.carregar()
        val aviso = async(start = CoroutineStart.UNDISPATCHED) { sessao.avisos.first() }
        vm.iniciar(ler)
        assertEquals("Já existe sessão em andamento", aviso.await())
        assertNull(sessao.sessao.value)
        assertNull(vm.estado.value.ocupado)
        assertTrue("habitos:$HOJE" in api.chamadas.drop(1), "recarrega o card depois da recusa")
    }
}
