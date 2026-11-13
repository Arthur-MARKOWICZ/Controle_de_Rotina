package com.controlerotina.mobile.estado

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SubtarefasListaTest {
    private val api = FakeRotinaApi()
    private val sessao = sessaoStore(api)

    private fun vm(scope: kotlinx.coroutines.CoroutineScope) = TarefasViewModel(api, sessao, { HOJE }, scope)

    @Test
    fun listaPrincipalPedeSoRaizes() = runTest {
        api.paginas = listOf(listOf(tarefa("1")))
        val vm = vm(backgroundScope)
        vm.recarregar()
        assertTrue(api.filtros.last().somenteRaizes)
    }

    @Test
    fun expandirCarregaUmaVezERecolherNaoRefazChamada() = runTest {
        api.paginas = listOf(listOf(tarefa("pai", temFilhos = true)))
        api.filhosDe = mapOf("pai" to listOf(tarefa("f1", parentId = "pai"), tarefa("f2", parentId = "pai")))
        val vm = vm(backgroundScope)
        vm.recarregar()

        vm.expandir("pai")
        assertEquals(listOf("f1", "f2"), vm.estado.value.filhos["pai"]!!.map { it.id })
        assertTrue("pai" in vm.estado.value.expandidos)

        vm.recolher("pai")
        vm.expandir("pai")
        assertEquals(1, api.chamadas.count { it == "filhos:pai" })
    }

    @Test
    fun recarregarBuscaDeNovoOsFilhosDosPaisAbertos() = runTest {
        api.paginas = listOf(listOf(tarefa("pai", temFilhos = true), tarefa("outro", temFilhos = true)))
        api.filhosDe = mapOf("pai" to listOf(tarefa("f1", parentId = "pai")))
        val vm = vm(backgroundScope)
        vm.recarregar()
        vm.expandir("pai")
        vm.expandir("outro")
        vm.recolher("outro")

        api.filhosDe = mapOf("pai" to listOf(tarefa("f1", parentId = "pai"), tarefa("f3", parentId = "pai")))
        vm.recarregar()
        assertEquals(listOf("f1", "f3"), vm.estado.value.filhos["pai"]!!.map { it.id })
        assertEquals(setOf("pai"), vm.estado.value.expandidos)
        assertTrue("outro" !in vm.estado.value.filhos, "pai recolhido perde o cache")
    }

    @Test
    fun substituirTrocaRaizEFilhaSemRecarregar() = runTest {
        api.paginas = listOf(listOf(tarefa("pai", temFilhos = true)))
        api.filhosDe = mapOf("pai" to listOf(tarefa("f1", parentId = "pai")))
        val vm = vm(backgroundScope)
        vm.recarregar()
        vm.expandir("pai")
        val antes = api.chamadas.size

        vm.substituir(tarefa("f1", parentId = "pai").copy(titulo = "Renomeada"))
        vm.substituir(tarefa("pai", temFilhos = true).copy(titulo = "Pai novo"))
        assertEquals("Renomeada", vm.estado.value.filhos["pai"]!!.single().titulo)
        assertEquals("Pai novo", vm.estado.value.tarefas.single().titulo)
        assertEquals(antes, api.chamadas.size)
    }

    @Test
    fun quickAddEnviaProjetoEscolhido() = runTest {
        val vm = vm(backgroundScope)
        vm.alterarTitulo("Pintar")
        vm.alterarProjeto("p1")
        assertTrue(vm.criar())
        assertEquals("p1", api.criadas.single().projetoId)
    }
}
