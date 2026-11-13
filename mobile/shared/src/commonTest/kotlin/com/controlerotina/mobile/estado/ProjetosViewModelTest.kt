package com.controlerotina.mobile.estado

import com.controlerotina.mobile.api.ErroCampo
import com.controlerotina.mobile.api.ApiErro
import com.controlerotina.mobile.api.ProblemDetail
import com.controlerotina.mobile.api.StatusProjeto
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjetosViewModelTest {
    private val api = FakeRotinaApi()
    private val sessao = sessaoStore(api)

    @Test
    fun trocaDeFiltroBuscaOStatusCerto() = runTest {
        api.projetosPorStatus = mapOf(
            StatusProjeto.ATIVO to listOf(projeto("a")),
            StatusProjeto.ARQUIVADO to listOf(projeto("x", StatusProjeto.ARQUIVADO)),
        )
        val vm = ProjetosViewModel(api, sessao, backgroundScope)
        vm.recarregar()
        assertEquals(listOf("a"), vm.estado.value.projetos.map { it.id })
        vm.filtrar(StatusProjeto.ARQUIVADO)
        assertEquals(listOf("x"), vm.estado.value.projetos.map { it.id })
        assertEquals(listOf("projetos:ATIVO", "projetos:ARQUIVADO"), api.chamadas)
    }

    @Test
    fun listaVazia() = runTest {
        val vm = ProjetosViewModel(api, sessao, backgroundScope)
        vm.recarregar()
        assertTrue(vm.estado.value.projetos.isEmpty())
        assertFalse(vm.estado.value.carregando)
    }

    @Test
    fun nomeVazioNaoEnvia() = runTest {
        val vm = ProjetosViewModel(api, sessao, backgroundScope)
        vm.alterarForm { it.copy(nome = "  ") }
        assertFalse(vm.criar())
        assertEquals("Informe o nome", vm.form.value.errosCampo["nome"])
        assertTrue(api.projetosCriados.isEmpty())
    }

    @Test
    fun criarAnexaEAtualizaOSeletor() = runTest {
        var avisos = 0
        val vm = ProjetosViewModel(api, sessao, backgroundScope, aoMudarProjetos = { avisos++ })
        vm.recarregar()
        vm.alterarForm { it.copy(nome = " Casa ", descricao = "") }
        assertTrue(vm.criar())
        assertEquals("Casa", api.projetosCriados.single().nome)
        assertEquals(null, api.projetosCriados.single().descricao)
        assertEquals(listOf("Casa"), vm.estado.value.projetos.map { it.nome })
        assertEquals(1, avisos)
    }

    @Test
    fun erro400NoCampo() = runTest {
        api.falhaEm = mapOf("criarProjeto" to ApiErro.Http(400, ProblemDetail(errors = listOf(ErroCampo("nome", "já existe")))))
        val vm = ProjetosViewModel(api, sessao, backgroundScope)
        vm.alterarForm { it.copy(nome = "Casa") }
        assertFalse(vm.criar())
        assertEquals("já existe", vm.form.value.errosCampo["nome"])
    }

    @Test
    fun aplicarArquivadoTiraDosAtivos() = runTest {
        api.projetosPorStatus = mapOf(StatusProjeto.ATIVO to listOf(projeto("a"), projeto("b")))
        val vm = ProjetosViewModel(api, sessao, backgroundScope)
        vm.recarregar()
        vm.aplicar(projeto("a", StatusProjeto.ARQUIVADO))
        assertEquals(listOf("b"), vm.estado.value.projetos.map { it.id })
    }

    @Test
    fun detalheRenomeiaSoONome() = runTest {
        api.projetosPorStatus = mapOf(StatusProjeto.ATIVO to listOf(projeto("a")))
        val mudancas = mutableListOf<String>()
        val vm = ProjetoDetalheViewModel(api, sessao, backgroundScope) { mudancas += it.nome }
        vm.abrir("a")
        assertFalse(vm.estado.value.alterado)
        vm.alterarForm { it.copy(nome = "Reforma") }
        assertTrue(vm.salvar())
        assertEquals(mapOf("nome" to JsonPrimitive("Reforma")), api.patches.single().second.toMap())
        assertEquals(listOf("Reforma"), mudancas)
    }

    @Test
    fun detalheArquivaEReativa() = runTest {
        api.projetosPorStatus = mapOf(StatusProjeto.ATIVO to listOf(projeto("a")))
        val status = mutableListOf<StatusProjeto>()
        val vm = ProjetoDetalheViewModel(api, sessao, backgroundScope) { status += it.status }
        vm.abrir("a")
        vm.arquivar()
        assertEquals(StatusProjeto.ARQUIVADO, vm.estado.value.projeto!!.status)
        vm.reativar()
        assertEquals(listOf(StatusProjeto.ARQUIVADO, StatusProjeto.ATIVO), status)
    }

    @Test
    fun recusaAoArquivarMantemProjeto() = runTest {
        api.projetosPorStatus = mapOf(StatusProjeto.ATIVO to listOf(projeto("a")))
        api.falhaEm = mapOf("arquivar" to erroHttp(409, "Projeto tem tarefas em andamento"))
        val vm = ProjetoDetalheViewModel(api, sessao, backgroundScope)
        vm.abrir("a")
        vm.arquivar()
        assertEquals(StatusProjeto.ATIVO, vm.estado.value.projeto!!.status)
        assertFalse(vm.estado.value.ocupado)
    }

    @Test
    fun detalheListaTarefasDoProjeto() = runTest {
        api.projetosPorStatus = mapOf(StatusProjeto.ATIVO to listOf(projeto("a")))
        api.tarefasDoProjeto = mapOf("a" to listOf(tarefa("t1", projetoId = "a"), tarefa("t2", projetoId = "a")))
        val vm = ProjetoDetalheViewModel(api, sessao, backgroundScope)
        vm.abrir("a")
        assertEquals("a", api.filtros.last().projetoId)
        assertEquals(listOf("t1", "t2"), vm.estado.value.tarefas.map { it.id })

        vm.aplicarTarefa(tarefa("t1", projetoId = null))
        assertEquals(listOf("t2"), vm.estado.value.tarefas.map { it.id }, "saiu do projeto")
    }
}
