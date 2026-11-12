package com.controlerotina.tarefa.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;

class TarefaResponseTest {

    @Test
    void deveMapearRaizComHierarquiaVazia() {
        Instant agora = Instant.parse("2026-11-12T12:00:00Z");
        Tarefa raiz = new Tarefa(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Estudar Security",
                "desc",
                Prioridade.MEDIA,
                agora,
                StatusTarefa.PENDENTE,
                60,
                agora,
                agora
        );

        TarefaResponse response = TarefaResponse.from(raiz);

        assertThat(response.parentId()).isNull();
        assertThat(response.parentTitulo()).isNull();
        assertThat(response.temFilhos()).isFalse();
    }

    @Test
    void deveMapearFilhoComTituloDoPai() {
        Instant agora = Instant.parse("2026-11-12T12:00:00Z");
        UUID paiId = UUID.randomUUID();
        Tarefa filho = new Tarefa(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Ler OWASP",
                "desc",
                Prioridade.MEDIA,
                agora,
                StatusTarefa.PENDENTE,
                60,
                agora,
                agora
        );
        filho.definirParentId(paiId);

        TarefaResponse response = TarefaResponse.from(
                filho,
                null,
                60,
                List.of(),
                null,
                "Estudar Security",
                false
        );

        assertThat(response.parentId()).isEqualTo(paiId);
        assertThat(response.parentTitulo()).isEqualTo("Estudar Security");
        assertThat(response.temFilhos()).isFalse();
    }
}
