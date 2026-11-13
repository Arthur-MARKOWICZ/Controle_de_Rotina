package com.controlerotina.mobile.estado

import com.controlerotina.mobile.api.ApiErro
import com.controlerotina.mobile.api.HabitoDoDia
import com.controlerotina.mobile.api.RotinaApi
import com.controlerotina.mobile.dominio.podeAgirNoHabito
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.coroutines.cancellation.CancellationException

data class EstadoHabitos(
    val habitos: List<HabitoDoDia> = emptyList(),
    val carregando: Boolean = true,
    val atualizando: Boolean = false,
    val erro: String? = null,
    val ocupado: String? = null,
)

/** Tela Hábitos: hábitos do dia com iniciar e concluir direto (spec mobile-habitos). */
class HabitosViewModel(
    private val api: RotinaApi,
    private val sessaoAtiva: SessaoAtivaStore,
    private val hoje: () -> String,
    val escopo: CoroutineScope,
) {
    private val _estado = MutableStateFlow(EstadoHabitos())
    val estado: StateFlow<EstadoHabitos> = _estado.asStateFlow()

    fun podeAgir(habito: HabitoDoDia) = podeAgirNoHabito(habito)

    /** Volta ao estado inicial, sem nada do usuário anterior na memória. */
    fun limpar() {
        _estado.value = EstadoHabitos()
    }

    suspend fun carregar(puxando: Boolean = false) {
        _estado.update {
            if (it.habitos.isEmpty() && !puxando) it.copy(carregando = true, erro = null)
            else it.copy(atualizando = true, erro = null)
        }
        try {
            val lista = api.habitosDoDia(hoje())
            _estado.update { it.copy(habitos = lista, carregando = false, atualizando = false) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiErro) {
            _estado.update { it.copy(carregando = false, atualizando = false, erro = mensagemDeErro(e)) }
        }
    }

    suspend fun iniciar(habito: HabitoDoDia) = acao(habito) {
        val execucao = api.iniciarHabito(habito.id, hoje())
        sessaoAtiva.registrarInicio(habito.titulo, habito.duracaoMinutos, execucao)
    }

    suspend fun concluir(habito: HabitoDoDia) = acao(habito) {
        val execucao = api.concluirHabitoDireto(habito.id, hoje())
        sessaoAtiva.avisar(mensagemConclusao(execucao.xp))
        sessaoAtiva.notificarMudanca()
    }

    private suspend fun acao(habito: HabitoDoDia, bloco: suspend () -> Unit) {
        if (!podeAgirNoHabito(habito) || _estado.value.ocupado != null) return
        _estado.update { it.copy(ocupado = habito.id) }
        try {
            bloco()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiErro) {
            sessaoAtiva.avisar(mensagemDeErro(e))
        } finally {
            _estado.update { it.copy(ocupado = null) }
            carregar()
        }
    }
}
