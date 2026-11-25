package com.controlerotina.mobile.estado

import com.controlerotina.mobile.api.ApiErro
import com.controlerotina.mobile.api.RotinaApi
import com.controlerotina.mobile.arquivo.SalvadorArquivo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** Palavra que o usuário digita para liberar a exclusão da conta. */
const val PALAVRA_CONFIRMACAO_EXCLUSAO = "EXCLUIR"

const val MSG_EXPORT_FALHOU = "Não foi possível exportar seus dados. Tente de novo."
const val MSG_EXPORT_SALVO = "Arquivo salvo."
const val MSG_SENHA_INCORRETA = "Senha atual incorreta."
const val MSG_EXCLUSAO_FALHOU = "Não foi possível excluir a conta. Tente de novo."

data class EstadoPerfil(
    val exportando: Boolean = false,
    val avisoExport: String? = null,
    val erroExport: String? = null,
    val dialogoExclusaoAberto: Boolean = false,
    val senha: String = "",
    val confirmacao: String = "",
    val excluindo: Boolean = false,
    val erroSenha: String? = null,
    val erroExclusao: String? = null,
) {
    /** Botão final só libera com senha e a palavra exata. */
    val exclusaoLiberada: Boolean
        get() = senha.isNotEmpty() && confirmacao == PALAVRA_CONFIRMACAO_EXCLUSAO && !excluindo
}

/** Exportar dados e excluir conta (LGPD) no Perfil do app. */
class PerfilViewModel(
    private val api: RotinaApi,
    private val salvador: SalvadorArquivo,
    private val hoje: () -> String,
    private val escopo: CoroutineScope,
    /** Roda depois que a API confirmou a exclusão: limpa a sessão e volta ao login. */
    private val aoExcluirConta: suspend () -> Unit,
) {
    private val _estado = MutableStateFlow(EstadoPerfil())
    val estado: StateFlow<EstadoPerfil> = _estado.asStateFlow()

    fun exportar() {
        if (_estado.value.exportando) return
        _estado.update { it.copy(exportando = true, erroExport = null, avisoExport = null) }
        escopo.launch {
            try {
                val conteudo = api.exportarDados()
                val salvo = salvador.salvar("controle-rotina-export-${hoje()}.json", "application/json", conteudo)
                _estado.update { it.copy(exportando = false, avisoExport = if (salvo) MSG_EXPORT_SALVO else null) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: ApiErro.ContaExcluida) {
                _estado.update { it.copy(exportando = false) }
            } catch (_: Exception) {
                _estado.update { it.copy(exportando = false, erroExport = MSG_EXPORT_FALHOU) }
            }
        }
    }

    fun abrirExclusao() = _estado.update {
        it.copy(dialogoExclusaoAberto = true, senha = "", confirmacao = "", erroSenha = null, erroExclusao = null)
    }

    fun fecharExclusao() {
        if (_estado.value.excluindo) return
        _estado.update { it.copy(dialogoExclusaoAberto = false, senha = "", confirmacao = "", erroSenha = null, erroExclusao = null) }
    }

    fun alterarSenha(valor: String) = _estado.update { it.copy(senha = valor, erroSenha = null) }

    fun alterarConfirmacao(valor: String) = _estado.update { it.copy(confirmacao = valor) }

    fun confirmarExclusao() {
        val atual = _estado.value
        if (!atual.exclusaoLiberada) return
        _estado.update { it.copy(excluindo = true, erroSenha = null, erroExclusao = null) }
        escopo.launch {
            try {
                api.excluirConta(atual.senha)
                _estado.value = EstadoPerfil()
                aoExcluirConta()
            } catch (e: CancellationException) {
                throw e
            } catch (_: ApiErro.ContaExcluida) {
                _estado.value = EstadoPerfil()
            } catch (e: ApiErro.Http) {
                val senhaErrada = e.status == 401
                _estado.update {
                    it.copy(
                        excluindo = false,
                        senha = if (senhaErrada) "" else it.senha,
                        erroSenha = if (senhaErrada) MSG_SENHA_INCORRETA else null,
                        erroExclusao = if (senhaErrada) null else MSG_EXCLUSAO_FALHOU,
                    )
                }
            } catch (_: Exception) {
                _estado.update { it.copy(excluindo = false, erroExclusao = MSG_EXCLUSAO_FALHOU) }
            }
        }
    }

    fun limpar() {
        _estado.value = EstadoPerfil()
    }
}
