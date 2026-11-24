package com.controlerotina.projeto.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class AtualizarProjetoRequestTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final UUID OBJETIVO = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Test
    void objetivoIdsAusenteNaoMarcaPresente() {
        AtualizarProjetoRequest request = ler("{\"nome\":\"Novo\"}");

        assertThat(request.objetivoIdsPresente()).isFalse();
        assertThat(request.objetivoIds()).isNull();
    }

    @Test
    void objetivoIdsVazioMarcaPresenteComListaVazia() {
        AtualizarProjetoRequest request = ler("{\"objetivoIds\":[]}");

        assertThat(request.objetivoIdsPresente()).isTrue();
        assertThat(request.objetivoIds()).isEmpty();
    }

    @Test
    void objetivoIdsNuloMarcaPresenteComListaVazia() {
        AtualizarProjetoRequest request = ler("{\"objetivoIds\":null}");

        assertThat(request.objetivoIdsPresente()).isTrue();
        assertThat(request.objetivoIds()).isEmpty();
    }

    @Test
    void objetivoIdsComUuidsPreservaOrdem() {
        UUID outro = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        AtualizarProjetoRequest request = ler(
                "{\"objetivoIds\":[\"" + OBJETIVO + "\",\"" + outro + "\"]}");

        assertThat(request.objetivoIdsPresente()).isTrue();
        assertThat(request.objetivoIds()).containsExactly(OBJETIVO, outro);
    }

    private static AtualizarProjetoRequest ler(String json) {
        return JSON.readValue(json, AtualizarProjetoRequest.class);
    }
}
