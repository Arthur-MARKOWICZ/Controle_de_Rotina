package com.controlerotina.mobile.estado

import com.controlerotina.mobile.api.ApiErro
import com.controlerotina.mobile.api.CriarProjetoRequest
import com.controlerotina.mobile.api.FiltroTarefas
import com.controlerotina.mobile.api.Projeto
import com.controlerotina.mobile.api.RotinaApi
import com.controlerotina.mobile.api.StatusProjeto
import com.controlerotina.mobile.api.Tarefa
import com.controlerotina.mobile.dominio.diffPatch
import com.controlerotina.mobile.dominio.errosPorCampo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.coroutines.cancellation.CancellationException

const val NOME_PROJETO_MAX = 80
const val DESCRICAO_PROJETO_MAX = 500

data class EstadoProjetos(
    val filtro: StatusProjeto = StatusProjeto.ATIVO,
    val projetos: List<Projeto> = emptyList(),
    val carregando: Boolean = true,
    val atualizando: Boolean = false,
    val erro: String? = null,
)

/** Formulário de nome + descrição, usado em criar e editar projeto. */
data class FormProjeto(
    val nome: String = "",
    val descricao: String = "",
    val enviando: Boolean = false,
    val errosCampo: Map<String, String> = emptyMap(),
    val erroGeral: String? = null,
)

private fun FormProjeto.limitado() =
    copy(nome = nome.take(NOME_PROJETO_MAX), descricao = descricao.take(DESCRICAO_PROJETO_MAX))

