package com.controlerotina.mobile.estado

import com.controlerotina.mobile.api.ApiErro
import com.controlerotina.mobile.api.CriarTarefaRequest
import com.controlerotina.mobile.api.FiltroTarefas
import com.controlerotina.mobile.api.RotinaApi
import com.controlerotina.mobile.api.Tarefa
import com.controlerotina.mobile.dominio.duracaoValida
import com.controlerotina.mobile.dominio.errosPorCampo
import com.controlerotina.mobile.dominio.podeSessaoAvulsa
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.coroutines.cancellation.CancellationException

const val TAMANHO_PAGINA = 20
const val TITULO_MAX = 120

/** Subtarefas de um pai cabem numa página (só um nível, poucos filhos). */
const val TAMANHO_FILHOS = 100

private val RAIZES = FiltroTarefas(somenteRaizes = true)

data class EstadoTarefas(
    val tarefas: List<Tarefa> = emptyList(),
    val carregandoInicial: Boolean = true,
    val carregandoMais: Boolean = false,
    val atualizando: Boolean = false,
    val proximaPagina: Int = 0,
    val fim: Boolean = false,
    val erro: String? = null,
    val ocupado: String? = null,
    /** Subtarefas já carregadas, por id do pai (design D6c). */
    val filhos: Map<String, List<Tarefa>> = emptyMap(),
    val expandidos: Set<String> = emptySet(),
)

data class FormNovaTarefa(
    val titulo: String = "",
    val duracao: String = "",
    /** ISO-8601 UTC já convertido para o fim do dia escolhido. */
    val prazo: String? = null,
    val prazoRotulo: String? = null,
    val projetoId: String? = null,
    val enviando: Boolean = false,
    val errosCampo: Map<String, String> = emptyMap(),
    val erroGeral: String? = null,
)

enum class AcaoSessaoTarefa { INICIAR, CONCLUIR }

