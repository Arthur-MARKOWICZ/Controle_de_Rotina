package com.controlerotina.mobile.dominio

import com.controlerotina.mobile.api.ExecucoesDoDia
import com.controlerotina.mobile.api.Plano
import com.controlerotina.mobile.api.StatusExecucao
import com.controlerotina.mobile.api.StatusPlano
import com.controlerotina.mobile.api.StatusTarefa
import com.controlerotina.mobile.api.Tarefa
import kotlin.time.Instant

// O que cada lembrete diz (spec mobile-lembretes). `null` = nada a avisar, não notifica.

enum class DestinoLembrete { PLANO, TAREFAS }

data class Notificacao(
    val tipo: TipoLembrete,
    val titulo: String,
    val texto: String,
    val destino: DestinoLembrete,
)

/** Status de tarefa que entram no aviso de prazo amanhã. */
val STATUS_AVISO_PRAZO = listOf(StatusTarefa.PENDENTE, StatusTarefa.EM_PROGRESSO)

/** Quantos títulos o aviso de prazo mostra. */
const val MAX_TITULOS_AVISO = 3

private val STATUS_PENDENTES = setOf(StatusExecucao.NAO_INICIADO, StatusExecucao.EM_ANDAMENTO)

fun montarResumo(plano: Plano?, execucoes: ExecucoesDoDia): Notificacao? = when (plano?.status) {
    StatusPlano.RASCUNHO -> Notificacao(
        TipoLembrete.RESUMO,
        "Plano pronto para aprovar",
        "Revise e aprove o plano de hoje.",
        DestinoLembrete.PLANO,
    )
    StatusPlano.APROVADO -> resumoAprovado(plano, execucoes)
    // Sem plano ou descartado: o dia ainda não tem plano.
    else -> Notificacao(
        TipoLembrete.RESUMO,
        "Seu dia ainda não tem plano",
        "Toque para gerar o plano de hoje.",
        DestinoLembrete.PLANO,
    )
}

private fun resumoAprovado(plano: Plano, execucoes: ExecucoesDoDia): Notificacao? {
    val statusPorBloco = execucoes.itens.associate { it.bloco.id to it.execucao.status }
    val pendentes = plano.blocos
        .sortedBy { it.ordem }
        .filter { (statusPorBloco[it.id] ?: StatusExecucao.NAO_INICIADO) in STATUS_PENDENTES }
    if (pendentes.isEmpty()) return null
    val minutos = pendentes.sumOf { it.duracaoMinutos }
    val proximo = pendentes.firstOrNull { (statusPorBloco[it.id] ?: StatusExecucao.NAO_INICIADO) == StatusExecucao.NAO_INICIADO }
    val blocos = if (pendentes.size == 1) "1 bloco" else "${pendentes.size} blocos"
    val texto = buildString {
        append("$blocos (${formatarMinutos(minutos)}) pendentes.")
        if (proximo != null) append(" Próximo: ${proximo.titulo.ifBlank { "Bloco" }}")
    }
    return Notificacao(TipoLembrete.RESUMO, "Plano de hoje", texto, DestinoLembrete.PLANO)
}

/**
 * Aviso de prazo amanhã a partir da página já filtrada pela API. [total] é o `totalElements`
 * da página (pode haver mais tarefas do que as baixadas).
 */
fun montarAvisoPrazo(tarefas: List<Tarefa>, total: Long?): Notificacao? {
    val validas = tarefas
        .filter { !it.deletada && it.status in STATUS_AVISO_PRAZO && it.prazo != null }
        .sortedBy { runCatching { Instant.parse(it.prazo!!) }.getOrNull() ?: Instant.DISTANT_FUTURE }
    if (validas.isEmpty()) return null
    // Se o cliente descartou alguma, a página não é confiável para o total: usa o que sobrou.
    val quantidade = if (total != null && validas.size == tarefas.size && total > validas.size) total else validas.size.toLong()
    val titulo = if (quantidade == 1L) "1 tarefa vence amanhã" else "$quantidade tarefas vencem amanhã"
    val titulos = validas.take(MAX_TITULOS_AVISO).map { it.titulo.ifBlank { "Tarefa sem título" } }
    val resto = quantidade - titulos.size
    val texto = titulos.joinToString(", ") + if (resto > 0) " e mais $resto" else ""
    return Notificacao(TipoLembrete.PRAZO, titulo, texto, DestinoLembrete.TAREFAS)
}
