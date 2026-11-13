package com.controlerotina.mobile.estado

import com.controlerotina.mobile.api.ApiErro
import com.controlerotina.mobile.api.BlocoPlano
import com.controlerotina.mobile.api.Execucao
import com.controlerotina.mobile.api.Plano
import com.controlerotina.mobile.api.RotinaApi
import com.controlerotina.mobile.api.StatusExecucao
import com.controlerotina.mobile.api.StatusPlano
import com.controlerotina.mobile.dominio.AcaoBloco
import com.controlerotina.mobile.dominio.acoesDoBloco
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.coroutines.cancellation.CancellationException

data class BlocoNaTela(
    val bloco: BlocoPlano,
    val execucao: Execucao?,
    val acoes: Set<AcaoBloco>,
) {
    val status: StatusExecucao get() = execucao?.status ?: StatusExecucao.NAO_INICIADO
}

sealed interface EstadoHoje {
    data object Carregando : EstadoHoje
    data class SemPlano(val gerando: Boolean = false) : EstadoHoje
    data class Conteudo(val plano: Plano, val blocos: List<BlocoNaTela>) : EstadoHoje
    data class Erro(val mensagem: String) : EstadoHoje
}

/** Tela Hoje: plano do dia, gerar/aprovar e execução dos blocos (spec mobile-hoje). */
class HojeViewModel(
    private val api: RotinaApi,
    private val sessaoAtiva: SessaoAtivaStore,
    private val hoje: () -> String,
    val escopo: CoroutineScope,
) {
    private val _estado = MutableStateFlow<EstadoHoje>(EstadoHoje.Carregando)
    val estado: StateFlow<EstadoHoje> = _estado.asStateFlow()

    private val _atualizando = MutableStateFlow(false)
    val atualizando: StateFlow<Boolean> = _atualizando.asStateFlow()

    /** Id do bloco (ou "plano") com ação em andamento, para desabilitar o botão. */
    private val _ocupado = MutableStateFlow<String?>(null)
    val ocupado: StateFlow<String?> = _ocupado.asStateFlow()

    /** Volta ao estado inicial, sem nada do usuário anterior na memória. */
    fun limpar() {
        _estado.value = EstadoHoje.Carregando
        _atualizando.value = false
        _ocupado.value = null
    }

    suspend fun carregar(puxando: Boolean = false) {
        if (puxando || _estado.value is EstadoHoje.Conteudo) _atualizando.value = true
        else _estado.value = EstadoHoje.Carregando
        try {
            val data = hoje()
            val plano = api.plano(data)
            _estado.value = if (plano == null) EstadoHoje.SemPlano() else montar(plano, data)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiErro) {
            if (_estado.value is EstadoHoje.Conteudo) sessaoAtiva.avisar(mensagemDeErro(e))
            else _estado.value = EstadoHoje.Erro(mensagemDeErro(e))
        } finally {
            _atualizando.value = false
        }
    }

    private suspend fun montar(plano: Plano, data: String): EstadoHoje.Conteudo {
        val execucoes = if (plano.status == StatusPlano.APROVADO) {
            api.execucoes(data).itens.associate { it.bloco.id to it.execucao }
        } else {
            emptyMap()
        }
        val blocos = plano.blocos.sortedBy { it.ordem }.map { bloco ->
            val exec = execucoes[bloco.id]
            BlocoNaTela(bloco, exec, acoesDoBloco(plano.status, exec?.status))
        }
        return EstadoHoje.Conteudo(plano, blocos)
    }

    suspend fun gerar() {
        if (_estado.value !is EstadoHoje.SemPlano) return
        _estado.value = EstadoHoje.SemPlano(gerando = true)
        try {
            val data = hoje()
            _estado.value = montar(api.gerarPlano(data), data)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiErro) {
            _estado.value = EstadoHoje.SemPlano()
            sessaoAtiva.avisar(mensagemDeErro(e))
        }
    }

    suspend fun aprovar() {
        val atual = _estado.value as? EstadoHoje.Conteudo ?: return
        if (atual.plano.status != StatusPlano.RASCUNHO) return
        acao("plano") {
            val aprovado = api.aprovarPlano(atual.plano.id)
            _estado.value = montar(aprovado, hoje())
            sessaoAtiva.avisar("Plano aprovado")
        }
    }

    suspend fun iniciar(item: BlocoNaTela) {
        if (AcaoBloco.INICIAR !in item.acoes) return
        acao(item.bloco.id) {
            val execucao = api.iniciarBloco(item.bloco.id)
            sessaoAtiva.registrarInicio(item.bloco.titulo, item.bloco.duracaoMinutos, execucao)
            carregar()
        }
    }

    suspend fun concluir(item: BlocoNaTela) {
        val id = item.execucao?.id ?: return
        if (AcaoBloco.CONCLUIR !in item.acoes) return
        acao(item.bloco.id) {
            val resultado = api.concluirExecucao(id)
            sessaoAtiva.avisar(mensagemConclusao(resultado.xp))
            if (sessaoAtiva.sessao.value?.execucaoId == id) sessaoAtiva.limpar()
            sessaoAtiva.notificarMudanca()
            carregar()
        }
    }

    suspend fun abortar(item: BlocoNaTela) {
        val id = item.execucao?.id ?: return
        if (AcaoBloco.ABORTAR !in item.acoes) return
        acao(item.bloco.id) {
            api.abortarExecucao(id)
            if (sessaoAtiva.sessao.value?.execucaoId == id) sessaoAtiva.limpar()
            sessaoAtiva.notificarMudanca()
            carregar()
        }
    }

    private suspend fun acao(chave: String, bloco: suspend () -> Unit) {
        if (_ocupado.value != null) return
        _ocupado.value = chave
        try {
            bloco()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiErro) {
            sessaoAtiva.avisar(mensagemDeErro(e))
            carregar()
        } finally {
            _ocupado.update { if (it == chave) null else it }
        }
    }
}
