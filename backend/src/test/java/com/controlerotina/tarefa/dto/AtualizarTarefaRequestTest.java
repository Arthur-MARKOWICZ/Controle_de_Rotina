package com.controlerotina.tarefa.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class AtualizarTarefaRequestTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final UUID OBJETIVO = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Test
    void objetivoIdsAusenteNaoMarcaPresente() {
        AtualizarTarefaRequest request = ler("{\"titulo\":\"Novo\"}");

        assertThat(request.objetivoIdsPresente()).isFalse();
        assertThat(request.objetivoIds()).isNull();
    }

    @Test
    void objetivoIdsVazioMarcaPresenteComListaVazia() {
        AtualizarTarefaRequest request = ler("{\"objetivoIds\":[]}");

        assertThat(request.objetivoIdsPresente()).isTrue();
        assertThat(request.objetivoIds()).isEmpty();
    }

    @Test
    void objetivoIdsNuloMarcaPresenteComListaVazia() {
        AtualizarTarefaRequest request = ler("{\"objetivoIds\":null}");

        assertThat(request.objetivoIdsPresente()).isTrue();
        assertThat(request.objetivoIds()).isEmpty();
    }

    @Test
    void objetivoIdsComUuidsPreservaOrdem() {
        UUID outro = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        AtualizarTarefaRequest request = ler(
                "{\"objetivoIds\":[\"" + OBJETIVO + "\",\"" + outro + "\"]}");

        assertThat(request.objetivoIdsPresente()).isTrue();
        assertThat(request.objetivoIds()).containsExactly(OBJETIVO, outro);
    }

    private static AtualizarTarefaRequest ler(String json) {
        return JSON.readValue(json, AtualizarTarefaRequest.class);
    }
}
