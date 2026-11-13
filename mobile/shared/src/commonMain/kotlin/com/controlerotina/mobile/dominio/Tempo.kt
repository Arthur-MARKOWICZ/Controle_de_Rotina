package com.controlerotina.mobile.dominio

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.number
import kotlin.math.ceil
import kotlin.math.max
import kotlin.time.Clock
import kotlin.time.Instant

/** Data de "hoje" no fuso do aparelho, em ISO (`2026-11-12`), como a web faz. */
fun hojeIso(clock: Clock = Clock.System, fuso: TimeZone = TimeZone.currentSystemDefault()): String =
    clock.todayIn(fuso).toString()

/** Prazo escolhido num dia = fim desse dia (23:59) no fuso do aparelho, em ISO-8601 UTC. */
fun prazoFimDoDia(data: LocalDate, fuso: TimeZone = TimeZone.currentSystemDefault()): String =
    LocalDateTime(data, LocalTime(23, 59)).toInstant(fuso).toString()

private fun decorridoMinutos(iniciadoEm: String, agora: Instant): Double? =
    runCatching { (agora - Instant.parse(iniciadoEm)).inWholeSeconds / 60.0 }.getOrNull()

/** Minutos que faltam do bloco em andamento (nunca negativo). Calculado a partir da API. */
fun minutosRestantes(iniciadoEm: String, duracaoMinutos: Int, agora: Instant): Int {
    val decorrido = decorridoMinutos(iniciadoEm, agora) ?: return 0
    if (duracaoMinutos < 1) return 0
    return max(0, ceil(duracaoMinutos - decorrido).toInt())
}

/** Minutos além da duração prevista (0 enquanto dentro do tempo). */
fun minutosExcedidos(iniciadoEm: String, duracaoMinutos: Int, agora: Instant): Int {
    val decorrido = decorridoMinutos(iniciadoEm, agora) ?: return 0
    return max(0, ceil(decorrido - duracaoMinutos).toInt())
}

/** `45min`, `1h`, `1h05`. */
fun formatarMinutos(minutos: Int): String {
    val m = max(0, minutos)
    if (m < 60) return "${m}min"
    val h = m / 60
    val resto = m % 60
    return if (resto == 0) "${h}h" else "${h}h${resto.toString().padStart(2, '0')}"
}

/** Texto curto de um instante ISO no fuso do aparelho: `12/11`. */
fun formatarDataCurta(iso: String?, fuso: TimeZone = TimeZone.currentSystemDefault()): String? {
    if (iso == null) return null
    val data = runCatching { Instant.parse(iso).toLocalDateTime(fuso).date }.getOrNull() ?: return null
    return "${data.day.toString().padStart(2, '0')}/${data.month.number.toString().padStart(2, '0')}"
}
