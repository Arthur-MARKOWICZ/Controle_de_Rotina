package com.controlerotina.mobile.estado

import com.controlerotina.mobile.api.ApiErro
import com.controlerotina.mobile.api.Autenticador
import com.controlerotina.mobile.api.BlocoExecucao
import com.controlerotina.mobile.api.BlocoPlano
import com.controlerotina.mobile.api.CriarDecidirRequest
import com.controlerotina.mobile.api.CriarProjetoRequest
import com.controlerotina.mobile.api.CriarTarefaRequest
import com.controlerotina.mobile.api.FiltroTarefas
import com.controlerotina.mobile.api.ItemDecidir
import com.controlerotina.mobile.api.Projeto
import com.controlerotina.mobile.api.PromoverDecidirRequest
import com.controlerotina.mobile.api.StatusProjeto
import com.controlerotina.mobile.api.Execucao
import com.controlerotina.mobile.api.ExecucoesDoDia
import com.controlerotina.mobile.api.HabitoDoDia
import com.controlerotina.mobile.api.ItemExecucao
import com.controlerotina.mobile.api.MeResponse
import com.controlerotina.mobile.api.OrigemBloco
import com.controlerotina.mobile.api.PaginaTarefas
import com.controlerotina.mobile.api.Plano
import com.controlerotina.mobile.api.ProblemDetail
import com.controlerotina.mobile.api.RotinaApi
import com.controlerotina.mobile.api.StatusExecucao
import com.controlerotina.mobile.api.StatusPlano
import com.controlerotina.mobile.api.StatusTarefa
import com.controlerotina.mobile.api.Tarefa
import com.controlerotina.mobile.api.TarefaResumo
import com.controlerotina.mobile.api.XpConclusao
import com.controlerotina.mobile.sessao.PreferenciasEmMemoria
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

const val HOJE = "2026-11-12"

fun bloco(id: String, ordem: Int, titulo: String = "T$id") = BlocoPlano(
    id = id, duracaoMinutos = 30, ordem = ordem, origem = OrigemBloco.TAREFA, tarefaId = "t$id",
    tarefa = TarefaResumo(id = "t$id", titulo = titulo),
)

fun plano(status: StatusPlano, vararg blocos: BlocoPlano, conflito: Boolean = false) = Plano(
    id = "p1", data = HOJE, status = status, temConflito = conflito,
    mensagem = if (conflito) "Nem tudo coube" else null, blocos = blocos.toList(),
)

fun execucao(id: String, status: StatusExecucao, blocoId: String = "b1", xp: Int = 0) = Execucao(
    id = id, blocoPlanoId = blocoId, status = status, iniciadoEm = "2026-11-12T10:00:00Z",
    xp = if (xp > 0) XpConclusao(ganhoNesteRequest = xp) else null,
)

fun tarefa(
    id: String,
    restante: Int = 60,
    status: StatusTarefa = StatusTarefa.PENDENTE,
    bloqueada: Boolean = false,
    temFilhos: Boolean = false,
    deletada: Boolean = false,
    parentId: String? = null,
    projetoId: String? = null,
) = Tarefa(
    id = id, titulo = "Tarefa $id", status = status, duracaoMinutos = 60, restanteMinutos = restante,
    bloqueada = bloqueada, temFilhos = temFilhos, deletada = deletada, parentId = parentId, projetoId = projetoId,
)

fun projeto(id: String, status: StatusProjeto = StatusProjeto.ATIVO) = Projeto(id = id, nome = "Projeto $id", status = status)

fun itemDecidir(id: String, decidirAte: String? = null, tarefaId: String? = null) =
    ItemDecidir(id = id, titulo = "Item $id", decidirAte = decidirAte, tarefaId = tarefaId)

fun erroHttp(status: Int, detalhe: String = "Recusado") = ApiErro.Http(status, ProblemDetail(status = status, detail = detalhe))

/** API em memória; cada campo pode ser trocado pelo teste. */
class FakeRotinaApi : RotinaApi {
    val chamadas = mutableListOf<String>()

    var planoAtual: Plano? = null
    var gerado: Plano = plano(StatusPlano.RASCUNHO, bloco("b1", 1))
    var itensExecucao: List<ItemExecucao> = emptyList()
    var habitos: List<HabitoDoDia> = emptyList()
    var paginas: List<List<Tarefa>> = emptyList()
    var falhaEm: Map<String, Throwable> = emptyMap()
    var xpConclusao = 0
    var tarefaPorId: (String) -> Tarefa = { com.controlerotina.mobile.estado.tarefa(it) }

