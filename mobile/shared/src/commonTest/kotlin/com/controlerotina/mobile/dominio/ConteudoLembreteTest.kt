package com.controlerotina.mobile.dominio

import com.controlerotina.mobile.api.BlocoExecucao
import com.controlerotina.mobile.api.BlocoPlano
import com.controlerotina.mobile.api.Execucao
import com.controlerotina.mobile.api.ExecucoesDoDia
import com.controlerotina.mobile.api.ItemExecucao
import com.controlerotina.mobile.api.OrigemBloco
import com.controlerotina.mobile.api.Plano
import com.controlerotina.mobile.api.StatusExecucao
import com.controlerotina.mobile.api.StatusPlano
import com.controlerotina.mobile.api.StatusTarefa
import com.controlerotina.mobile.api.Tarefa
import com.controlerotina.mobile.api.TarefaResumo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConteudoLembreteTest {

    private fun bloco(id: String, ordem: Int, minutos: Int, titulo: String = "Bloco $id") = BlocoPlano(
        id = id,
        duracaoMinutos = minutos,
        ordem = ordem,
        origem = OrigemBloco.TAREFA,
        tarefa = TarefaResumo(id = "t$id", titulo = titulo),
    )

    private fun plano(status: StatusPlano, vararg blocos: BlocoPlano) =
        Plano(id = "p1", data = "2026-11-13", status = status, blocos = blocos.toList())

    private fun exec(blocoId: String, status: StatusExecucao) = ItemExecucao(
        bloco = BlocoExecucao(blocoId, 30, 0),
        execucao = Execucao(id = "e$blocoId", status = status),
    )

    private fun tarefa(id: String, prazo: String?, status: StatusTarefa = StatusTarefa.PENDENTE, titulo: String = "T$id") =
        Tarefa(id = id, titulo = titulo, prazo = prazo, status = status)

    // ---- resumo ----

    @Test
    fun deveConvidarAGerarQuandoNaoHaPlano() {
        val n = montarResumo(null, ExecucoesDoDia())!!
        assertEquals("Seu dia ainda não tem plano", n.titulo)
        assertEquals(DestinoLembrete.PLANO, n.destino)
        assertEquals(TipoLembrete.RESUMO, n.tipo)
    }

    @Test
    fun deveTratarPlanoDescartadoComoSemPlano() {
        assertEquals("Seu dia ainda não tem plano", montarResumo(plano(StatusPlano.DESCARTADO), ExecucoesDoDia())!!.titulo)
    }

    @Test
    fun deveConvidarAAprovarQuandoRascunho() {
        val n = montarResumo(plano(StatusPlano.RASCUNHO, bloco("1", 0, 30)), ExecucoesDoDia())!!
        assertEquals("Plano pronto para aprovar", n.titulo)
    }

    @Test
    fun deveResumirBlocosPendentesEProximoPelaOrdem() {
        val p = plano(
            StatusPlano.APROVADO,
            bloco("d", 3, 45, "Ler"),
            bloco("a", 0, 30, "Feito"),
            bloco("c", 2, 60, "Escrever"),
            bloco("b", 1, 30, "Revisar"),
        )
        val n = montarResumo(p, ExecucoesDoDia(listOf(exec("a", StatusExecucao.CONCLUIDO))))!!
        assertEquals("Plano de hoje", n.titulo)
        assertEquals("3 blocos (2h15) pendentes. Próximo: Revisar", n.texto)
    }

    @Test
    fun blocoEmAndamentoContaComoPendenteMasNaoComoProximo() {
        val p = plano(StatusPlano.APROVADO, bloco("a", 0, 30, "Agora"), bloco("b", 1, 15, "Depois"))
        val n = montarResumo(p, ExecucoesDoDia(listOf(exec("a", StatusExecucao.EM_ANDAMENTO))))!!
        assertEquals("2 blocos (45min) pendentes. Próximo: Depois", n.texto)
    }

    @Test
    fun deveOmitirProximoQuandoSoRestaBlocoEmAndamento() {
        val p = plano(StatusPlano.APROVADO, bloco("a", 0, 60))
        val n = montarResumo(p, ExecucoesDoDia(listOf(exec("a", StatusExecucao.EM_ANDAMENTO))))!!
        assertEquals("1 bloco (1h) pendentes.", n.texto)
    }

    @Test
    fun naoDeveNotificarQuandoPlanoAprovadoTodoExecutado() {
        val p = plano(StatusPlano.APROVADO, bloco("a", 0, 30), bloco("b", 1, 30))
        val execs = ExecucoesDoDia(listOf(exec("a", StatusExecucao.CONCLUIDO), exec("b", StatusExecucao.ABORTADO)))
        assertNull(montarResumo(p, execs))
    }

    @Test
    fun naoDeveNotificarQuandoPlanoAprovadoSemBlocos() {
        assertNull(montarResumo(plano(StatusPlano.APROVADO), ExecucoesDoDia()))
    }

    // ---- aviso de prazo ----

    @Test
    fun deveMostrarTituloDaUnicaTarefa() {
        val n = montarAvisoPrazo(listOf(tarefa("1", "2026-11-15T02:59:00Z", titulo = "Relatório")), total = 1)!!
        assertEquals("1 tarefa vence amanhã", n.titulo)
        assertEquals("Relatório", n.texto)
        assertEquals(DestinoLembrete.TAREFAS, n.destino)
        assertEquals(TipoLembrete.PRAZO, n.tipo)
    }

    @Test
    fun deveListarTresTitulosDePrazoMaisCedoEContarOTotal() {
        val tarefas = listOf(
            tarefa("1", "2026-11-14T20:00:00Z", titulo = "C"),
            tarefa("2", "2026-11-14T10:00:00Z", titulo = "A"),
            tarefa("3", "2026-11-15T02:59:00Z", titulo = "E"),
            tarefa("4", "2026-11-14T12:00:00Z", titulo = "B"),
            tarefa("5", "2026-11-14T22:00:00Z", titulo = "D"),
        )
        val n = montarAvisoPrazo(tarefas, total = 5)!!
        assertEquals("5 tarefas vencem amanhã", n.titulo)
        assertEquals("A, B, C e mais 2", n.texto)
    }

    @Test
    fun deveUsarTotalElementsQuandoHaMaisQueAPagina() {
        val n = montarAvisoPrazo(listOf(tarefa("1", "2026-11-14T10:00:00Z", titulo = "A")), total = 60)!!
        assertEquals("60 tarefas vencem amanhã", n.titulo)
        assertEquals("A e mais 59", n.texto)
    }

    @Test
    fun naoDeveNotificarListaVazia() {
        assertNull(montarAvisoPrazo(emptyList(), total = 0))
    }

    @Test
    fun deveIgnorarFinalizadaCanceladaEDeletada() {
        val tarefas = listOf(
            tarefa("1", "2026-11-14T10:00:00Z", StatusTarefa.FINALIZADA),
            tarefa("2", "2026-11-14T10:00:00Z", StatusTarefa.CANCELADA),
            Tarefa(id = "3", titulo = "X", prazo = "2026-11-14T10:00:00Z", status = StatusTarefa.PENDENTE, deletada = true),
            tarefa("4", null),
        )
        assertNull(montarAvisoPrazo(tarefas, total = 4))
    }

    @Test
    fun naoDeveConfiarNoTotalQuandoClienteDescartouTarefas() {
        val tarefas = listOf(
            tarefa("1", "2026-11-14T10:00:00Z", titulo = "Válida"),
            tarefa("2", "2026-11-14T10:00:00Z", StatusTarefa.FINALIZADA),
        )
        assertEquals("1 tarefa vence amanhã", montarAvisoPrazo(tarefas, total = 2)!!.titulo)
    }

    @Test
    fun deveUsarTextoPadraoParaTituloVazioEPrazoMalformadoVaiProFim() {
        val tarefas = listOf(
            tarefa("1", "nao-e-data", titulo = "Malformada"),
            tarefa("2", "2026-11-14T10:00:00Z", titulo = "  "),
        )
        assertEquals("Tarefa sem título, Malformada", montarAvisoPrazo(tarefas, total = null)!!.texto)
    }
}
