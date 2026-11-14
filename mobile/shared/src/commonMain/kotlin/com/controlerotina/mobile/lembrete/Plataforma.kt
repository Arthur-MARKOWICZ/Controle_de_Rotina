package com.controlerotina.mobile.lembrete

import com.controlerotina.mobile.dominio.Notificacao
import com.controlerotina.mobile.dominio.TipoLembrete
import com.controlerotina.mobile.dominio.lerHorario
import com.controlerotina.mobile.dominio.formatarHorario
import com.controlerotina.mobile.sessao.PreferenciasLocais
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

// Fronteira com a plataforma (design L4). O Android implementa; os testes usam fakes.

/** Exibe e remove notificações de lembrete. */
interface Notificador {
    /** `false` se o usuário bloqueou as notificações do app (ou não concedeu no Android 13+). */
    fun permitido(): Boolean
    fun exibir(notificacao: Notificacao)
    fun limparTodas()
}

/** Agenda a próxima execução de cada tipo. Agendar de novo substitui a anterior. */
interface AgendadorLembretes {
    fun agendar(tipo: TipoLembrete, em: Instant)
    fun cancelar(tipo: TipoLembrete)
    fun cancelarTodos()
}

/** Pedido de permissão de notificação, com o diálogo do sistema quando ele existe. */
interface PermissaoNotificacao {
    fun concedida(): Boolean

    /** Pede ao usuário (Android 13+); devolve se ficou concedida. */
    suspend fun solicitar(): Boolean

    /** Abre as configurações de notificação do app no sistema (quando o usuário bloqueou). */
    fun abrirConfiguracoes()
}

/** Implementações vazias: plataformas sem lembrete (iOS, testes que não ligam para isso). */
object SemNotificacao : Notificador, AgendadorLembretes, PermissaoNotificacao {
    override fun permitido() = false
    override fun exibir(notificacao: Notificacao) = Unit
    override fun limparTodas() = Unit
    override fun agendar(tipo: TipoLembrete, em: Instant) = Unit
    override fun cancelar(tipo: TipoLembrete) = Unit
    override fun cancelarTodos() = Unit
    override fun concedida() = false
    override suspend fun solicitar() = false
    override fun abrirConfiguracoes() = Unit
}

/** Preferências de lembrete no aparelho: `lembrete.<tipo>.ligado|horario|ultimoDia`. */
class PreferenciasLembretes(private val preferencias: PreferenciasLocais) {
    private fun chave(tipo: TipoLembrete, campo: String) = "lembrete.${tipo.chave}.$campo"

    suspend fun ligado(tipo: TipoLembrete): Boolean = preferencias.ler(chave(tipo, "ligado")) == "true"

    suspend fun salvarLigado(tipo: TipoLembrete, ligado: Boolean) =
        preferencias.salvar(chave(tipo, "ligado"), ligado.toString())

    /** Horário salvo; inválido ou ausente cai no padrão do tipo. */
    suspend fun horario(tipo: TipoLembrete): LocalTime =
        lerHorario(preferencias.ler(chave(tipo, "horario"))) ?: tipo.horarioPadrao

    suspend fun salvarHorario(tipo: TipoLembrete, horario: LocalTime) =
        preferencias.salvar(chave(tipo, "horario"), formatarHorario(horario))

    suspend fun ultimoDia(tipo: TipoLembrete): String? = preferencias.ler(chave(tipo, "ultimoDia"))

    suspend fun marcarDisparo(tipo: TipoLembrete, dia: LocalDate) =
        preferencias.salvar(chave(tipo, "ultimoDia"), dia.toString())
}