/** Tela Tarefas: lista paginada, quick add e sessão avulsa (spec mobile-tarefas). */
class TarefasViewModel(
    private val api: RotinaApi,
    private val sessaoAtiva: SessaoAtivaStore,
    private val hoje: () -> String,
    val escopo: CoroutineScope,
) {
    private val _estado = MutableStateFlow(EstadoTarefas())
    val estado: StateFlow<EstadoTarefas> = _estado.asStateFlow()

    private val _form = MutableStateFlow(FormNovaTarefa())
    val form: StateFlow<FormNovaTarefa> = _form.asStateFlow()

    fun podeSessao(tarefa: Tarefa) = podeSessaoAvulsa(tarefa)

    /** Volta ao estado inicial, sem nada do usuário anterior na memória. */
    fun limpar() {
        _estado.value = EstadoTarefas()
        _form.value = FormNovaTarefa()
    }

    suspend fun recarregar(puxando: Boolean = false) {
        _estado.update {
            if (it.tarefas.isEmpty()) it.copy(carregandoInicial = true, erro = null)
            else it.copy(atualizando = puxando, erro = null)
        }
        try {
            val pagina = api.tarefas(0, TAMANHO_PAGINA, RAIZES)
            val raizes = pagina.content.mapTo(mutableSetOf()) { it.id }
            // Pais que continuam na lista seguem abertos, com os filhos buscados de novo.
            val expandidos = _estado.value.expandidos.filterTo(mutableSetOf()) { it in raizes }
            val filhos = expandidos.associateWith { buscarFilhos(it) }
            _estado.update {
                it.copy(
                    tarefas = pagina.content,
                    filhos = filhos,
                    expandidos = expandidos,
                    proximaPagina = 1,
                    fim = pagina.ultima,
                    carregandoInicial = false,
                    atualizando = false,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiErro) {
            _estado.update { it.copy(carregandoInicial = false, atualizando = false, erro = mensagemDeErro(e)) }
        }
    }

    suspend fun carregarMais() {
        val atual = _estado.value
        if (atual.fim || atual.carregandoMais || atual.carregandoInicial) return
        _estado.update { it.copy(carregandoMais = true) }
        try {
            val pagina = api.tarefas(atual.proximaPagina, TAMANHO_PAGINA, RAIZES)
            _estado.update { estado ->
                val ids = estado.tarefas.mapTo(mutableSetOf()) { it.id }
                estado.copy(
                    tarefas = estado.tarefas + pagina.content.filter { it.id !in ids },
                    proximaPagina = estado.proximaPagina + 1,
                    fim = pagina.ultima,
                    carregandoMais = false,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiErro) {
            _estado.update { it.copy(carregandoMais = false) }
            sessaoAtiva.avisar(mensagemDeErro(e))
        }
    }

    // ---- Subtarefas (design D6c) ----

    private suspend fun buscarFilhos(paiId: String): List<Tarefa> =
        api.tarefas(0, TAMANHO_FILHOS, FiltroTarefas(parentId = paiId)).content

    /** Abre o pai; busca os filhos só na primeira vez (até o próximo recarregar). */
    suspend fun expandir(paiId: String) {
        _estado.update { it.copy(expandidos = it.expandidos + paiId) }
        if (_estado.value.filhos.containsKey(paiId)) return
        try {
            val filhos = buscarFilhos(paiId)
            _estado.update { it.copy(filhos = it.filhos + (paiId to filhos)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiErro) {
            _estado.update { it.copy(expandidos = it.expandidos - paiId) }
            sessaoAtiva.avisar(mensagemDeErro(e))
        }
    }

    fun recolher(paiId: String) = _estado.update { it.copy(expandidos = it.expandidos - paiId) }

    /** Troca uma tarefa (raiz ou filha) pela versão nova, sem recarregar a lista. */
    fun substituir(tarefa: Tarefa) = _estado.update { estado ->
        estado.copy(
            tarefas = estado.tarefas.map { if (it.id == tarefa.id) tarefa else it },
            filhos = estado.filhos.mapValues { (_, lista) -> lista.map { if (it.id == tarefa.id) tarefa else it } },
        )
    }

    /** Depois de criar subtarefa: o pai muda (`temFilhos`, duração) e, se aberto, os filhos também. */
    suspend fun atualizarPai(paiId: String) {
        try {
            substituir(api.tarefa(paiId))
            if (_estado.value.filhos.containsKey(paiId) || paiId in _estado.value.expandidos) {
                val filhos = buscarFilhos(paiId)
                _estado.update { it.copy(filhos = it.filhos + (paiId to filhos)) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: ApiErro) {
            // O próximo recarregar corrige.
        }
    }

    // ---- Quick add ----

    fun alterarTitulo(v: String) = _form.update {
        it.copy(titulo = v.take(TITULO_MAX), errosCampo = it.errosCampo - "titulo", erroGeral = null)
    }

    fun alterarDuracao(v: String) = _form.update {
        it.copy(duracao = v.filter(Char::isDigit).take(4), errosCampo = it.errosCampo - "duracaoMinutos")
    }

    fun alterarPrazo(isoUtc: String?, rotulo: String?) = _form.update {
        it.copy(prazo = isoUtc, prazoRotulo = rotulo, errosCampo = it.errosCampo - "prazo")
    }

    fun alterarProjeto(id: String?) = _form.update { it.copy(projetoId = id, errosCampo = it.errosCampo - "projetoId") }

    fun limparForm() {
        _form.value = FormNovaTarefa()
    }

    /** `true` se criou (a tela fecha o formulário). */
    suspend fun criar(): Boolean {
        val f = _form.value
        if (f.enviando) return false
        val titulo = f.titulo.trim()
        if (titulo.isEmpty()) {
            _form.update { it.copy(errosCampo = it.errosCampo + ("titulo" to "Informe o título")) }
            return false
        }
        _form.update { it.copy(enviando = true, erroGeral = null) }
        return try {
            val criada = api.criarTarefa(CriarTarefaRequest(titulo, f.duracao.toIntOrNull(), f.prazo, f.projetoId))
            _estado.update { it.copy(tarefas = listOf(criada) + it.tarefas.filter { t -> t.id != criada.id }) }
            _form.value = FormNovaTarefa()
            sessaoAtiva.avisar("Tarefa criada")
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiErro.Http) {
            val porCampo = errosPorCampo(e.problema)
            _form.update {
                it.copy(
                    enviando = false,
                    errosCampo = porCampo,
                    erroGeral = if (porCampo.isEmpty()) mensagemDeErro(e) else null,
                )
            }
            false
        } catch (e: ApiErro) {
            _form.update { it.copy(enviando = false, erroGeral = mensagemDeErro(e)) }
            false
        }
    }

    // ---- Sessão avulsa ----

    suspend fun executarSessao(tarefa: Tarefa, acao: AcaoSessaoTarefa, duracaoMinutos: Int) {
        if (!podeSessaoAvulsa(tarefa)) return
        val duracao = duracaoValida(duracaoMinutos, tarefa.restanteMinutos) ?: return
        if (_estado.value.ocupado != null) return
        _estado.update { it.copy(ocupado = tarefa.id) }
        try {
            when (acao) {
                AcaoSessaoTarefa.INICIAR -> {
                    val execucao = api.iniciarTarefa(tarefa.id, duracao, hoje())
                    sessaoAtiva.registrarInicio(tarefa.titulo, duracao, execucao, avulsaDeTarefa = true)
                }
                AcaoSessaoTarefa.CONCLUIR -> {
                    val execucao = api.concluirTarefaDireto(tarefa.id, duracao, hoje())
                    sessaoAtiva.avisar(mensagemConclusao(execucao.xp))
                    sessaoAtiva.notificarMudanca()
                }
            }
            atualizarCard(tarefa.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiErro) {
            // Recusa (bloqueada, sem restante...): mostra o motivo e traz o card atualizado.
            sessaoAtiva.avisar(mensagemDeErro(e))
            atualizarCard(tarefa.id)
        } finally {
            _estado.update { it.copy(ocupado = null) }
        }
    }

    private suspend fun atualizarCard(id: String) {
        try {
            substituir(api.tarefa(id))
        } catch (e: CancellationException) {
            throw e
        } catch (_: ApiErro) {
            // Card fica como estava; o próximo puxar-para-atualizar corrige.
        }
    }
}
