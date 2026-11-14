package com.controlerotina.mobile.lembrete

import com.controlerotina.mobile.dominio.TipoLembrete
import com.controlerotina.mobile.dominio.proximoDisparo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

data class EstadoLembrete(val ligado: Boolean, val horario: LocalTime)

data class EstadoLembretes(
    val tipos: Map<TipoLembrete, EstadoLembrete> = TipoLembrete.entries.associateWith { EstadoLembrete(false, it.horarioPadrao) },
    /** Notificações bloqueadas pelo usuário: a tela mostra o atalho para as configurações. */
    val bloqueado: Boolean = false,
    val carregado: Boolean = false,
) {
    operator fun get(tipo: TipoLembrete): EstadoLembrete = tipos.getValue(tipo)
}

/** Perfil > Lembretes (design L5): liga/desliga e horário por tipo, sempre reagendando. */
class LembretesViewModel(
    private val preferencias: PreferenciasLembretes,
    private val agendador: AgendadorLembretes,
    private val permissao: PermissaoNotificacao,
    private val clock: Clock = Clock.System,
    private val fuso: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    private val _estado = MutableStateFlow(EstadoLembretes())
    val estado: StateFlow<EstadoLembretes> = _estado.asStateFlow()

    /**
     * O usuário recusou a permissão nesta sessão. Sobrevive ao [carregar] que o `onResume` faz
     * quando o diálogo do sistema fecha; sem isso o aviso de bloqueio sumiria logo após a recusa.
     */
    private var recusada = false

    /** Lê as preferências e o estado da permissão (que pode ter mudado nas configurações). */
    suspend fun carregar() {
        val tipos = TipoLembrete.entries.associateWith {
            EstadoLembrete(preferencias.ligado(it), preferencias.horario(it))
        }
        val algumLigado = tipos.values.any { it.ligado }
        val bloqueado = (algumLigado || recusada) && !permissao.concedida()
        _estado.value = EstadoLembretes(tipos, bloqueado = bloqueado, carregado = true)
    }

    /** `true` se ligar ainda vai abrir o diálogo do sistema: a tela explica o motivo antes. */
    fun precisaPedirPermissao(): Boolean = !permissao.concedida()

    fun abrirConfiguracoes() = permissao.abrirConfiguracoes()

    /** Liga pedindo a permissão antes; recusada, fica desligado e [EstadoLembretes.bloqueado]. */
    suspend fun ligar(tipo: TipoLembrete) {
        if (!permissao.concedida() && !permissao.solicitar()) {
            recusada = true
            _estado.update { it.copy(bloqueado = true) }
            return
        }
        recusada = false
        preferencias.salvarLigado(tipo, true)
        agendar(tipo, preferencias.horario(tipo))
        atualizar(tipo) { it.copy(ligado = true) }
        _estado.update { it.copy(bloqueado = false) }
    }

    suspend fun desligar(tipo: TipoLembrete) {
        preferencias.salvarLigado(tipo, false)
        agendador.cancelar(tipo)
        atualizar(tipo) { it.copy(ligado = false) }
    }

    suspend fun mudarHorario(tipo: TipoLembrete, horario: LocalTime) {
        preferencias.salvarHorario(tipo, horario)
        if (preferencias.ligado(tipo)) agendar(tipo, horario)
        atualizar(tipo) { it.copy(horario = horario) }
    }

    /** Depois do login/restauração: volta a agendar só os tipos ligados. */
    suspend fun reagendarLigados() {
        TipoLembrete.entries.forEach { tipo ->
            if (preferencias.ligado(tipo)) agendar(tipo, preferencias.horario(tipo))
        }
    }

    private fun agendar(tipo: TipoLembrete, horario: LocalTime) =
        agendador.agendar(tipo, proximoDisparo(clock.now(), horario, fuso()))

    private fun atualizar(tipo: TipoLembrete, mudar: (EstadoLembrete) -> EstadoLembrete) =
        _estado.update { it.copy(tipos = it.tipos + (tipo to mudar(it[tipo]))) }
}
