package com.controlerotina.mobile.estado

import com.controlerotina.mobile.api.ErroCampo
import com.controlerotina.mobile.api.Prioridade
import com.controlerotina.mobile.api.ProblemDetail
import com.controlerotina.mobile.api.ApiErro
import com.controlerotina.mobile.api.Tarefa
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditarTarefaViewModelTest {
    private val api = FakeRotinaApi()
    private val sessao = sessaoStore(api)
    private val salvas = mutableListOf<Tarefa>()
    private val paisAtualizados = mutableListOf<String>()

    private fun vm(scope: kotlinx.coroutines.CoroutineScope) = EditarTarefaViewModel(
        api, sessao, scope, aoSalvar = { salvas += it }, aoMudarFilhos = { paisAtualizados += it },
    )

    private val base = tarefa("t1").copy(prioridade = Prioridade.MEDIA, prazo = "2026-11-20T02:59:00Z", projetoId = "p1")

    @Test
    fun salvarSoTituloEnviaSoTitulo() = runTest {
        api.tarefaPorId = { base }
        val vm = vm(backgroundScope)
        vm.abrir("t1")
        assertFalse(vm.estado.value.alterado)

        vm.alterar { it.copy(titulo = "Estudar Kotlin") }
        assertTrue(vm.estado.value.alterado)
        assertTrue(vm.salvar())
        val (id, corpo) = api.patches.single()
        assertEquals("t1", id)
        assertEquals(mapOf("titulo" to JsonPrimitive("Estudar Kotlin")), corpo.toMap())
        assertEquals("Estudar Kotlin", salvas.single().titulo)
    }

    @Test
    fun semMudancaFechaSemChamarApi() = runTest {
        api.tarefaPorId = { base }
        val vm = vm(backgroundScope)
        vm.abrir("t1")
        assertTrue(vm.salvar())
        assertTrue(api.patches.isEmpty())
    }

    @Test
    fun tituloVazioNaoEnvia() = runTest {
        api.tarefaPorId = { base }
        val vm = vm(backgroundScope)
        vm.abrir("t1")
        vm.alterar { it.copy(titulo = "  ") }
        assertFalse(vm.salvar())
        assertEquals("Informe o título", vm.estado.value.errosCampo["titulo"])
        assertTrue(api.patches.isEmpty())
    }

    @Test
    fun semProjetoEnviaNullETrocarPrazoEnviaData() = runTest {
        api.tarefaPorId = { base }
        val vm = vm(backgroundScope)
        vm.abrir("t1")
        vm.alterar { it.copy(projetoId = null, prazo = "2026-11-25T02:59:00Z") }
        vm.salvar()
        val corpo = api.patches.single().second
        assertEquals(JsonNull, corpo["projetoId"])
        assertEquals(JsonPrimitive("2026-11-25T02:59:00Z"), corpo["prazo"])
        assertEquals(2, corpo.size)
    }

    @Test
    fun erro400VaiParaOCampo() = runTest {
        api.tarefaPorId = { base }
        api.falhaEm = mapOf(
            "patchTarefa" to ApiErro.Http(400, ProblemDetail(status = 400, errors = listOf(ErroCampo("prazo", "não pode estar no passado")))),
        )
        val vm = vm(backgroundScope)
        vm.abrir("t1")
        vm.alterar { it.copy(prazo = "2020-01-01T00:00:00Z") }
        assertFalse(vm.salvar())
        assertEquals("não pode estar no passado", vm.estado.value.errosCampo["prazo"])
        assertTrue(vm.estado.value.alterado, "mantém o que foi digitado")
    }

    @Test
    fun paiComFilhosNaoEnviaDuracao() = runTest {
        api.tarefaPorId = { tarefa("pai", temFilhos = true) }
        val vm = vm(backgroundScope)
        vm.abrir("pai")
        assertFalse(vm.estado.value.duracaoEditavel)
        vm.alterar { it.copy(duracao = "999") }
        assertFalse(vm.estado.value.alterado)
    }

    @Test
    fun alteradoLigaEDesliga() = runTest {
        api.tarefaPorId = { base }
        val vm = vm(backgroundScope)
        vm.abrir("t1")
        vm.alterar { it.copy(descricao = "nota") }
        assertTrue(vm.estado.value.alterado)
        vm.alterar { it.copy(descricao = "") }
        assertFalse(vm.estado.value.alterado)
    }

    @Test
    fun criarSubtarefaEnviaParentIdEAvisaALista() = runTest {
        var temFilhos = false
        api.tarefaPorId = { tarefa(it, temFilhos = temFilhos) }
        val vm = vm(backgroundScope)
        vm.abrir("pai")
        assertTrue(vm.estado.value.aceitaSubtarefa)

        temFilhos = true
        api.filhosDe = mapOf("pai" to listOf(tarefa("f1", parentId = "pai").copy(titulo = "Comprar tinta")))
        vm.alterarNovaSubtarefa("Comprar tinta")
        assertTrue(vm.criarSubtarefa())
        assertEquals("pai", api.criadas.single().parentId)
        assertEquals(listOf("Comprar tinta"), vm.estado.value.subtarefas.map { it.titulo })
        assertEquals("", vm.estado.value.novaSubtarefa)
        assertTrue(vm.estado.value.original!!.temFilhos)
        assertEquals(listOf("pai"), paisAtualizados)
    }

    @Test
    fun erroAoCriarSubtarefaMantemTitulo() = runTest {
        api.tarefaPorId = { tarefa(it) }
        api.falhaEm = mapOf("criar" to erroHttp(409, "Pai já tem bloco concluído"))
        val vm = vm(backgroundScope)
        vm.abrir("pai")
        vm.alterarNovaSubtarefa("Filha")
        assertFalse(vm.criarSubtarefa())
        assertEquals("Filha", vm.estado.value.novaSubtarefa)
        assertEquals("Pai já tem bloco concluído", vm.estado.value.erroSubtarefa)
    }

    @Test
    fun subtarefaNaoAceitaFilho() = runTest {
        api.tarefaPorId = { tarefa(it, parentId = "pai") }
        val vm = vm(backgroundScope)
        vm.abrir("f1")
        assertFalse(vm.estado.value.aceitaSubtarefa)
        vm.alterarNovaSubtarefa("Neta")
        assertFalse(vm.criarSubtarefa())
        assertTrue(api.criadas.isEmpty())
    }
}
