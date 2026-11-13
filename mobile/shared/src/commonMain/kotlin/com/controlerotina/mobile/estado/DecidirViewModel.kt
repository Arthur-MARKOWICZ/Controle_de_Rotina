package com.controlerotina.mobile.estado

import com.controlerotina.mobile.api.ApiErro
import com.controlerotina.mobile.api.CriarDecidirRequest
import com.controlerotina.mobile.api.ItemDecidir
import com.controlerotina.mobile.api.Prioridade
import com.controlerotina.mobile.api.PromoverDecidirRequest
import com.controlerotina.mobile.api.RotinaApi
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

const val DESCRICAO_DECIDIR_MAX = 2000

data class EstadoDecidir(
    val decididos: Boolean = false,
    val itens: List<ItemDecidir> = emptyList(),
    val carregando: Boolean = true,
    val atualizando: Boolean = false,
    val erro: String? = null,
)

/** Título, descrição e data limite: usado em capturar e editar. */
data class FormDecidir(
    val titulo: String = "",
    val descricao: String = "",
    /** ISO-8601 UTC. */
    val decidirAte: String? = null,
    val decidirAteRotulo: String? = null,
    val enviando: Boolean = false,
    val errosCampo: Map<String, String> = emptyMap(),
    val erroGeral: String? = null,
) {
    companion object {
        fun de(item: ItemDecidir) = FormDecidir(
            titulo = item.titulo,
            descricao = item.descricao.orEmpty(),
            decidirAte = item.decidirAte,
            decidirAteRotulo = formatarDataCurta(item.decidirAte),
        )
    }
}

private fun FormDecidir.limitado() =
    copy(titulo = titulo.take(TITULO_MAX), descricao = descricao.take(DESCRICAO_DECIDIR_MAX))

private fun FormDecidir.comErro(e: ApiErro): FormDecidir {
    val porCampo = if (e is ApiErro.Http) errosPorCampo(e.problema) else emptyMap()
    return copy(enviando = false, errosCampo = porCampo, erroGeral = if (porCampo.isEmpty()) mensagemDeErro(e) else null)
}

/** A API de Decidir aceita `null` para limpar descrição e data (`*Presente` no backend). */
private fun camposDecidir(titulo: String, descricao: String, decidirAte: String?): Map<String, JsonElement> = mapOf(
    "titulo" to JsonPrimitive(titulo.trim()),
    "descricao" to (descricao.trim().ifEmpty { null }?.let(::JsonPrimitive) ?: JsonNull),
    "decidirAte" to (decidirAte?.let(::JsonPrimitive) ?: JsonNull),
)

internal fun corpoEdicao(item: ItemDecidir, form: FormDecidir) = diffPatch(
    camposDecidir(item.titulo, item.descricao.orEmpty(), item.decidirAte),
    camposDecidir(form.titulo, form.descricao, form.decidirAte),
)

