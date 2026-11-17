package com.controlerotina.habito.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class AtualizarHabitoRequestTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final UUID OBJETIVO = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Test
    void objetivoIdsAusenteNaoMarcaPresente() {
        AtualizarHabitoRequest request = ler("{\"titulo\":\"Novo\"}");

        assertThat(request.objetivoIdsPresente()).isFalse();
        assertThat(request.objetivoIds()).isNull();
    }

    @Test
    void objetivoIdsVazioMarcaPresenteComListaVazia() {
        AtualizarHabitoRequest request = ler("{\"objetivoIds\":[]}");

        assertThat(request.objetivoIdsPresente()).isTrue();
        assertThat(request.objetivoIds()).isEmpty();
    }

    @Test
    void objetivoIdsNuloMarcaPresenteComListaVazia() {
        AtualizarHabitoRequest request = ler("{\"objetivoIds\":null}");

        assertThat(request.objetivoIdsPresente()).isTrue();
        assertThat(request.objetivoIds()).isEmpty();
    }

    @Test
    void objetivoIdsComUuidsPreservaOrdem() {
        UUID outro = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        AtualizarHabitoRequest request = ler(
                "{\"objetivoIds\":[\"" + OBJETIVO + "\",\"" + outro + "\"]}");

        assertThat(request.objetivoIdsPresente()).isTrue();
        assertThat(request.objetivoIds()).containsExactly(OBJETIVO, outro);
    }

    private static AtualizarHabitoRequest ler(String json) {
        return JSON.readValue(json, AtualizarHabitoRequest.class);
    }
}
