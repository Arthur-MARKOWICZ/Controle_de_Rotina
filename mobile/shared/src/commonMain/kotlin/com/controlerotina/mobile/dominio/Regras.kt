package com.controlerotina.mobile.dominio

import com.controlerotina.mobile.api.HabitoDoDia
import com.controlerotina.mobile.api.StatusExecucao
import com.controlerotina.mobile.api.StatusPlano
import com.controlerotina.mobile.api.StatusTarefa
import com.controlerotina.mobile.api.Tarefa

// Regras puras de "o que o usuário pode fazer agora" (design D6). Espelham ui-tarefa-sessao
// da web; a API continua sendo a autoridade e recusa o que escapar.

private val STATUS_COM_PROGRESSO = setOf(StatusTarefa.PENDENTE, StatusTarefa.EM_PROGRESSO, StatusTarefa.ATRASADA)

/** Iniciar/Concluir de sessão avulsa num card da lista de tarefas. */
fun podeSessaoAvulsa(tarefa: Tarefa): Boolean =
    !tarefa.deletada &&
        !tarefa.bloqueada &&
        !tarefa.temFilhos &&
        tarefa.restanteMinutos > 0 &&
        tarefa.status in STATUS_COM_PROGRESSO

enum class AcaoBloco { INICIAR, CONCLUIR, ABORTAR }

/** Ações de um bloco na tela Hoje: só em plano aprovado, conforme o estado da execução. */
fun acoesDoBloco(statusPlano: StatusPlano, statusExecucao: StatusExecucao?): Set<AcaoBloco> {
    if (statusPlano != StatusPlano.APROVADO) return emptySet()
    return when (statusExecucao ?: StatusExecucao.NAO_INICIADO) {
        StatusExecucao.NAO_INICIADO, StatusExecucao.ABORTADO -> setOf(AcaoBloco.INICIAR)
        StatusExecucao.EM_ANDAMENTO -> setOf(AcaoBloco.CONCLUIR, AcaoBloco.ABORTAR)
        StatusExecucao.CONCLUIDO -> emptySet()
    }
}

/** Iniciar/Concluir direto de hábito: não cumprido e sem sessão em andamento. */
fun podeAgirNoHabito(habito: HabitoDoDia): Boolean = !habito.cumprido && habito.emAndamento == null

// ---- Picker de duração ----

val PRESETS_DURACAO = listOf(15, 25, 30, 45, 60)

fun presetHabilitado(preset: Int, restanteMinutos: Int): Boolean = preset in 1..restanteMinutos

/** Duração aceita pela sessão avulsa: entre 1 e o restante; fora disso, `null`. */
fun duracaoValida(minutos: Int?, restanteMinutos: Int): Int? =
    minutos?.takeIf { restanteMinutos >= 1 && it in 1..restanteMinutos }