/** Aba Decidir: abertos ou decididos, e captura (spec `mobile-decidir`). */
class DecidirViewModel(
    private val api: RotinaApi,
    private val sessaoAtiva: SessaoAtivaStore,
    val escopo: CoroutineScope,
) {
    private val _estado = MutableStateFlow(EstadoDecidir())
    val estado: StateFlow<EstadoDecidir> = _estado.asStateFlow()

    private val _form = MutableStateFlow(FormDecidir())
    val form: StateFlow<FormDecidir> = _form.asStateFlow()

    fun limpar() {
        _estado.value = EstadoDecidir()
        _form.value = FormDecidir()
    }

    suspend fun recarregar(puxando: Boolean = false) {
        _estado.update { it.copy(carregando = it.itens.isEmpty(), atualizando = puxando, erro = null) }
        val decididos = _estado.value.decididos
        try {
            val itens = api.decidir(promovidos = decididos)
            _estado.update {
                if (it.decididos != decididos) it else it.copy(itens = itens, carregando = false, atualizando = false)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiErro) {
            _estado.update { it.copy(carregando = false, atualizando = false, erro = mensagemDeErro(e)) }
        }
    }

    suspend fun mostrarDecididos(decididos: Boolean) {
        if (_estado.value.decididos == decididos) return
        _estado.value = EstadoDecidir(decididos = decididos)
        recarregar()
    }

    fun alterarForm(mudar: (FormDecidir) -> FormDecidir) =
        _form.update { mudar(it).limitado().copy(errosCampo = emptyMap(), erroGeral = null) }

    fun limparForm() {
        _form.value = FormDecidir()
    }

    suspend fun criar(): Boolean {
        val f = _form.value
        if (f.enviando) return false
        val titulo = f.titulo.trim()
        if (titulo.isEmpty()) {
            _form.update { it.copy(errosCampo = mapOf("titulo" to "Informe o título")) }
            return false
        }
        _form.update { it.copy(enviando = true) }
        return try {
            val criado = api.criarDecidir(CriarDecidirRequest(titulo, f.descricao.trim().ifEmpty { null }, f.decidirAte))
            _form.value = FormDecidir()
            if (!_estado.value.decididos) _estado.update { it.copy(itens = listOf(criado) + it.itens) }
            sessaoAtiva.avisar("Anotado para decidir")
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiErro) {
            _form.update { it.comErro(e) }
            false
        }
    }

    /** O detalhe mudou ou tirou um item: reflete na lista sem recarregar. */
    fun aplicar(item: ItemDecidir?, idRemovido: String? = null) = _estado.update { e ->
        when {
            idRemovido != null -> e.copy(itens = e.itens.filter { it.id != idRemovido })
            item == null -> e
            item.promovido != e.decididos -> e.copy(itens = e.itens.filter { it.id != item.id })
            else -> e.copy(itens = e.itens.map { if (it.id == item.id) item else it })
        }
    }
}

data class FormPromover(
    val prioridade: Prioridade? = null,
    val prazo: String? = null,
    val prazoRotulo: String? = null,
    val duracao: String = "",
)

data class EstadoDecidirDetalhe(
    val item: ItemDecidir? = null,
    val form: FormDecidir = FormDecidir(),
    val promover: FormPromover = FormPromover(),
    val ocupado: Boolean = false,
    val erroAcao: String? = null,
) {
    val somenteLeitura: Boolean get() = item?.promovido == true
    val alterado: Boolean get() = item != null && !somenteLeitura && corpoEdicao(item, form).isNotEmpty()
}

/** Detalhe de um item: editar, descartar e promover a tarefa. */
class DecidirDetalheViewModel(
    private val api: RotinaApi,
    private val sessaoAtiva: SessaoAtivaStore,
    val escopo: CoroutineScope,
    private val aoMudar: (item: ItemDecidir?, idRemovido: String?) -> Unit = { _, _ -> },
    private val aoPromover: suspend () -> Unit = {},
) {
    private val _estado = MutableStateFlow(EstadoDecidirDetalhe())
    val estado: StateFlow<EstadoDecidirDetalhe> = _estado.asStateFlow()

    /** O item vem da lista (não há `GET` necessário para abrir). */
    fun abrir(item: ItemDecidir) {
        _estado.value = EstadoDecidirDetalhe(item = item, form = FormDecidir.de(item))
    }

    fun alterarForm(mudar: (FormDecidir) -> FormDecidir) = _estado.update {
        it.copy(form = mudar(it.form).limitado().copy(errosCampo = emptyMap(), erroGeral = null))
    }

    fun alterarPromover(mudar: (FormPromover) -> FormPromover) = _estado.update {
        it.copy(promover = mudar(it.promover).let { p -> p.copy(duracao = p.duracao.filter(Char::isDigit).take(4)) }, erroAcao = null)
    }

    suspend fun salvar(): Boolean {
        val e = _estado.value
        val item = e.item ?: return false
        if (e.somenteLeitura || e.form.enviando) return false
        if (e.form.titulo.isBlank()) {
            _estado.update { it.copy(form = it.form.copy(errosCampo = mapOf("titulo" to "Informe o título"))) }
            return false
        }
        val corpo = corpoEdicao(item, e.form)
        if (corpo.isEmpty()) return true
        _estado.update { it.copy(form = it.form.copy(enviando = true)) }
        return try {
            val salvo = api.atualizarDecidir(item.id, corpo)
            _estado.update { it.copy(item = salvo, form = FormDecidir.de(salvo)) }
            aoMudar(salvo, null)
            sessaoAtiva.avisar("Item salvo")
            true
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: ApiErro) {
            _estado.update { it.copy(form = it.form.comErro(ex)) }
            false
        }
    }

    /** `true` se descartou (a tela fecha). Não tem desfazer: a API apaga de vez. */
    suspend fun descartar(): Boolean = acao { item ->
        api.excluirDecidir(item.id)
        aoMudar(null, item.id)
        sessaoAtiva.avisar("Item descartado")
    }

    /** `true` se virou tarefa (a tela fecha). */
    suspend fun promover(): Boolean = acao { item ->
        val p = _estado.value.promover
        val promovido = api.promoverDecidir(item.id, PromoverDecidirRequest(p.prioridade, p.prazo, p.duracao.toIntOrNull()))
        aoMudar(promovido, null)
        aoPromover()
        sessaoAtiva.avisar("Virou tarefa")
    }

    private suspend fun acao(bloco: suspend (ItemDecidir) -> Unit): Boolean {
        val e = _estado.value
        val item = e.item ?: return false
        if (e.somenteLeitura || e.ocupado) return false
        _estado.update { it.copy(ocupado = true, erroAcao = null) }
        return try {
            bloco(item)
            _estado.update { it.copy(ocupado = false) }
            true
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: ApiErro) {
            _estado.update { it.copy(ocupado = false, erroAcao = mensagemDeErro(ex)) }
            false
        }
    }
}
