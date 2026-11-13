package com.controlerotina.mobile.estado

import com.controlerotina.mobile.api.ApiErro
import com.controlerotina.mobile.api.ErroCampo
import com.controlerotina.mobile.api.ProblemDetail
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TarefasViewModelTest {
    private val api = FakeRotinaApi()
    private val sessao = sessaoStore(api)

    private fun vm(scope: kotlinx.coroutines.CoroutineScope) = TarefasViewModel(api, sessao, { HOJE }, scope)

    @Test
    fun paginacaoAnexaProximaPaginaEParaNoFim() = runTest {
        api.paginas = listOf(listOf(tarefa("1"), tarefa("2")), listOf(tarefa("3")))
        val vm = vm(backgroundScope)
        vm.recarregar()
        assertEquals(listOf("1", "2"), vm.estado.value.tarefas.map { it.id })
        assertFalse(vm.estado.value.fim)

        vm.carregarMais()
        assertEquals(listOf("1", "2", "3"), vm.estado.value.tarefas.map { it.id })
        assertTrue(vm.estado.value.fim)

        vm.carregarMais()
        assertEquals(2, api.chamadas.count { it.startsWith("tarefas:") }, "não busca depois do fim")
    }

    @Test
    fun listaVaziaTerminaCarregamento() = runTest {
        val vm = vm(backgroundScope)
        vm.recarregar()
        assertTrue(vm.estado.value.tarefas.isEmpty())
        assertFalse(vm.estado.value.carregandoInicial)
    }

    @Test
    fun criarSoComTituloInsereNoTopo() = runTest {
        api.paginas = listOf(listOf(tarefa("1")))
        val vm = vm(backgroundScope)
        vm.recarregar()
        vm.alterarTitulo("  Comprar pão  ")
        assertTrue(vm.criar())
        assertEquals("Comprar pão", api.criadas.single().titulo)
        assertNull(api.criadas.single().duracaoMinutos)
        assertNull(api.criadas.single().prazo)
        assertEquals("Comprar pão", vm.estado.value.tarefas.first().titulo)
        assertEquals("", vm.form.value.titulo)
    }

    @Test
    fun tituloVazioNaoEnvia() = runTest {
        val vm = vm(backgroundScope)
        assertFalse(vm.criar())
        assertEquals("Informe o título", vm.form.value.errosCampo["titulo"])
        assertTrue(api.criadas.isEmpty())
    }

    @Test
    fun tituloLimitadoA120() = runTest {
        val vm = vm(backgroundScope)
        vm.alterarTitulo("a".repeat(200))
        assertEquals(120, vm.form.value.titulo.length)
    }

    @Test
    fun erro400MapeiaParaOCampo() = runTest {
        api.falhaEm = mapOf(
            "criar" to ApiErro.Http(
                400,
                ProblemDetail(400, "Dados inválidos", errors = listOf(ErroCampo("duracaoMinutos", "deve ser maior que 0"))),
            ),
        )
        val vm = vm(backgroundScope)
        vm.alterarTitulo("X")
        vm.alterarDuracao("0")
        assertFalse(vm.criar())
        assertEquals("deve ser maior que 0", vm.form.value.errosCampo["duracaoMinutos"])
    }

    @Test
    fun iniciarEnviaDuracaoEAbreSessao() = runTest {
        val t = tarefa("1", restante = 80)
        api.paginas = listOf(listOf(t))
        val vm = vm(backgroundScope)
        vm.recarregar()
        vm.executarSessao(t, AcaoSessaoTarefa.INICIAR, 30)
        assertTrue("iniciarTarefa:1:30" in api.chamadas)
        assertEquals("e-1", sessao.sessao.value?.execucaoId)
        assertTrue(sessao.sessao.value!!.avulsaDeTarefa)
    }

    @Test
    fun concluirComRestante() = runTest {
        val t = tarefa("1", restante = 20)
        val vm = vm(backgroundScope)
        vm.executarSessao(t, AcaoSessaoTarefa.CONCLUIR, 20)
        assertTrue("concluirTarefa:1:20" in api.chamadas)
    }

    @Test
    fun duracaoAcimaDoRestanteNaoEnvia() = runTest {
        val vm = vm(backgroundScope)
        vm.executarSessao(tarefa("1", restante = 20), AcaoSessaoTarefa.INICIAR, 30)
        assertTrue(api.chamadas.none { it.startsWith("iniciarTarefa") })
    }

    @Test
    fun recusaDaApiRecarregaOCardComoBloqueado() = runTest {
        val t = tarefa("1")
        api.paginas = listOf(listOf(t))
        api.falhaEm = mapOf("iniciarTarefa" to erroHttp(409, "Tarefa bloqueada por espera"))
        api.tarefaPorId = { tarefa(it, bloqueada = true) }
        val vm = vm(backgroundScope)
        vm.recarregar()
        vm.executarSessao(t, AcaoSessaoTarefa.INICIAR, 30)
        assertTrue(vm.estado.value.tarefas.single().bloqueada)
        assertFalse(vm.podeSessao(vm.estado.value.tarefas.single()))
        assertNull(vm.estado.value.ocupado)
    }

    @Test
    fun limparApagaListaEFormulario() = runTest {
        api.paginas = listOf(listOf(tarefa("1")))
        val vm = vm(backgroundScope)
        vm.recarregar()
        vm.alterarTitulo("rascunho")
        vm.limpar()
        assertTrue(vm.estado.value.tarefas.isEmpty())
        assertEquals("", vm.form.value.titulo)
    }
}
