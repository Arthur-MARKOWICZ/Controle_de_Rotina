package com.controlerotina.mobile.estado

import com.controlerotina.mobile.api.ApiErro
import com.controlerotina.mobile.api.CriarTarefaRequest
import com.controlerotina.mobile.api.FiltroTarefas
import com.controlerotina.mobile.api.Prioridade
import com.controlerotina.mobile.api.RotinaApi
import com.controlerotina.mobile.api.Tarefa
import com.controlerotina.mobile.dominio.diffPatch
import com.controlerotina.mobile.dominio.errosPorCampo
import com.controlerotina.mobile.dominio.formatarDataCurta
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.coroutines.cancellation.CancellationException

const val DESCRICAO_TAREFA_MAX = 2000

/** Campos editáveis da tarefa, como o usuário vê no formulário. */
data class CamposTarefa(
    val titulo: String = "",
    val descricao: String = "",
    val prioridade: Prioridade? = null,
    /** ISO-8601 UTC. */
    val prazo: String? = null,
    val prazoRotulo: String? = null,
    val duracao: String = "",
    val projetoId: String? = null,
) {
    companion object {
        fun de(t: Tarefa) = CamposTarefa(
            titulo = t.titulo,
            descricao = t.descricao.orEmpty(),
            prioridade = t.prioridade,
            prazo = t.prazo,
            prazoRotulo = formatarDataCurta(t.prazo),
            duracao = t.duracaoMinutos.toString(),
            projetoId = t.projetoId,
        )
    }
}

/**
 * Valores que o `PATCH /tarefas/{id}` entende. Prazo não tem "limpar" na API
 * (`null` mantém o atual), então só entra quando há um valor.
 */
internal fun CamposTarefa.paraPatch(duracaoEditavel: Boolean): Map<String, JsonElement> = buildMap {
    put("titulo", JsonPrimitive(titulo.trim()))
    put("descricao", JsonPrimitive(descricao.trim()))
    prioridade?.let { put("prioridade", JsonPrimitive(it.name)) }
    prazo?.let { put("prazo", JsonPrimitive(it)) }
    if (duracaoEditavel) duracao.toIntOrNull()?.let { put("duracaoMinutos", JsonPrimitive(it)) }
    put("projetoId", projetoId?.let(::JsonPrimitive) ?: JsonNull)
}

data class EstadoEditarTarefa(
    val carregando: Boolean = true,
    val erroCarga: String? = null,
    val original: Tarefa? = null,
    val campos: CamposTarefa = CamposTarefa(),
    val salvando: Boolean = false,
    val errosCampo: Map<String, String> = emptyMap(),
    val erroGeral: String? = null,
    val subtarefas: List<Tarefa> = emptyList(),
    val novaSubtarefa: String = "",
    val novaSubtarefaDuracao: String = "",
    val criandoSubtarefa: Boolean = false,
    val erroSubtarefa: String? = null,
) {
    /** Pai com filhos: a duração é a soma dos filhos, só leitura (spec `mobile-tarefas`). */
    val duracaoEditavel: Boolean get() = original?.temFilhos != true

    /** Só tarefa-raiz pode ganhar subtarefa: existe um nível só. */
    val aceitaSubtarefa: Boolean get() = original != null && original.parentId == null

    val alterado: Boolean
        get() = original != null &&
            diffPatch(CamposTarefa.de(original).paraPatch(duracaoEditavel), campos.paraPatch(duracaoEditavel)).isNotEmpty()
}

/**
 * Detalhe da tarefa: editar campos (`PATCH` parcial, design D6b) e criar subtarefa.
 * [aoSalvar] e [aoMudarFilhos] avisam a lista para trocar o card sem recarregar tudo.
 */
