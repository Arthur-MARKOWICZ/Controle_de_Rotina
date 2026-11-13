package com.controlerotina.mobile.estado

import com.controlerotina.mobile.api.BlocoExecucao
import com.controlerotina.mobile.api.ItemExecucao
import com.controlerotina.mobile.api.ItemNaoAlocado
import com.controlerotina.mobile.api.StatusExecucao
import com.controlerotina.mobile.api.StatusPlano
import com.controlerotina.mobile.api.TarefaResumo
import com.controlerotina.mobile.dominio.AcaoBloco
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HojeViewModelTest {
    private val api = FakeRotinaApi()
    private val sessao = sessaoStore(api)

    private fun vm(scope: kotlinx.coroutines.CoroutineScope) = HojeViewModel(api, sessao, { HOJE }, scope)

    @Test
    fun planoAprovadoMostraBlocosEmOrdemComAcoes() = runTest {
        api.planoAtual = plano(StatusPlano.APROVADO, bloco("b2", 2), bloco("b1", 1))
        val vm = vm(backgroundScope)
        vm.carregar()
        val conteudo = assertIs<EstadoHoje.Conteudo>(vm.estado.value)
        assertEquals(listOf("b1", "b2"), conteudo.blocos.map { it.bloco.id })
        assertTrue(conteudo.blocos.all { it.acoes == setOf(AcaoBloco.INICIAR) })
        assertTrue("plano:$HOJE" in api.chamadas)
    }

    @Test
    fun diaSemPlanoMostraVazioEGeraRascunho() = runTest {
        val vm = vm(backgroundScope)
        vm.carregar()
        assertIs<EstadoHoje.SemPlano>(vm.estado.value)
        vm.gerar()
        val conteudo = assertIs<EstadoHoje.Conteudo>(vm.estado.value)
        assertEquals(StatusPlano.RASCUNHO, conteudo.plano.status)
        assertTrue(conteudo.blocos.all { it.acoes.isEmpty() }, "rascunho não oferece execução")
    }

    @Test
    fun aprovarRascunhoLiberaExecucao() = runTest {
        api.planoAtual = plano(StatusPlano.RASCUNHO, bloco("b1", 1))
        val vm = vm(backgroundScope)
        vm.carregar()
        vm.aprovar()
        val conteudo = assertIs<EstadoHoje.Conteudo>(vm.estado.value)
        assertEquals(StatusPlano.APROVADO, conteudo.plano.status)
        assertEquals(setOf(AcaoBloco.INICIAR), conteudo.blocos.single().acoes)
    }

    @Test
    fun rascunhoComConflitoExpoeMensagem() = runTest {
        api.planoAtual = plano(StatusPlano.RASCUNHO, bloco("b1", 1), conflito = true)
        val vm = vm(backgroundScope)
        vm.carregar()
        val conteudo = assertIs<EstadoHoje.Conteudo>(vm.estado.value)
        assertTrue(conteudo.plano.temConflito)
        assertEquals("Nem tudo coube", conteudo.plano.mensagem)
    }

    @Test
    fun iniciarBlocoAbreSessaoAtivaEMudaAcoes() = runTest {
        api.planoAtual = plano(StatusPlano.APROVADO, bloco("b1", 1, "Estudar"))
        val vm = vm(backgroundScope)
        vm.carregar()
        val item = (vm.estado.value as EstadoHoje.Conteudo).blocos.single()
        vm.iniciar(item)
        val depois = (vm.estado.value as EstadoHoje.Conteudo).blocos.single()
        assertEquals(StatusExecucao.EM_ANDAMENTO, depois.status)
        assertEquals(setOf(AcaoBloco.CONCLUIR, AcaoBloco.ABORTAR), depois.acoes)
        assertEquals("Estudar", sessao.sessao.value?.titulo)
    }

    @Test
    fun abortarVoltaAOferecerIniciar() = runTest {
        api.planoAtual = plano(StatusPlano.APROVADO, bloco("b1", 1))
        api.itensExecucao = listOf(
            ItemExecucao(BlocoExecucao("b1", 30, 1), TarefaResumo("t", "T"), null, execucao("e1", StatusExecucao.EM_ANDAMENTO)),
        )
        val vm = vm(backgroundScope)
        vm.carregar()
        val item = (vm.estado.value as EstadoHoje.Conteudo).blocos.single()
        api.itensExecucao = listOf(
            ItemExecucao(BlocoExecucao("b1", 30, 1), TarefaResumo("t", "T"), null, execucao("e1", StatusExecucao.ABORTADO)),
        )
        vm.abortar(item)
        assertEquals(setOf(AcaoBloco.INICIAR), (vm.estado.value as EstadoHoje.Conteudo).blocos.single().acoes)
        assertTrue("abortar:e1" in api.chamadas)
    }

    @Test
    fun blocoConcluidoNaoTemAcoes() = runTest {
        api.planoAtual = plano(StatusPlano.APROVADO, bloco("b1", 1))
        api.itensExecucao = listOf(
            ItemExecucao(BlocoExecucao("b1", 30, 1), TarefaResumo("t", "T"), null, execucao("e1", StatusExecucao.CONCLUIDO)),
        )
        val vm = vm(backgroundScope)
        vm.carregar()
        assertTrue((vm.estado.value as EstadoHoje.Conteudo).blocos.single().acoes.isEmpty())
    }

    @Test
    fun puxarParaAtualizarBuscaDeNovo() = runTest {
        api.planoAtual = plano(StatusPlano.APROVADO, bloco("b1", 1))
        val vm = vm(backgroundScope)
        vm.carregar()
        vm.carregar(puxando = true)
        assertEquals(2, api.chamadas.count { it.startsWith("plano:") })
        assertEquals(2, api.chamadas.count { it.startsWith("execucoes:") })
    }

    @Test
    fun erroNoPrimeiroCarregamentoMostraErro() = runTest {
        api.falhaEm = mapOf("plano" to erroHttp(500, "Falhou"))
        val vm = vm(backgroundScope)
        vm.carregar()
        assertEquals(EstadoHoje.Erro("Falhou"), vm.estado.value)
    }

    @Test
    fun itensNaoAlocadosChegamATela() = runTest {
        api.planoAtual = plano(StatusPlano.APROVADO, bloco("b1", 1)).copy(
            itensNaoAlocados = listOf(ItemNaoAlocado(id = "t9", titulo = "Grande", duracaoMinutos = 300)),
        )
        val vm = vm(backgroundScope)
        vm.carregar()
        val conteudo = assertIs<EstadoHoje.Conteudo>(vm.estado.value)
        assertEquals(listOf("Grande"), conteudo.plano.itensNaoAlocados.map { it.titulo })
    }

    @Test
    fun limparApagaPlanoDaMemoria() = runTest {
        api.planoAtual = plano(StatusPlano.APROVADO, bloco("b1", 1))
        val vm = vm(backgroundScope)
        vm.carregar()
        vm.limpar()
        assertEquals(EstadoHoje.Carregando, vm.estado.value)
    }
}
