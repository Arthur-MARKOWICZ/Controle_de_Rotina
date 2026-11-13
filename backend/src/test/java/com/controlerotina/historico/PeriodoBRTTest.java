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

    @Test
    void deveCobrirUltimos90DiasTerminandoHojeEmBrt() {
        PeriodoBRT.Intervalo intervalo = PeriodoBRT.intervaloUltimosDias(LocalDate.of(2026, 11, 13), 90);

        assertThat(intervalo.inicioLocal()).isEqualTo(LocalDate.of(2026, 8, 16));
        assertThat(intervalo.fimLocal()).isEqualTo(LocalDate.of(2026, 11, 13));
        assertThat(intervalo.inicioInclusivo()).isEqualTo(Instant.parse("2026-08-16T03:00:00Z"));
        assertThat(intervalo.fimExclusivo()).isEqualTo(Instant.parse("2026-11-14T03:00:00Z"));
    }

    @Test
    void deveCruzarViradaDeAnoNosUltimosDias() {
        PeriodoBRT.Intervalo intervalo = PeriodoBRT.intervaloUltimosDias(LocalDate.of(2027, 1, 2), 5);

        assertThat(intervalo.inicioLocal()).isEqualTo(LocalDate.of(2026, 12, 29));
        assertThat(intervalo.fimExclusivo()).isEqualTo(Instant.parse("2027-01-03T03:00:00Z"));
    }

    @Test
    void deveCobrirSoHojeQuandoUmDia() {
        PeriodoBRT.Intervalo intervalo = PeriodoBRT.intervaloUltimosDias(LocalDate.of(2026, 3, 1), 1);

        assertThat(intervalo.inicioLocal()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(intervalo.inicioInclusivo()).isEqualTo(Instant.parse("2026-03-01T03:00:00Z"));
        assertThat(intervalo.fimExclusivo()).isEqualTo(Instant.parse("2026-03-02T03:00:00Z"));
    }

    @Test
    void deveRejeitarUltimosDiasZero() {
        assertThatThrownBy(() -> PeriodoBRT.intervaloUltimosDias(LocalDate.of(2026, 11, 13), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveRejeitarUltimosDiasNegativo() {
        assertThatThrownBy(() -> PeriodoBRT.intervaloUltimosDias(LocalDate.of(2026, 11, 13), -3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveRejeitarHojeNuloNosUltimosDias() {
        assertThatThrownBy(() -> PeriodoBRT.intervaloUltimosDias(null, 90))
                .isInstanceOf(NullPointerException.class);
    }
}
