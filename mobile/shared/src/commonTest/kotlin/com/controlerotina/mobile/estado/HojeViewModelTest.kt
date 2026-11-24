package com.controlerotina.mobile.estado

import com.controlerotina.mobile.api.BlocoExecucao
import com.controlerotina.mobile.api.CriarCapacidadeSemanalItem
import com.controlerotina.mobile.api.DiaSemana
import com.controlerotina.mobile.api.ItemExecucao
import com.controlerotina.mobile.api.ItemNaoAlocado
import com.controlerotina.mobile.api.StatusExecucao
import com.controlerotina.mobile.api.StatusPlano
import com.controlerotina.mobile.api.TarefaResumo
import com.controlerotina.mobile.dominio.AcaoBloco
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

    // ---- Guarda de capacidade e plano vazio (ui-primeiro-plano-capacidade) ----

    private suspend fun semCapacidade(
        vm: HojeViewModel,
        semana: List<com.controlerotina.mobile.api.CapacidadeSemanal> = emptyList(),
    ): EstadoHoje.SemPlano {
        api.horasResolvidas = 0
        api.semana = semana
        vm.carregar()
        vm.gerar()
        return assertIs<EstadoHoje.SemPlano>(vm.estado.value)
    }

    @Test
    fun gerarSemCapacidadePedeCapacidadeESemChamarGerarPlano() = runTest {
        val vm = vm(backgroundScope)
        val estado = semCapacidade(vm)
        assertTrue(estado.pedindoCapacidade)
        assertFalse(estado.gerando)
        assertEquals(0, estado.horasPorDia.getValue(DiaSemana.SEGUNDA))
        assertTrue(api.chamadas.none { it.startsWith("gerar:") })
    }

    @Test
    fun usarDiasUteisGravaSegASex4hEmLoteEGeraUmaVez() = runTest {
        val vm = vm(backgroundScope)
        semCapacidade(vm, listOf(capacidade(DiaSemana.SEGUNDA, 0)))
        vm.usarDiasUteis()
        assertEquals(
            listOf(listOf(DiaSemana.TERCA, DiaSemana.QUARTA, DiaSemana.QUINTA, DiaSemana.SEXTA).map { CriarCapacidadeSemanalItem(it, 4) }),
            api.criadasSemana,
        )
        assertEquals(listOf("c-SEGUNDA" to 4), api.atualizadasSemana)
        assertEquals(1, api.chamadas.count { it.startsWith("gerar:") })
        assertIs<EstadoHoje.Conteudo>(vm.estado.value)
    }

    @Test
    fun usarDiasUteisChamadoDuasVezesAoMesmoTempoNaoDuplica() = runTest {
        val vm = vm(backgroundScope)
        semCapacidade(vm)
        api.atrasoGravacaoMs = 100
        val primeiro = launch { vm.usarDiasUteis() }
        runCurrent()
        val segundo = launch { vm.usarDiasUteis() }
        advanceUntilIdle()
        primeiro.join(); segundo.join()
        assertEquals(1, api.criadasSemana.size)
        assertEquals(1, api.chamadas.count { it.startsWith("gerar:") })
    }

    @Test
    fun templateConfiguradoNoDomingoGeraDireto() = runTest {
        api.horasResolvidas = 0
        api.semana = listOf(capacidade(DiaSemana.SEGUNDA, 4))
        val vm = vm(backgroundScope)
        vm.carregar()
        vm.gerar()
        assertIs<EstadoHoje.Conteudo>(vm.estado.value)
        assertEquals(1, api.chamadas.count { it.startsWith("gerar:") })
    }

    @Test
    fun diaComHorasNaoLeTemplate() = runTest {
        val vm = vm(backgroundScope)
        vm.carregar()
        vm.gerar()
        assertIs<EstadoHoje.Conteudo>(vm.estado.value)
        assertTrue("semana" !in api.chamadas)
    }

    @Test
    fun falhaAoLerCapacidadeGeraMesmoAssim() = runTest {
        api.falhaEm = mapOf("resolvida" to erroHttp(500, "Falhou"))
        val vm = vm(backgroundScope)
        vm.carregar()
        vm.gerar()
        assertIs<EstadoHoje.Conteudo>(vm.estado.value)
    }

    @Test
    fun salvarSemanaGravaSoOsDiasComHorasEGera() = runTest {
        val vm = vm(backgroundScope)
        semCapacidade(vm)
        vm.salvarSemana(DiaSemana.entries.associateWith { if (it == DiaSemana.SABADO) 3 else 0 })
        assertEquals(listOf(listOf(CriarCapacidadeSemanalItem(DiaSemana.SABADO, 3))), api.criadasSemana)
        assertIs<EstadoHoje.Conteudo>(vm.estado.value)
    }

    @Test
    fun fecharSheetNaoGravaNemGera() = runTest {
        val vm = vm(backgroundScope)
        semCapacidade(vm)
        vm.fecharCapacidade()
        assertEquals(EstadoHoje.SemPlano(), vm.estado.value)
        assertTrue(api.criadasSemana.isEmpty())
        assertTrue(api.chamadas.none { it.startsWith("gerar:") })
    }

    @Test
    fun falhaAoGravarMostraErroNoSheetSemGerar() = runTest {
        val vm = vm(backgroundScope)
        semCapacidade(vm)
        api.falhaEm = mapOf("criarSemana" to erroHttp(500, "Erro interno"))
        vm.usarDiasUteis()
        val estado = assertIs<EstadoHoje.SemPlano>(vm.estado.value)
        assertEquals("Erro interno", estado.erroCapacidade)
        assertTrue(estado.pedindoCapacidade)
        assertFalse(estado.salvandoCapacidade)
        assertTrue(api.chamadas.none { it.startsWith("gerar:") })
    }

    @Test
    fun conflito409AoGravarReleSemanaEAvisa() = runTest {
        val vm = vm(backgroundScope)
        semCapacidade(vm)
        api.falhaEm = mapOf("criarSemana" to erroHttp(409, "Já existe", "Capacidade semanal já existe"))
        val leiturasAntes = api.chamadas.count { it == "semana" }
        vm.usarDiasUteis()
        val estado = assertIs<EstadoHoje.SemPlano>(vm.estado.value)
        assertEquals(MSG_CAPACIDADE_MUDOU, estado.erroCapacidade)
        assertEquals(leiturasAntes + 1, api.chamadas.count { it == "semana" })
        assertTrue(api.chamadas.none { it.startsWith("gerar:") })
    }

    @Test
    fun semConexaoAoGravarMostraMensagemDeRede() = runTest {
        val vm = vm(backgroundScope)
        semCapacidade(vm)
        api.falhaEm = mapOf("criarSemana" to com.controlerotina.mobile.api.ApiErro.Rede(RuntimeException("x")))
        vm.usarDiasUteis()
        val estado = assertIs<EstadoHoje.SemPlano>(vm.estado.value)
        assertEquals(mensagemDeErro(com.controlerotina.mobile.api.ApiErro.Rede(RuntimeException("x"))), estado.erroCapacidade)
    }

    @Test
    fun aprovarPlanoSemBlocosNaoChamaApi() = runTest {
        api.planoAtual = plano(StatusPlano.RASCUNHO)
        val vm = vm(backgroundScope)
        vm.carregar()
        vm.aprovar()
        assertTrue(api.chamadas.none { it.startsWith("aprovar:") })
        assertEquals(StatusPlano.RASCUNHO, assertIs<EstadoHoje.Conteudo>(vm.estado.value).plano.status)
    }

    @Test
    fun conflitoPlanoVazioDoBackViraMensagemEPlanoSegueRascunho() = runTest {
        api.planoAtual = plano(StatusPlano.RASCUNHO, bloco("b1", 1))
        api.falhaEm = mapOf("aprovar" to erroHttp(409, "Não há blocos para aprovar.", "Plano vazio"))
        val vm = vm(backgroundScope)
        vm.carregar()
        val aviso = async(start = CoroutineStart.UNDISPATCHED) { sessao.avisos.first() }
        vm.aprovar()
        assertEquals("Não há blocos para aprovar.", aviso.await())
        assertEquals(StatusPlano.RASCUNHO, assertIs<EstadoHoje.Conteudo>(vm.estado.value).plano.status)
        assertNull(vm.ocupado.value)
    }

    @Test
    fun alteracoesCapacidadeNaoMexeEmDiaQueJaEstaIgual() {
        val alteracoes = alteracoesCapacidade(
            listOf(capacidade(DiaSemana.SEGUNDA, 4), capacidade(DiaSemana.TERCA, 2), capacidade(DiaSemana.QUARTA, 9, ativo = false)),
            SEMANA_DIAS_UTEIS,
        )
        assertEquals(listOf("c-TERCA" to 4), alteracoes.atualizar)
        assertEquals(listOf(DiaSemana.QUARTA, DiaSemana.QUINTA, DiaSemana.SEXTA), alteracoes.criar.map { it.diaSemana })
    }
}
