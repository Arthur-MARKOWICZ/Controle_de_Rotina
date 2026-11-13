package com.controlerotina.mobile.estado

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Abas da barra inferior, nesta ordem (spec `mobile-navegacao`). */
enum class Aba(val rotulo: String) {
    TAREFAS("Tarefas"),
    PLANO("Plano"),
    HABITOS("Hábitos"),
    PROJETOS("Projetos"),
    DECIDIR("Decidir"),
}

/** Telas empilhadas por cima da aba atual (design D5). */
sealed interface Destino {
    data object Perfil : Destino
    data class TarefaDetalhe(val id: String) : Destino
    data class ProjetoDetalhe(val id: String) : Destino
    data class DecidirDetalhe(val id: String) : Destino
}

/**
 * Aba e pilha de detalhes. Vive no grafo (não no Compose) para sobreviver à rotação
 * sem `rememberSaveable` de tipos próprios.
 */
class Navegacao {
    private val _aba = MutableStateFlow(Aba.TAREFAS)
    val aba: StateFlow<Aba> = _aba.asStateFlow()

    private val _pilha = MutableStateFlow<List<Destino>>(emptyList())
    val pilha: StateFlow<List<Destino>> = _pilha.asStateFlow()

    fun selecionar(aba: Aba) {
        _aba.value = aba
        _pilha.value = emptyList()
    }

    fun abrir(destino: Destino) = _pilha.update { if (it.lastOrNull() == destino) it else it + destino }

    /** `false` se não havia nada para fechar (o voltar do sistema segue o padrão). */
    fun voltar(): Boolean {
        if (_pilha.value.isEmpty()) return false
        _pilha.update { it.dropLast(1) }
        return true
    }

    /** Login novo ou logout: volta para a aba inicial. */
    fun limpar() {
        _aba.value = Aba.TAREFAS
        _pilha.value = emptyList()
    }
}
