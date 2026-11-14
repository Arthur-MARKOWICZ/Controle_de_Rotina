package com.controlerotina.mobile.lembrete

import com.controlerotina.mobile.dominio.Notificacao
import com.controlerotina.mobile.dominio.TipoLembrete
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant

val BRT = TimeZone.of("America/Sao_Paulo")

class RelogioFixo(var agora: Instant) : Clock {
    override fun now(): Instant = agora
}

class FakeNotificador(var permite: Boolean = true) : Notificador {
    val exibidas = mutableListOf<Notificacao>()
    var limpezas = 0
    override fun permitido() = permite
    override fun exibir(notificacao: Notificacao) { exibidas += notificacao }
    override fun limparTodas() {
        limpezas++
        exibidas.clear()
    }
}

class FakeAgendador : AgendadorLembretes {
    /** Próximo disparo por tipo; agendar de novo substitui (como `ExistingWorkPolicy.REPLACE`). */
    val agendados = mutableMapOf<TipoLembrete, Instant>()
    var cancelamentosTotais = 0
    override fun agendar(tipo: TipoLembrete, em: Instant) { agendados[tipo] = em }
    override fun cancelar(tipo: TipoLembrete) { agendados.remove(tipo) }
    override fun cancelarTodos() {
        cancelamentosTotais++
        agendados.clear()
    }
}

class FakePermissao(var concedida: Boolean = false, var respostaDoUsuario: Boolean = true) : PermissaoNotificacao {
    var pedidos = 0
    override fun concedida() = concedida
    override suspend fun solicitar(): Boolean {
        pedidos++
        concedida = respostaDoUsuario
        return concedida
    }
    var aberturasConfiguracoes = 0
    override fun abrirConfiguracoes() { aberturasConfiguracoes++ }
}
