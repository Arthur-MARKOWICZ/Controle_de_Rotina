package com.controlerotina.habito.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.controlerotina.capacidade.entity.DiaSemana;
import com.controlerotina.habito.entity.Habito;
import com.controlerotina.habito.entity.StatusHabito;

class HabitoResponseTest {

    @Test
    void semVinculoDeObjetivoDevolveListaVazia() {
        HabitoResponse response = HabitoResponse.from(habito());

        assertThat(response.objetivos()).isEmpty();
    }

    @Test
    void objetivosNulosDevemVirarListaVazia() {
        HabitoResponse response = HabitoResponse.from(habito(), null, null, null);

        assertThat(response.objetivos()).isEmpty();
        assertThat(response.tags()).isEmpty();
    }

    private static Habito habito() {
        Instant agora = Instant.parse("2026-11-12T12:00:00Z");
        return new Habito(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Correr",
                null,
                30,
                Set.of(DiaSemana.SEGUNDA),
                null,
                StatusHabito.ATIVO,
                0,
                0,
                agora,
                agora
        );
    }
}
