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
    }
}