    private fun registrar(nome: String) {
        chamadas += nome
        falhaEm[nome.substringBefore(':')]?.let { throw it }
    }

    override suspend fun me(): MeResponse = MeResponse("x@y.z").also { registrar("me") }

    override suspend fun plano(data: String): Plano? { registrar("plano:$data"); return planoAtual }
    override suspend fun gerarPlano(data: String): Plano { registrar("gerar:$data"); planoAtual = gerado; return gerado }
    override suspend fun aprovarPlano(id: String): Plano {
        registrar("aprovar:$id")
        val aprovado = planoAtual!!.copy(status = StatusPlano.APROVADO)
        planoAtual = aprovado
        return aprovado
    }

    override suspend fun execucoes(data: String): ExecucoesDoDia { registrar("execucoes:$data"); return ExecucoesDoDia(itensExecucao) }

    override suspend fun iniciarBloco(blocoId: String): Execucao {
        registrar("iniciarBloco:$blocoId")
        val exec = execucao("e-$blocoId", StatusExecucao.EM_ANDAMENTO, blocoId)
        itensExecucao = itensExecucao.filter { it.bloco.id != blocoId } +
            ItemExecucao(BlocoExecucao(blocoId, 30, 1), TarefaResumo("t", "T$blocoId"), null, exec)
        return exec
    }

    override suspend fun concluirExecucao(id: String): Execucao {
        registrar("concluir:$id")
        return execucao(id, StatusExecucao.CONCLUIDO, xp = xpConclusao)
    }

    override suspend fun abortarExecucao(id: String): Execucao {
        registrar("abortar:$id")
        return execucao(id, StatusExecucao.ABORTADO)
    }

    val filtros = mutableListOf<FiltroTarefas>()
    /** Filhos por pai (`parentId`) e tarefas por projeto (`projetoId`). */
    var filhosDe: Map<String, List<Tarefa>> = emptyMap()
    var tarefasDoProjeto: Map<String, List<Tarefa>> = emptyMap()

    override suspend fun tarefas(pagina: Int, tamanho: Int, filtro: FiltroTarefas): PaginaTarefas {
        filtros += filtro
        filtro.parentId?.let { registrar("filhos:$it"); return PaginaTarefas(filhosDe[it].orEmpty(), 0, 1) }
        filtro.projetoId?.let { registrar("tarefasProjeto:$it"); return PaginaTarefas(tarefasDoProjeto[it].orEmpty(), 0, 1) }
        registrar("tarefas:$pagina")
        val conteudo = paginas.getOrElse(pagina) { emptyList() }
        return PaginaTarefas(conteudo, pagina, paginas.size)
    }

    val patches = mutableListOf<Pair<String, JsonObject>>()
    override suspend fun atualizarTarefa(id: String, campos: JsonObject): Tarefa {
        registrar("patchTarefa:$id")
        patches += id to campos
        val atual = tarefaPorId(id)
        return atual.copy(titulo = campos["titulo"]?.jsonPrimitive?.content ?: atual.titulo)
    }

    override suspend fun tarefa(id: String): Tarefa { registrar("tarefa:$id"); return tarefaPorId(id) }

    var criadas = mutableListOf<CriarTarefaRequest>()
    override suspend fun criarTarefa(pedido: CriarTarefaRequest): Tarefa {
        registrar("criar")
        criadas += pedido
        return com.controlerotina.mobile.estado.tarefa("nova").copy(
            titulo = pedido.titulo, parentId = pedido.parentId, projetoId = pedido.projetoId,
        )
    }

    override suspend fun iniciarTarefa(id: String, duracaoMinutos: Int, data: String): Execucao {
        registrar("iniciarTarefa:$id:$duracaoMinutos")
        return execucao("e-$id", StatusExecucao.EM_ANDAMENTO)
    }

    override suspend fun concluirTarefaDireto(id: String, duracaoMinutos: Int, data: String): Execucao {
        registrar("concluirTarefa:$id:$duracaoMinutos")
        return execucao("e-$id", StatusExecucao.CONCLUIDO, xp = xpConclusao)
    }

