package com.controlerotina.mobile.dominio

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// Regras puras de horário dos lembretes locais (change mobile-lembretes-locais, design L3/L4).

enum class TipoLembrete(val chave: String, val horarioPadrao: LocalTime) {
    RESUMO("resumo", LocalTime(8, 0)),
    PRAZO("prazo", LocalTime(19, 0)),
}

/** Atraso máximo aceito entre o horário escolhido e a execução real (spec: 2 horas). */
val TOLERANCIA_LEMBRETE: Duration = 2.hours

/** O sistema às vezes executa um pouco antes do previsto; até isso ainda conta como "no horário". */
private val ADIANTAMENTO_ACEITO: Duration = 5.minutes

/** Instante em que o relógio do [fuso] marca [horario] neste dia. */
fun LocalDate.no(horario: LocalTime, fuso: TimeZone): Instant = LocalDateTime(this, horario).toInstant(fuso)

/** Próximo instante, estritamente depois de [agora], em que o relógio do [fuso] marca [horario]. */
fun proximoDisparo(agora: Instant, horario: LocalTime, fuso: TimeZone): Instant {
    val hoje = agora.toLocalDateTime(fuso).date
    val candidato = hoje.no(horario, fuso)
    return if (candidato > agora) candidato else hoje.plus(1, DateTimeUnit.DAY).no(horario, fuso)
}

/**
 * Dia (local) a que pertence uma execução em [agora], ou `null` se ela caiu fora da janela
 * `[horario - 5min, horario + tolerancia]`. Olha ontem também: um lembrete das 23:30 executado
 * às 00:40 pertence a ontem.
 */
fun diaDoDisparo(
    agora: Instant,
    horario: LocalTime,
    fuso: TimeZone,
    tolerancia: Duration = TOLERANCIA_LEMBRETE,
): LocalDate? {
    val hoje = agora.toLocalDateTime(fuso).date
    return listOf(hoje, hoje.minus(1, DateTimeUnit.DAY), hoje.plus(1, DateTimeUnit.DAY)).firstOrNull { dia ->
        val alvo = dia.no(horario, fuso)
        agora >= alvo - ADIANTAMENTO_ACEITO && agora <= alvo + tolerancia
    }
}

/**
 * Intervalo de "amanhã" no [fuso], em instantes, para `prazoDe`/`prazoAte`.
 * O backend trata os dois limites como inclusivos, então o fim é o início de depois de amanhã - 1 ms.
 */
fun intervaloAmanha(agora: Instant, fuso: TimeZone): Pair<Instant, Instant> =
    intervaloDiaSeguinte(agora.toLocalDateTime(fuso).date, fuso)

/** Como [intervaloAmanha], mas a partir de um dia local (o dia a que o disparo pertence). */
fun intervaloDiaSeguinte(dia: LocalDate, fuso: TimeZone): Pair<Instant, Instant> {
    val amanha = dia.plus(1, DateTimeUnit.DAY)
    val inicio = amanha.atStartOfDayIn(fuso)
    val fim = amanha.plus(1, DateTimeUnit.DAY).atStartOfDayIn(fuso) - 1.milliseconds
    return inicio to fim
}

/** `HH:mm` → [LocalTime]; `null` se inválido. */
fun lerHorario(texto: String?): LocalTime? {
    val partes = texto?.split(":") ?: return null
    if (partes.size != 2 || partes.any { it.length != 2 }) return null
    val h = partes[0].toIntOrNull() ?: return null
    val m = partes[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return LocalTime(h, m)
}

/** [LocalTime] → `HH:mm`. */
fun formatarHorario(horario: LocalTime): String =
    "${horario.hour.toString().padStart(2, '0')}:${horario.minute.toString().padStart(2, '0')}"
