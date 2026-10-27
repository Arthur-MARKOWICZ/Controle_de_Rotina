package com.controlerotina.historico;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

public final class PeriodoBRT {

    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private PeriodoBRT() {
    }

    public static Intervalo intervaloDia(LocalDate data) {
        Instant inicio = data.atStartOfDay(ZONE).toInstant();
        Instant fim = data.plusDays(1).atStartOfDay(ZONE).toInstant();
        return new Intervalo(inicio, fim, data, data);
    }

    public static Intervalo intervaloSemana(LocalDate semanaInicio) {
        garantirSegunda(semanaInicio);
        Instant inicio = semanaInicio.atStartOfDay(ZONE).toInstant();
        Instant fim = semanaInicio.plusDays(7).atStartOfDay(ZONE).toInstant();
        return new Intervalo(inicio, fim, semanaInicio, semanaInicio.plusDays(6));
    }

    public static LocalDate segundaDaSemana(LocalDate referencia) {
        return referencia.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public static void garantirSegunda(LocalDate semanaInicio) {
        if (semanaInicio.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new SemanaInicioInvalidoException(semanaInicio);
        }
    }

    public record Intervalo(Instant inicioInclusivo, Instant fimExclusivo, LocalDate inicioLocal, LocalDate fimLocal) {
    }
}
