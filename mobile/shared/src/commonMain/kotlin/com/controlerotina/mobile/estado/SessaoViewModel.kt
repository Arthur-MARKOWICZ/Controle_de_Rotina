package com.controlerotina.mobile.estado

import com.controlerotina.mobile.api.ApiErro
import com.controlerotina.mobile.api.Autenticador
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

const val MSG_CREDENCIAIS = "E-mail ou senha inválidos"
const val MSG_SEM_CONEXAO = "Não foi possível conectar. Verifique sua conexão e tente de novo."
const val MSG_SESSAO_EXPIRADA = "Sua sessão expirou. Entre novamente."

sealed interface EstadoApp {
    data object Verificando : EstadoApp
    data object Autenticado : EstadoApp

    /** Tela de login; [aviso] explica por que o usuário voltou para ela (ex.: sessão expirada). */
    data class Login(val aviso: String? = null) : EstadoApp

    /** Havia sessão guardada, mas não foi possível falar com a API no boot. */
    data object SemConexaoNoBoot : EstadoApp
}

data class FormLogin(
    val email: String = "",
    val senha: String = "",
    val enviando: Boolean = false,
    val erro: String? = null,
)

/** Estado de autenticação do app inteiro: boot, login, expiração e logout. */
class SessaoViewModel(
    private val auth: Autenticador,
    val escopo: CoroutineScope,
    /** Roda depois de login ou restauração bem-sucedidos (ex.: reagendar lembretes). */
    private val aoAutenticar: suspend () -> Unit = {},
    /** Apaga o que é do usuário (telas, sessão ativa local) ao sair ou ao expirar a sessão. */
    private val aoEncerrar: suspend () -> Unit = {},
) {
    private val _estado = MutableStateFlow<EstadoApp>(EstadoApp.Verificando)
    val estado: StateFlow<EstadoApp> = _estado.asStateFlow()

    private val _form = MutableStateFlow(FormLogin())
    val form: StateFlow<FormLogin> = _form.asStateFlow()

    init {
        escopo.launch {
            auth.sessaoExpirada.collect {
                aoEncerrar()
                _form.value = FormLogin()
                _estado.value = EstadoApp.Login(MSG_SESSAO_EXPIRADA)
            }
        }
    }

    suspend fun iniciar() {
        _estado.value = EstadoApp.Verificando
        _estado.value = try {
            when {
                !auth.temSessaoGuardada() -> EstadoApp.Login()
                auth.restaurar() -> {
                    aoAutenticar()
                    EstadoApp.Autenticado
                }
                else -> {
                    aoEncerrar()
                    EstadoApp.Login(MSG_SESSAO_EXPIRADA)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: ApiErro.Rede) {
            EstadoApp.SemConexaoNoBoot
        } catch (_: ApiErro) {
            EstadoApp.SemConexaoNoBoot
        }
    }

    fun alterarEmail(valor: String) = _form.update { it.copy(email = valor, erro = null) }
    fun alterarSenha(valor: String) = _form.update { it.copy(senha = valor, erro = null) }

    suspend fun entrar() {
        val atual = _form.value
        if (atual.enviando) return
        if (atual.email.isBlank() || atual.senha.isEmpty()) {
            _form.update { it.copy(erro = "Informe e-mail e senha") }
            return
        }
        _form.update { it.copy(enviando = true, erro = null) }
        try {
            auth.login(atual.email.trim(), atual.senha)
            aoAutenticar()
            _form.value = FormLogin()
            _estado.value = EstadoApp.Autenticado
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiErro.Http) {
            val msg = if (e.status == 401 || e.status == 400) MSG_CREDENCIAIS else e.message ?: MSG_SEM_CONEXAO
            // Em erro de credenciais a senha digitada é apagada.
            _form.update { it.copy(enviando = false, senha = "", erro = msg) }
        } catch (_: ApiErro) {
            _form.update { it.copy(enviando = false, erro = MSG_SEM_CONEXAO) }
        }
    }

    suspend fun sair() {
        try {
            auth.logout()
        } catch (e: CancellationException) {
            throw e
        } catch (_: ApiErro) {
            // Sem rede ou API indisponível: a sessão local some do mesmo jeito (spec mobile-sessao).
        }
        aoEncerrar()
        _form.value = FormLogin()
        _estado.value = EstadoApp.Login()
    }
}
