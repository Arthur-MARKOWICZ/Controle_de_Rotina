package com.controlerotina.mobile.estado

import com.controlerotina.mobile.api.HabitoDoDia
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HabitosViewModelTest {
    private val api = FakeRotinaApi()
    private val sessao = sessaoStore(api)

    private fun vm(scope: kotlinx.coroutines.CoroutineScope) = HabitosViewModel(api, sessao, { HOJE }, scope)

    private val ler = HabitoDoDia(id = "h1", titulo = "Ler", duracaoMinutos = 15, streakAtual = 3)

    @Test
    fun listaHabitosDoDia() = runTest {
        api.habitos = listOf(ler)
        val vm = vm(backgroundScope)
        vm.carregar()
        assertEquals(listOf("Ler"), vm.estado.value.habitos.map { it.titulo })
        assertTrue("habitos:$HOJE" in api.chamadas)
    }

    @Test
    fun listaVazia() = runTest {
        val vm = vm(backgroundScope)
        vm.carregar()
        assertTrue(vm.estado.value.habitos.isEmpty())
        assertFalse(vm.estado.value.carregando)
    }

    @Test
    fun iniciarAbreSessaoAtiva() = runTest {
        api.habitos = listOf(ler)
        val vm = vm(backgroundScope)
        vm.carregar()
        vm.iniciar(ler)
        assertEquals("Ler", sessao.sessao.value?.titulo)
    }

    @Test
    fun concluirMarcaCumpridoComStreakEXp() = runTest {
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
    fun habitoCumpridoNaoAge() = runTest {
        val cumprido = ler.copy(cumprido = true)
        val vm = vm(backgroundScope)
        vm.concluir(cumprido)
        vm.iniciar(cumprido)
        assertTrue(api.chamadas.none { it.startsWith("concluirHabito") || it.startsWith("iniciarHabito") })
    }

    @Test
    fun limparApagaHabitos() = runTest {
        api.habitos = listOf(ler)
        val vm = vm(backgroundScope)
        vm.carregar()
        vm.limpar()
        assertTrue(vm.estado.value.habitos.isEmpty())
    }
}