    override suspend fun habitosDoDia(data: String): List<HabitoDoDia> { registrar("habitos:$data"); return habitos }

    override suspend fun iniciarHabito(id: String, data: String): Execucao {
        registrar("iniciarHabito:$id")
        return execucao("eh-$id", StatusExecucao.EM_ANDAMENTO)
    }

    override suspend fun concluirHabitoDireto(id: String, data: String): Execucao {
        registrar("concluirHabito:$id")
        habitos = habitos.map { if (it.id == id) it.copy(cumprido = true, streakAtual = it.streakAtual + 1) else it }
        return execucao("eh-$id", StatusExecucao.CONCLUIDO, xp = xpConclusao)
    }

    // ---- Projetos ----
    var projetosPorStatus: Map<StatusProjeto, List<Projeto>> = emptyMap()
    val projetosCriados = mutableListOf<CriarProjetoRequest>()

    override suspend fun projetos(status: StatusProjeto): List<Projeto> {
        registrar("projetos:$status")
        return projetosPorStatus[status].orEmpty()
    }

    override suspend fun projeto(id: String): Projeto {
        registrar("projeto:$id")
        return projetosPorStatus.values.flatten().firstOrNull { it.id == id } ?: com.controlerotina.mobile.estado.projeto(id)
    }

    override suspend fun criarProjeto(pedido: CriarProjetoRequest): Projeto {
        registrar("criarProjeto")
        projetosCriados += pedido
        return Projeto(id = "novo", nome = pedido.nome, descricao = pedido.descricao)
    }

    override suspend fun atualizarProjeto(id: String, campos: JsonObject): Projeto {
        registrar("patchProjeto:$id")
        patches += id to campos
        return Projeto(id = id, nome = campos["nome"]?.jsonPrimitive?.content ?: "Projeto $id")
    }

    override suspend fun arquivarProjeto(id: String): Projeto {
        registrar("arquivar:$id")
        return com.controlerotina.mobile.estado.projeto(id, StatusProjeto.ARQUIVADO)
    }

    override suspend fun reativarProjeto(id: String): Projeto {
        registrar("reativar:$id")
        return com.controlerotina.mobile.estado.projeto(id, StatusProjeto.ATIVO)
    }

    // ---- Decidir ----
    var abertos: List<ItemDecidir> = emptyList()
    var decididos: List<ItemDecidir> = emptyList()
    val decidirCriados = mutableListOf<CriarDecidirRequest>()
    val promocoes = mutableListOf<Pair<String, PromoverDecidirRequest>>()

    override suspend fun decidir(promovidos: Boolean): List<ItemDecidir> {
        registrar("decidir:$promovidos")
        return if (promovidos) decididos else abertos
    }

    override suspend fun criarDecidir(pedido: CriarDecidirRequest): ItemDecidir {
        registrar("criarDecidir")
        decidirCriados += pedido
        return ItemDecidir(id = "novo", titulo = pedido.titulo, decidirAte = pedido.decidirAte)
    }

    override suspend fun atualizarDecidir(id: String, campos: JsonObject): ItemDecidir {
        registrar("patchDecidir:$id")
        patches += id to campos
        return ItemDecidir(id = id, titulo = campos["titulo"]?.jsonPrimitive?.content ?: "Item $id")
    }

    override suspend fun excluirDecidir(id: String) {
        registrar("excluirDecidir:$id")
    }

    override suspend fun promoverDecidir(id: String, pedido: PromoverDecidirRequest): ItemDecidir {
        registrar("promover:$id")
        promocoes += id to pedido
        return ItemDecidir(id = id, titulo = "Item $id", tarefaId = "t-$id")
    }
}

class FakeAutenticador : Autenticador {
    override val sessaoExpirada = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    var guardada = false
    var restaura: () -> Boolean = { true }
    var loginFalha: Throwable? = null
    var logouts = 0

    override suspend fun temSessaoGuardada() = guardada
    override suspend fun login(email: String, senha: String) { loginFalha?.let { throw it } }
    override suspend fun restaurar() = restaura()
    override suspend fun logout() { logouts++ }
}

fun sessaoStore(api: RotinaApi, prefs: PreferenciasEmMemoria = PreferenciasEmMemoria()) =
    SessaoAtivaStore(api, prefs) { HOJE }
