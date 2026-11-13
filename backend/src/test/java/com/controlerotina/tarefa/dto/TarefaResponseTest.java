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
        assertThat(response.ordem()).isEqualTo(0);
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

    @Test
    void restanteNegativoDeveVirarZero() {
        Instant agora = Instant.parse("2026-11-12T12:00:00Z");
        Tarefa tarefa = raiz(agora);

        TarefaResponse response = TarefaResponse.from(tarefa, null, -15);

        assertThat(response.restanteMinutos()).isZero();
        assertThat(tarefa.getDuracaoMinutos()).isEqualTo(60);
    }

    @Test
    void tagsNulasDevemVirarListaVazia() {
        Instant agora = Instant.parse("2026-11-12T12:00:00Z");
        Tarefa tarefa = raiz(agora);

        TarefaResponse response = TarefaResponse.from(tarefa, "Projeto", 10, null, null);

        assertThat(response.tags()).isEmpty();
        assertThat(response.projetoNome()).isEqualTo("Projeto");
        assertThat(response.tagMatchScore()).isNull();
    }

    @Test
    void esperasNulasDevemVirarListaVaziaEMarcarBloqueada() {
        Instant agora = Instant.parse("2026-11-12T12:00:00Z");
        Tarefa tarefa = raiz(agora);

        TarefaResponse response = TarefaResponse.from(
                tarefa, null, 60, List.of(), null, null, true, null, true);

        assertThat(response.esperas()).isEmpty();
        assertThat(response.bloqueada()).isTrue();
        assertThat(response.temFilhos()).isTrue();
    }

    @Test
    void deletedAtPreenchidoDeveMarcarDeletada() {
        Instant agora = Instant.parse("2026-11-12T12:00:00Z");
        Tarefa tarefa = raiz(agora);
        tarefa.softDelete(agora);

        TarefaResponse response = TarefaResponse.from(tarefa);

        assertThat(response.deletada()).isTrue();
        assertThat(response.deletedAt()).isEqualTo(agora);
    }

    @Test
    void fromSimplesCopiaCamposDaEntidade() {
        Instant agora = Instant.parse("2026-11-12T12:00:00Z");
        Tarefa tarefa = raiz(agora);

        TarefaResponse response = TarefaResponse.from(tarefa);

        assertThat(response.id()).isEqualTo(tarefa.getId());
        assertThat(response.titulo()).isEqualTo("Estudar Security");
        assertThat(response.duracaoMinutos()).isEqualTo(60);
        assertThat(response.restanteMinutos()).isEqualTo(60);
        assertThat(response.deletada()).isFalse();
        assertThat(response.bloqueada()).isFalse();
    }

    private static Tarefa raiz(Instant agora) {
        return new Tarefa(
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
    }
}
