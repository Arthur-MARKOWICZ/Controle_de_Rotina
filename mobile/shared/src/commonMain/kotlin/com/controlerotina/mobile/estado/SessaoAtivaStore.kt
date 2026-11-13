package com.controlerotina.mobile.estado

import com.controlerotina.mobile.api.ApiErro
import com.controlerotina.mobile.api.Execucao
import com.controlerotina.mobile.api.RotinaApi
import com.controlerotina.mobile.api.StatusExecucao
import com.controlerotina.mobile.api.XpConclusao
import com.controlerotina.mobile.api.jsonApi
import com.controlerotina.mobile.sessao.PreferenciasLocais
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlin.coroutines.cancellation.CancellationException

@Serializable
data class SessaoAtiva(
    val execucaoId: String,
    val titulo: String,
    val duracaoMinutos: Int,
    val iniciadoEm: String,
    val data: String,
    /** Só a sessão avulsa de tarefa depende do resumo local: as outras vêm da API. */
    val avulsaDeTarefa: Boolean = false,
)

/** Mensagem curta para o snackbar global. */
fun mensagemConclusao(xp: XpConclusao?): String {
    val ganho = xp?.ganhoNesteRequest ?: 0
    return if (ganho > 0) "Concluído! +$ganho XP" else "Concluído!"
}

/** Texto amigável de qualquer falha de chamada, sem dados sensíveis. */
fun mensagemDeErro(e: Throwable): String = when (e) {
    is ApiErro.Rede -> MSG_SEM_CONEXAO
    is ApiErro.SessaoExpirada -> MSG_SESSAO_EXPIRADA
    is ApiErro.Http -> e.problema?.detail ?: e.problema?.title ?: "Não foi possível concluir a ação (erro ${e.status})."
    else -> "Algo deu errado. Tente de novo."
}

/**
 * Execução `EM_ANDAMENTO` do dia, compartilhada por todas as telas (barra de sessão ativa).
 *
 * A fonte é a API: `GET /execucoes` (blocos do plano aprovado) e `GET /habitos/do-dia`
 * (sessão avulsa de hábito). A sessão avulsa de tarefa não aparece em nenhuma listagem,
 * então um resumo dela fica em [PreferenciasLocais] para sobreviver ao processo morto.
 */
class SessaoAtivaStore(
    private val api: RotinaApi,
    private val preferencias: PreferenciasLocais,
    private val hoje: () -> String,
) {
    private val _sessao = MutableStateFlow<SessaoAtiva?>(null)
    val sessao: StateFlow<SessaoAtiva?> = _sessao.asStateFlow()

    private val _ocupado = MutableStateFlow(false)
    val ocupado: StateFlow<Boolean> = _ocupado.asStateFlow()

    /** Incrementa a cada mudança de execução, para as telas recarregarem. */
    private val _versao = MutableStateFlow(0)
    val versao: StateFlow<Int> = _versao.asStateFlow()

    private val _avisos = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val avisos: SharedFlow<String> = _avisos.asSharedFlow()

    fun avisar(mensagem: String) {
        _avisos.tryEmit(mensagem)
    }

    fun notificarMudanca() {
        _versao.value += 1
    }

    suspend fun recarregar() {
        val data = hoje()
        try {
            val doPlano = api.execucoes(data).itens
                .filter { it.execucao.status == StatusExecucao.EM_ANDAMENTO && it.execucao.id != null && it.execucao.iniciadoEm != null }
                .maxByOrNull { it.execucao.iniciadoEm!! }
                ?.let { SessaoAtiva(it.execucao.id!!, it.titulo, it.bloco.duracaoMinutos, it.execucao.iniciadoEm!!, data) }
            val doHabito = if (doPlano == null) {
                api.habitosDoDia(data).firstNotNullOfOrNull { habito ->
                    val slot = habito.execucaoAvulsa?.takeIf { it.status == StatusExecucao.EM_ANDAMENTO }
                    if (slot?.execucaoId != null && slot.iniciadoEm != null) {
                        SessaoAtiva(slot.execucaoId, habito.titulo, habito.duracaoMinutos, slot.iniciadoEm, data)
                    } else {
                        null
                    }
                }
            } else {
                null
            }
            val resolvida = doPlano ?: doHabito ?: lerAvulsaLocal()?.takeIf { it.data == data && it.avulsaDeTarefa }
            definir(resolvida)
        } catch (e: CancellationException) {
            throw e
        } catch (_: ApiErro) {
            // Sem rede: mantém o que já se sabia (ou o resumo local) em vez de sumir com a barra.
            if (_sessao.value == null) _sessao.value = lerAvulsaLocal()?.takeIf { it.data == data && it.avulsaDeTarefa }
        }
    }

    /** Chamado logo depois de qualquer "Iniciar" que devolveu a execução. */
    suspend fun registrarInicio(
        titulo: String,
        duracaoMinutos: Int,
        execucao: Execucao,
        avulsaDeTarefa: Boolean = false,
    ) {
        val id = execucao.id ?: return
        val inicio = execucao.iniciadoEm ?: return
        definir(SessaoAtiva(id, titulo, duracaoMinutos, inicio, hoje(), avulsaDeTarefa))
        notificarMudanca()
    }

    suspend fun concluir() {
        val atual = _sessao.value ?: return
        executar(atual) {
            val resultado = api.concluirExecucao(atual.execucaoId)
            avisar(mensagemConclusao(resultado.xp))
        }
    }

    suspend fun abortar() {
        val atual = _sessao.value ?: return
        executar(atual) {
            api.abortarExecucao(atual.execucaoId)
            avisar("Sessão abortada")
        }
    }

    private suspend fun executar(atual: SessaoAtiva, bloco: suspend () -> Unit) {
        if (_ocupado.value) return
        _ocupado.value = true
        try {
            bloco()
            definir(null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiErro.Http) {
            avisar(mensagemDeErro(e))
            // A execução já não está em andamento no servidor (concluída/abortada em outro lugar).
            if (e.status == 404 || e.status == 409 || e.status == 400) definir(null) else _sessao.value = atual
        } catch (e: ApiErro) {
            avisar(mensagemDeErro(e))
        } finally {
            _ocupado.value = false
            notificarMudanca()
        }
    }

    suspend fun limpar() {
        definir(null)
    }

    private suspend fun definir(sessao: SessaoAtiva?) {
        _sessao.value = sessao
        preferencias.salvar(CHAVE, sessao?.let { jsonApi.encodeToString(SessaoAtiva.serializer(), it) })
    }

    private suspend fun lerAvulsaLocal(): SessaoAtiva? = preferencias.ler(CHAVE)?.let {
        runCatching { jsonApi.decodeFromString(SessaoAtiva.serializer(), it) }.getOrNull()
    }

    private companion object {
        const val CHAVE = "sessao_ativa"
    }
}