class EditarTarefaViewModel(
    private val api: RotinaApi,
    private val sessaoAtiva: SessaoAtivaStore,
    val escopo: CoroutineScope,
    private val aoSalvar: (Tarefa) -> Unit = {},
    private val aoMudarFilhos: suspend (paiId: String) -> Unit = {},
) {
    private val _estado = MutableStateFlow(EstadoEditarTarefa())
    val estado: StateFlow<EstadoEditarTarefa> = _estado.asStateFlow()

    suspend fun abrir(id: String) {
        _estado.value = EstadoEditarTarefa()
        try {
            val tarefa = api.tarefa(id)
            val filhos = if (tarefa.parentId == null && tarefa.temFilhos) buscarFilhos(id) else emptyList()
            _estado.value = EstadoEditarTarefa(
                carregando = false,
                original = tarefa,
                campos = CamposTarefa.de(tarefa),
                subtarefas = filhos,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiErro) {
            _estado.value = EstadoEditarTarefa(carregando = false, erroCarga = mensagemDeErro(e))
        }
    }

    private suspend fun buscarFilhos(paiId: String) =
        api.tarefas(0, TAMANHO_FILHOS, FiltroTarefas(parentId = paiId)).content

    fun alterar(mudar: (CamposTarefa) -> CamposTarefa) = _estado.update {
        val novos = mudar(it.campos)
        it.copy(
            campos = novos.copy(titulo = novos.titulo.take(TITULO_MAX), descricao = novos.descricao.take(DESCRICAO_TAREFA_MAX)),
            errosCampo = emptyMap(),
            erroGeral = null,
        )
    }

    /** `true` se salvou (ou não havia o que salvar) e a tela pode fechar. */
    suspend fun salvar(): Boolean {
        val e = _estado.value
        val original = e.original ?: return false
        if (e.salvando) return false
        if (e.campos.titulo.isBlank()) {
            _estado.update { it.copy(errosCampo = mapOf("titulo" to "Informe o título")) }
            return false
        }
        val corpo = diffPatch(CamposTarefa.de(original).paraPatch(e.duracaoEditavel), e.campos.paraPatch(e.duracaoEditavel))
        if (corpo.isEmpty()) return true
        _estado.update { it.copy(salvando = true, erroGeral = null) }
        return try {
            val salva = api.atualizarTarefa(original.id, corpo)
            _estado.update { it.copy(salvando = false, original = salva, campos = CamposTarefa.de(salva)) }
            aoSalvar(salva)
            original.parentId?.let { aoMudarFilhos(it) }
            sessaoAtiva.avisar("Tarefa salva")
            true
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: ApiErro.Http) {
            val porCampo = errosPorCampo(ex.problema)
            _estado.update {
                it.copy(salvando = false, errosCampo = porCampo, erroGeral = if (porCampo.isEmpty()) mensagemDeErro(ex) else null)
            }
            false
        } catch (ex: ApiErro) {
            _estado.update { it.copy(salvando = false, erroGeral = mensagemDeErro(ex)) }
            false
        }
    }

    // ---- Subtarefas ----

    fun alterarNovaSubtarefa(titulo: String) =
        _estado.update { it.copy(novaSubtarefa = titulo.take(TITULO_MAX), erroSubtarefa = null) }

    fun alterarNovaSubtarefaDuracao(v: String) =
        _estado.update { it.copy(novaSubtarefaDuracao = v.filter(Char::isDigit).take(4), erroSubtarefa = null) }

    /** Cria a subtarefa com `parentId` do pai aberto. Em erro, o título digitado fica. */
    suspend fun criarSubtarefa(): Boolean {
        val e = _estado.value
        val pai = e.original ?: return false
        if (!e.aceitaSubtarefa || e.criandoSubtarefa) return false
        val titulo = e.novaSubtarefa.trim()
        if (titulo.isEmpty()) {
            _estado.update { it.copy(erroSubtarefa = "Informe o título") }
            return false
        }
        _estado.update { it.copy(criandoSubtarefa = true, erroSubtarefa = null) }
        return try {
            api.criarTarefa(CriarTarefaRequest(titulo, e.novaSubtarefaDuracao.toIntOrNull(), parentId = pai.id))
            val paiAtual = api.tarefa(pai.id)
            val filhos = buscarFilhos(pai.id)
            _estado.update {
                it.copy(
                    criandoSubtarefa = false,
                    novaSubtarefa = "",
                    novaSubtarefaDuracao = "",
                    subtarefas = filhos,
                    original = paiAtual,
                    // Duração do pai passa a ser a soma dos filhos.
                    campos = it.campos.copy(duracao = paiAtual.duracaoMinutos.toString()),
                )
            }
            aoMudarFilhos(pai.id)
            true
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: ApiErro) {
            _estado.update { it.copy(criandoSubtarefa = false, erroSubtarefa = mensagemDeErro(ex)) }
            false
        }
    }
}
