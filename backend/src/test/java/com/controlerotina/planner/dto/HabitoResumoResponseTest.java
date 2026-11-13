package com.controlerotina.planner.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.controlerotina.capacidade.entity.DiaSemana;
import com.controlerotina.habito.entity.Habito;
import com.controlerotina.habito.entity.StatusHabito;

class HabitoResumoResponseTest {

    @Test
    void t8_incluiDiasSemanaNoResumo() {
        Instant agora = Instant.parse("2026-10-20T12:00:00Z");
        Habito habito = new Habito(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Leitura",
                null,
                30,
                EnumSet.of(DiaSemana.SEGUNDA, DiaSemana.QUARTA),
                null,
                StatusHabito.ATIVO,
                2,
                4,
                agora,
                agora
        );

        HabitoResumoResponse resumo = HabitoResumoResponse.from(habito);

        assertThat(resumo.diasSemana()).containsExactlyInAnyOrder(DiaSemana.SEGUNDA, DiaSemana.QUARTA);
        assertThat(resumo.streakAtual()).isEqualTo(2);
        assertThat(resumo.titulo()).isEqualTo("Leitura");
        assertThat(resumo.id()).isEqualTo(habito.getId());
        assertThat(resumo.horarioPreferencial()).isNull();
    }

    @Test
    void streakZeroEDiasVaziosSaoPropagados() {
        Instant agora = Instant.parse("2026-10-20T12:00:00Z");
        Habito habito = new Habito(
                UUID.randomUUID(), UUID.randomUUID(), "Meditar", null, 10,
                EnumSet.noneOf(DiaSemana.class), null, StatusHabito.ATIVO, 0, 0, agora, agora);

        HabitoResumoResponse resumo = HabitoResumoResponse.from(habito);

        assertThat(resumo.streakAtual()).isZero();
        assertThat(resumo.diasSemana()).isEmpty();
    }

    @Test
    void horarioPreferencialEPropagado() {
        Instant agora = Instant.parse("2026-10-20T12:00:00Z");
        Habito habito = new Habito(
                UUID.randomUUID(), UUID.randomUUID(), "Corrida", null, 40,
                EnumSet.of(DiaSemana.SABADO), java.time.LocalTime.of(7, 30),
                StatusHabito.ATIVO, 1, 1, agora, agora);

        assertThat(HabitoResumoResponse.from(habito).horarioPreferencial())
                .isEqualTo(java.time.LocalTime.of(7, 30));
    }

    @Test
    void naoCopiaStreakMaximoParaStreakAtual() {
        Instant agora = Instant.parse("2026-10-20T12:00:00Z");
        Habito habito = new Habito(
                UUID.randomUUID(), UUID.randomUUID(), "X", null, 20,
                EnumSet.of(DiaSemana.DOMINGO), null, StatusHabito.PAUSADO, 1, 9, agora, agora);

        assertThat(HabitoResumoResponse.from(habito).streakAtual()).isEqualTo(1);
    }

    @Test
    void tituloEmBrancoPermaneceComoEsta() {
        Instant agora = Instant.parse("2026-10-20T12:00:00Z");
        Habito habito = new Habito(
                UUID.randomUUID(), UUID.randomUUID(), " ", null, 5,
                EnumSet.of(DiaSemana.SEGUNDA), null, StatusHabito.ATIVO, 0, 0, agora, agora);

        assertThat(HabitoResumoResponse.from(habito).titulo()).isEqualTo(" ");
    }

    @Test
    void umUnicoDiaSemanaNaoViraListaVazia() {
        Instant agora = Instant.parse("2026-10-20T12:00:00Z");
        Habito habito = new Habito(
                UUID.randomUUID(), UUID.randomUUID(), "Yoga", null, 45,
                EnumSet.of(DiaSemana.QUINTA), null, StatusHabito.ATIVO, 3, 3, agora, agora);

        assertThat(HabitoResumoResponse.from(habito).diasSemana()).containsExactly(DiaSemana.QUINTA);
    }
}
