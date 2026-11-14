package com.controlerotina.mobile.api

import kotlinx.serialization.Serializable

// Espelho dos records do backend. Instantes e datas chegam como String ISO-8601 e
// são convertidos só onde a tela precisa; campos desconhecidos são ignorados.

@Serializable
data class LoginRequest(val email: String, val senha: String)

@Serializable
data class AccessTokenResponse(val accessToken: String)

@Serializable
data class MeResponse(val email: String)

@Serializable
data class ErroCampo(val property: String? = null, val message: String? = null)

@Serializable
data class ProblemDetail(
    val status: Int? = null,
    val title: String? = null,
    val detail: String? = null,
    val errors: List<ErroCampo> = emptyList(),
)

// ---- Plano ----

@Serializable
enum class StatusPlano { RASCUNHO, APROVADO, DESCARTADO, HABITO_AVULSO, TAREFA_AVULSA }

@Serializable
enum class OrigemBloco { TAREFA, HABITO }

@Serializable
enum class StatusTarefa { PENDENTE, EM_PROGRESSO, FINALIZADA, ATRASADA, CANCELADA }

@Serializable
enum class Prioridade { ALTA, MEDIA, BAIXA }

@Serializable
enum class StatusExecucao { NAO_INICIADO, EM_ANDAMENTO, CONCLUIDO, ABORTADO }

@Serializable
data class TarefaResumo(
    val id: String,
    val titulo: String,
    val prioridade: Prioridade? = null,
    val status: StatusTarefa? = null,
    val duracaoMinutos: Int = 0,
    val prazo: String? = null,
    val bloqueada: Boolean = false,
)

@Serializable
data class HabitoResumo(
    val id: String,
    val titulo: String,
    val horarioPreferencial: String? = null,
    val streakAtual: Int = 0,
)

@Serializable
data class BlocoPlano(
    val id: String,
    val duracaoMinutos: Int,
    val ordem: Int,
    val origem: OrigemBloco,
    val tarefaId: String? = null,
    val habitoId: String? = null,
    val tarefa: TarefaResumo? = null,
    val habito: HabitoResumo? = null,
) {
    val titulo: String get() = tarefa?.titulo ?: habito?.titulo ?: "Bloco"
}

@Serializable
data class ItemNaoAlocado(
    val tipo: String? = null,
    val id: String,
    val titulo: String,
    val duracaoMinutos: Int = 0,
    val motivo: String? = null,
)

@Serializable
data class Plano(
    val id: String,
    val data: String,
    val versao: Int = 1,
    val status: StatusPlano,
    val temConflito: Boolean = false,
    val mensagem: String? = null,
    val blocos: List<BlocoPlano> = emptyList(),
    val itensNaoAlocados: List<ItemNaoAlocado> = emptyList(),
)

// ---- Execução / XP ----

@Serializable
data class XpProgresso(
    val xpTotal: Int = 0,
    val nivel: Int = 1,
    val xpNoNivel: Int = 0,
    val xpParaProximoNivel: Int = 0,
)

@Serializable
data class XpConclusao(
    val ganhoNesteRequest: Int = 0,
    val progresso: XpProgresso? = null,
)

@Serializable
data class Execucao(
    val id: String? = null,
    val blocoPlanoId: String? = null,
    val status: StatusExecucao,
    val iniciadoEm: String? = null,
    val concluidoEm: String? = null,
    val abortadoEm: String? = null,
    val xp: XpConclusao? = null,
)

@Serializable
data class BlocoExecucao(val id: String, val duracaoMinutos: Int, val ordem: Int)

@Serializable
data class ItemExecucao(
    val bloco: BlocoExecucao,
    val tarefaResumo: TarefaResumo? = null,
    val habitoResumo: HabitoResumo? = null,
    val execucao: Execucao,
) {
    val titulo: String get() = tarefaResumo?.titulo ?: habitoResumo?.titulo ?: "Bloco"
}

@Serializable
data class ExecucoesDoDia(val itens: List<ItemExecucao> = emptyList())

