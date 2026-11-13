package com.controlerotina.mobile.estado

import com.controlerotina.mobile.api.ItemDecidir
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DecidirViewModelTest {
    private val api = FakeRotinaApi()
    private val sessao = sessaoStore(api)

    @Test
    fun abertosEDecididos() = runTest {
        api.abertos = listOf(itemDecidir("a"))
        api.decididos = listOf(itemDecidir("d", tarefaId = "t"))
        val vm = DecidirViewModel(api, sessao, backgroundScope)
        vm.recarregar()
        assertEquals(listOf("a"), vm.estado.value.itens.map { it.id })
        vm.mostrarDecididos(true)
        assertEquals(listOf("d"), vm.estado.value.itens.map { it.id })
        assertEquals(listOf("decidir:false", "decidir:true"), api.chamadas)
    }

    @Test
    fun listaVazia() = runTest {
        val vm = DecidirViewModel(api, sessao, backgroundScope)
        vm.recarregar()
        assertTrue(vm.estado.value.itens.isEmpty())
        assertFalse(vm.estado.value.carregando)
    }

    @Test
    fun tituloVazioNaoEnvia() = runTest {
        val vm = DecidirViewModel(api, sessao, backgroundScope)
        assertFalse(vm.criar())
        assertEquals("Informe o título", vm.form.value.errosCampo["titulo"])
        assertTrue(api.decidirCriados.isEmpty())
    }

    @Test
    fun capturarSoComTitulo() = runTest {
        val vm = DecidirViewModel(api, sessao, backgroundScope)
        vm.recarregar()
        vm.alterarForm { it.copy(titulo = "Trocar de plano?") }
        assertTrue(vm.criar())
        val pedido = api.decidirCriados.single()
        assertEquals("Trocar de plano?", pedido.titulo)
        assertNull(pedido.descricao)
        assertNull(pedido.decidirAte)
        assertEquals("Trocar de plano?", vm.estado.value.itens.first().titulo)
    }

    @Test
    fun erro400NoCampo() = runTest {
        api.falhaEm = mapOf(
            "criarDecidir" to com.controlerotina.mobile.api.ApiErro.Http(
                400,
                com.controlerotina.mobile.api.ProblemDetail(errors = listOf(com.controlerotina.mobile.api.ErroCampo("decidirAte", "inválida"))),
            ),
        )
        val vm = DecidirViewModel(api, sessao, backgroundScope)
        vm.alterarForm { it.copy(titulo = "X") }
        assertFalse(vm.criar())
        assertEquals("inválida", vm.form.value.errosCampo["decidirAte"])
    }

    private fun detalhe(scope: kotlinx.coroutines.CoroutineScope, lista: DecidirViewModel, promovidos: MutableList<Unit> = mutableListOf()) =
        DecidirDetalheViewModel(api, sessao, scope, aoMudar = lista::aplicar, aoPromover = { promovidos += Unit })

    @Test
    fun limparDataEnviaNull() = runTest {
        val item = itemDecidir("a", decidirAte = "2026-11-20T02:59:00Z")
        api.abertos = listOf(item)
        val lista = DecidirViewModel(api, sessao, backgroundScope)
        lista.recarregar()
        val vm = detalhe(backgroundScope, lista)
        vm.abrir(item)
        vm.alterarForm { it.copy(decidirAte = null, decidirAteRotulo = null) }
        assertTrue(vm.estado.value.alterado)
        assertTrue(vm.salvar())
        assertEquals(mapOf("decidirAte" to JsonNull), api.patches.single().second.toMap())
    }

    @Test
    fun descartarTiraDaLista() = runTest {
        api.abertos = listOf(itemDecidir("a"), itemDecidir("b"))
        val lista = DecidirViewModel(api, sessao, backgroundScope)
        lista.recarregar()
        val vm = detalhe(backgroundScope, lista)
        vm.abrir(api.abertos.first())
        assertTrue(vm.descartar())
        assertTrue("excluirDecidir:a" in api.chamadas)
        assertEquals(listOf("b"), lista.estado.value.itens.map { it.id })
    }

    @Test
    fun promoverTiraDosAbertosEAvisaTarefas() = runTest {
        api.abertos = listOf(itemDecidir("a"))
        val lista = DecidirViewModel(api, sessao, backgroundScope)
        lista.recarregar()
        val promovidos = mutableListOf<Unit>()
        val vm = detalhe(backgroundScope, lista, promovidos)
        vm.abrir(api.abertos.first())
        vm.alterarPromover { it.copy(duracao = "45") }
        assertTrue(vm.promover())
        assertEquals(45, api.promocoes.single().second.duracaoMinutos)
        assertNull(api.promocoes.single().second.prazo)
        assertTrue(lista.estado.value.itens.isEmpty())
        assertEquals(1, promovidos.size)
    }

    @Test
    fun decididoESomenteLeitura() = runTest {
        val vm = detalhe(backgroundScope, DecidirViewModel(api, sessao, backgroundScope))
        vm.abrir(ItemDecidir(id = "d", titulo = "X", tarefaId = "t"))
        assertTrue(vm.estado.value.somenteLeitura)
        vm.alterarForm { it.copy(titulo = "Y") }
        assertFalse(vm.estado.value.alterado)
        assertFalse(vm.salvar())
        assertFalse(vm.descartar())
        assertFalse(vm.promover())
        assertTrue(api.chamadas.isEmpty())
    }
}