/** Aba Projetos: lista com filtro Ativos/Arquivados e criação (spec `mobile-projetos`). */
class ProjetosViewModel(
    private val api: RotinaApi,
    private val sessaoAtiva: SessaoAtivaStore,
    val escopo: CoroutineScope,
    private val aoMudarProjetos: suspend () -> Unit = {},
) {
    private val _estado = MutableStateFlow(EstadoProjetos())
    val estado: StateFlow<EstadoProjetos> = _estado.asStateFlow()

    private val _form = MutableStateFlow(FormProjeto())
    val form: StateFlow<FormProjeto> = _form.asStateFlow()

    fun limpar() {
        _estado.value = EstadoProjetos()
        _form.value = FormProjeto()
    }

    suspend fun recarregar(puxando: Boolean = false) {
        _estado.update { it.copy(carregando = it.projetos.isEmpty(), atualizando = puxando, erro = null) }
        val filtro = _estado.value.filtro
        try {
            val lista = api.projetos(filtro)
            _estado.update {
                if (it.filtro != filtro) it else it.copy(projetos = lista, carregando = false, atualizando = false)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiErro) {
            _estado.update { it.copy(carregando = false, atualizando = false, erro = mensagemDeErro(e)) }
        }
    }

    suspend fun filtrar(status: StatusProjeto) {
        if (_estado.value.filtro == status) return
        _estado.update { EstadoProjetos(filtro = status) }
        recarregar()
    }

    fun alterarForm(mudar: (FormProjeto) -> FormProjeto) =
        _form.update { mudar(it).limitado().copy(errosCampo = emptyMap(), erroGeral = null) }

    fun limparForm() {
        _form.value = FormProjeto()
    }

    suspend fun criar(): Boolean {
        val f = _form.value
        if (f.enviando) return false
        val nome = f.nome.trim()
        if (nome.isEmpty()) {
            _form.update { it.copy(errosCampo = mapOf("nome" to "Informe o nome")) }
            return false
        }
        _form.update { it.copy(enviando = true) }
        return try {
            val criado = api.criarProjeto(CriarProjetoRequest(nome, f.descricao.trim().ifEmpty { null }))
            _form.value = FormProjeto()
            if (_estado.value.filtro == StatusProjeto.ATIVO) {
                _estado.update { it.copy(projetos = it.projetos + criado) }
            }
            aoMudarProjetos()
            sessaoAtiva.avisar("Projeto criado")
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiErro) {
            _form.update { it.comErro(e) }
            false
        }
    }

    /** Detalhe mudou o projeto: troca, ou tira da lista se mudou de status. */
    fun aplicar(projeto: Projeto) = _estado.update { e ->
        val semEle = e.projetos.filter { it.id != projeto.id }
        val lista = when {
            projeto.status != e.filtro -> semEle
            e.projetos.any { it.id == projeto.id } -> e.projetos.map { if (it.id == projeto.id) projeto else it }
            else -> e.projetos + projeto
        }
        e.copy(projetos = lista)
    }
}

internal fun FormProjeto.comErro(e: ApiErro): FormProjeto {
    val porCampo = if (e is ApiErro.Http) errosPorCampo(e.problema) else emptyMap()
    return copy(enviando = false, errosCampo = porCampo, erroGeral = if (porCampo.isEmpty()) mensagemDeErro(e) else null)
}

data class EstadoProjetoDetalhe(
    val carregando: Boolean = true,
    val erroCarga: String? = null,
    val projeto: Projeto? = null,
    val form: FormProjeto = FormProjeto(),
    val tarefas: List<Tarefa> = emptyList(),
    val ocupado: Boolean = false,
) {
    val alterado: Boolean get() = projeto != null && corpoEdicao(projeto, form).isNotEmpty()
}

private fun camposProjeto(nome: String, descricao: String): Map<String, JsonElement> =
    mapOf("nome" to JsonPrimitive(nome.trim()), "descricao" to JsonPrimitive(descricao.trim()))

internal fun corpoEdicao(projeto: Projeto, form: FormProjeto) =
    diffPatch(camposProjeto(projeto.nome, projeto.descricao.orEmpty()), camposProjeto(form.nome, form.descricao))

/** Detalhe do projeto: editar, arquivar/reativar e tarefas do projeto. */
class ProjetoDetalheViewModel(
    private val api: RotinaApi,
    private val sessaoAtiva: SessaoAtivaStore,
    val escopo: CoroutineScope,
    private val aoMudar: suspend (Projeto) -> Unit = {},
) {
    private val _estado = MutableStateFlow(EstadoProjetoDetalhe())
    val estado: StateFlow<EstadoProjetoDetalhe> = _estado.asStateFlow()

    suspend fun abrir(id: String) {
        _estado.value = EstadoProjetoDetalhe()
        try {
            val projeto = api.projeto(id)
            _estado.value = EstadoProjetoDetalhe(
                carregando = false,
                projeto = projeto,
                form = FormProjeto(projeto.nome, projeto.descricao.orEmpty()),
            )
            recarregarTarefas()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiErro) {
            _estado.value = EstadoProjetoDetalhe(carregando = false, erroCarga = mensagemDeErro(e))
        }
    }

    suspend fun recarregarTarefas() {
        val id = _estado.value.projeto?.id ?: return
        try {
            val tarefas = api.tarefas(0, TAMANHO_FILHOS, FiltroTarefas(projetoId = id)).content
            _estado.update { if (it.projeto?.id == id) it.copy(tarefas = tarefas) else it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiErro) {
            sessaoAtiva.avisar(mensagemDeErro(e))
        }
    }

    /** Tarefa editada no detalhe dela: troca na lista do projeto ou some se saiu do projeto. */
    fun aplicarTarefa(tarefa: Tarefa) = _estado.update { e ->
        val id = e.projeto?.id
        e.copy(tarefas = if (tarefa.projetoId != id) e.tarefas.filter { it.id != tarefa.id } else e.tarefas.map { if (it.id == tarefa.id) tarefa else it })
    }

    fun alterarForm(mudar: (FormProjeto) -> FormProjeto) = _estado.update {
        it.copy(form = mudar(it.form).limitado().copy(errosCampo = emptyMap(), erroGeral = null))
    }

    suspend fun salvar(): Boolean {
        val e = _estado.value
        val projeto = e.projeto ?: return false
        if (e.form.enviando) return false
        if (e.form.nome.isBlank()) {
            _estado.update { it.copy(form = it.form.copy(errosCampo = mapOf("nome" to "Informe o nome"))) }
            return false
        }
        val corpo = corpoEdicao(projeto, e.form)
        if (corpo.isEmpty()) return true
        _estado.update { it.copy(form = it.form.copy(enviando = true)) }
        return try {
            val salvo = api.atualizarProjeto(projeto.id, corpo)
            _estado.update { it.copy(projeto = salvo, form = FormProjeto(salvo.nome, salvo.descricao.orEmpty())) }
            aoMudar(salvo)
            sessaoAtiva.avisar("Projeto salvo")
            true
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: ApiErro) {
            _estado.update { it.copy(form = it.form.comErro(ex)) }
            false
        }
    }

    suspend fun arquivar() = mudarStatus { api.arquivarProjeto(it) }

    suspend fun reativar() = mudarStatus { api.reativarProjeto(it) }

    private suspend fun mudarStatus(chamada: suspend (String) -> Projeto) {
        val projeto = _estado.value.projeto ?: return
        if (_estado.value.ocupado) return
        _estado.update { it.copy(ocupado = true) }
        try {
            val novo = chamada(projeto.id)
            _estado.update { it.copy(projeto = novo, ocupado = false) }
            aoMudar(novo)
            sessaoAtiva.avisar(if (novo.status == StatusProjeto.ARQUIVADO) "Projeto arquivado" else "Projeto reativado")
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiErro) {
            _estado.update { it.copy(ocupado = false) }
            sessaoAtiva.avisar(mensagemDeErro(e))
        }
    }
}