@Serializable
data class IniciarExecucaoRequest(val blocoId: String)

// ---- Tarefas ----

@Serializable
data class Tarefa(
    val id: String,
    val titulo: String,
    val prioridade: Prioridade? = null,
    val prazo: String? = null,
    val status: StatusTarefa,
    val duracaoMinutos: Int = 0,
    val restanteMinutos: Int = 0,
    val deletada: Boolean = false,
    val temFilhos: Boolean = false,
    val bloqueada: Boolean = false,
    val projetoNome: String? = null,
    val descricao: String? = null,
    val projetoId: String? = null,
    val parentId: String? = null,
    val parentTitulo: String? = null,
)

/**
 * Filtros de `GET /tarefas` que o app usa. [status] padrão são os abertos.
 * [prazoDe]/[prazoAte] são instantes ISO-8601 UTC; o backend trata os dois limites como inclusivos.
 */
data class FiltroTarefas(
    val somenteRaizes: Boolean = false,
    val parentId: String? = null,
    val projetoId: String? = null,
    val status: List<StatusTarefa> = STATUS_ABERTOS,
    val prazoDe: String? = null,
    val prazoAte: String? = null,
)

@Serializable
data class PaginaTarefas(
    val content: List<Tarefa> = emptyList(),
    val number: Int = 0,
    val totalPages: Int = 0,
    val last: Boolean? = null,
    val totalElements: Long? = null,
) {
    val ultima: Boolean get() = last ?: (number + 1 >= totalPages)
}

@Serializable
data class CriarTarefaRequest(
    val titulo: String,
    val duracaoMinutos: Int? = null,
    val prazo: String? = null,
    val projetoId: String? = null,
    val parentId: String? = null,
)

@Serializable
data class SessaoTarefaRequest(val duracaoMinutos: Int? = null, val data: String? = null)

// ---- Hábitos ----

@Serializable
data class SlotExecucaoHabito(
    val blocoId: String? = null,
    val execucaoId: String? = null,
    val status: StatusExecucao? = null,
    val iniciadoEm: String? = null,
)

@Serializable
data class HabitoDoDia(
    val id: String,
    val titulo: String,
    val duracaoMinutos: Int = 0,
    val horarioPreferencial: String? = null,
    val streakAtual: Int = 0,
    val cumprido: Boolean = false,
    val noPlanoAprovado: Boolean = false,
    val execucaoAvulsa: SlotExecucaoHabito? = null,
    val execucaoNoPlano: SlotExecucaoHabito? = null,
) {
    val emAndamento: SlotExecucaoHabito?
        get() = listOfNotNull(execucaoAvulsa, execucaoNoPlano).firstOrNull { it.status == StatusExecucao.EM_ANDAMENTO }
}

// ---- Páginas genéricas (Spring `Page`) ----

@Serializable
data class Pagina<T>(
    val content: List<T> = emptyList(),
    val number: Int = 0,
    val totalPages: Int = 0,
    val last: Boolean? = null,
)

// ---- Projetos ----

@Serializable
enum class StatusProjeto { ATIVO, ARQUIVADO }

@Serializable
data class Projeto(
    val id: String,
    val nome: String,
    val descricao: String? = null,
    val status: StatusProjeto = StatusProjeto.ATIVO,
    val ordem: Int = 0,
    val bloqueada: Boolean = false,
)

@Serializable
data class CriarProjetoRequest(val nome: String, val descricao: String? = null)

// ---- Decidir ----

@Serializable
data class ItemDecidir(
    val id: String,
    val titulo: String,
    val descricao: String? = null,
    val decidirAte: String? = null,
    val projetoId: String? = null,
    val tarefaId: String? = null,
) {
    val promovido: Boolean get() = tarefaId != null
}

@Serializable
data class CriarDecidirRequest(
    val titulo: String,
    val descricao: String? = null,
    val decidirAte: String? = null,
)

@Serializable
data class PromoverDecidirRequest(
    val prioridade: Prioridade? = null,
    val prazo: String? = null,
    val duracaoMinutos: Int? = null,
)
