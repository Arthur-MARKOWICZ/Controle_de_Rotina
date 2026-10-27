package com.controlerotina.historico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class PeriodoBRTTest {

    @Test
    void deveConverterDiaParaIntervaloBrtComFimExclusivo() {
        PeriodoBRT.Intervalo intervalo = PeriodoBRT.intervaloDia(LocalDate.of(2026, 10, 27));

        assertThat(intervalo.inicioInclusivo()).isEqualTo(Instant.parse("2026-10-27T03:00:00Z"));
        assertThat(intervalo.fimExclusivo()).isEqualTo(Instant.parse("2026-10-28T03:00:00Z"));
        assertThat(intervalo.inicioLocal()).isEqualTo(LocalDate.of(2026, 10, 27));
        assertThat(intervalo.fimLocal()).isEqualTo(LocalDate.of(2026, 10, 27));
    }

    @Test
    void deveConverterSemanaSegDomComViradaDeDomingo() {
        LocalDate segunda = LocalDate.of(2026, 10, 26);
        PeriodoBRT.Intervalo intervalo = PeriodoBRT.intervaloSemana(segunda);

        assertThat(intervalo.inicioInclusivo()).isEqualTo(Instant.parse("2026-10-26T03:00:00Z"));
        assertThat(intervalo.fimExclusivo()).isEqualTo(Instant.parse("2026-11-02T03:00:00Z"));
        assertThat(intervalo.inicioLocal()).isEqualTo(segunda);
        assertThat(intervalo.fimLocal()).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    void deveRejeitarSemanaInicioQueNaoESegunda() {
        assertThatThrownBy(() -> PeriodoBRT.intervaloSemana(LocalDate.of(2026, 10, 27)))
                .isInstanceOf(SemanaInicioInvalidoException.class);
    }

    @Test
    void deveResolverSegundaDaSemanaCorrente() {
        assertThat(PeriodoBRT.segundaDaSemana(LocalDate.of(2026, 10, 28)))
                .isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(PeriodoBRT.segundaDaSemana(LocalDate.of(2026, 10, 26)))
                .isEqualTo(LocalDate.of(2026, 10, 26));
    }
}
