package com.controlerotina.projeto.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.controlerotina.projeto.entity.Projeto;
import com.controlerotina.projeto.entity.StatusProjeto;

class ProjetoResponseTest {

    @Test
    void semVinculoDeObjetivoDevolveListaVazia() {
        ProjetoResponse response = ProjetoResponse.from(projeto());

        assertThat(response.objetivos()).isEmpty();
    }

    @Test
    void objetivosNulosDevemVirarListaVazia() {
        ProjetoResponse response = ProjetoResponse.from(
                projeto(), List.of(), null, List.of(), false, null);

        assertThat(response.objetivos()).isEmpty();
    }

    private static Projeto projeto() {
        Instant agora = Instant.parse("2026-11-12T12:00:00Z");
        return new Projeto(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Casa",
                null,
                StatusProjeto.ATIVO,
                agora,
                agora
        );
    }
}
