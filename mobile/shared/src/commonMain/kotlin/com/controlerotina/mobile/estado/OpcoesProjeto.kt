package com.controlerotina.mobile.estado

import com.controlerotina.mobile.api.ApiErro
import com.controlerotina.mobile.api.Projeto
import com.controlerotina.mobile.api.RotinaApi
import com.controlerotina.mobile.api.StatusProjeto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.cancellation.CancellationException

/** Projetos ativos para o seletor de projeto da tarefa (spec `mobile-projetos`). */
class OpcoesProjeto(private val api: RotinaApi) {
    private val _ativos = MutableStateFlow<List<Projeto>>(emptyList())
    val ativos: StateFlow<List<Projeto>> = _ativos.asStateFlow()

    suspend fun carregar() {
        try {
            _ativos.value = api.projetos(StatusProjeto.ATIVO)
        } catch (e: CancellationException) {
            throw e
        } catch (_: ApiErro) {
            // Seletor fica com o que já tinha; criar/editar sem projeto continua funcionando.
        }
    }

    fun limpar() {
        _ativos.value = emptyList()
    }
}
