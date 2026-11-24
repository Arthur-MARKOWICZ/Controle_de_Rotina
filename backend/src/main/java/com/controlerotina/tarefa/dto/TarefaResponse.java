package com.controlerotina.tarefa.dto;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.controlerotina.objetivo.dto.ObjetivoResumoResponse;
import com.controlerotina.pendencia.dto.EsperaResumo;
import com.controlerotina.tag.dto.TagResumoResponse;
import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;

public record TarefaResponse(
        UUID id,
        String titulo,
        String descricao,
        Prioridade prioridade,
        @Nullable Instant prazo,
        StatusTarefa status,
        int duracaoMinutos,
        int restanteMinutos,
        int ordem,
        @Nullable UUID projetoId,
        @Nullable String projetoNome,
        List<TagResumoResponse> tags,
        @Nullable Integer tagMatchScore,
        Instant createdAt,
        Instant updatedAt,
        @Nullable Instant deletedAt,
        boolean deletada,
        @Nullable UUID parentId,
        @Nullable String parentTitulo,
        boolean temFilhos,
        List<EsperaResumo> esperas,
        boolean bloqueada,
        List<ObjetivoResumoResponse> objetivos
) {
    public static TarefaResponse from(Tarefa tarefa) {
        return from(tarefa, null, tarefa.getDuracaoMinutos(), List.of(), null);
    }

    public static TarefaResponse from(Tarefa tarefa, String projetoNome) {
        return from(tarefa, projetoNome, tarefa.getDuracaoMinutos(), List.of(), null);
    }

    public static TarefaResponse from(Tarefa tarefa, String projetoNome, int restanteMinutos) {
        return from(tarefa, projetoNome, restanteMinutos, List.of(), null);
    }

    public static TarefaResponse from(
            Tarefa tarefa,
            String projetoNome,
            int restanteMinutos,
            List<TagResumoResponse> tags,
            Integer tagMatchScore
    ) {
        return from(tarefa, projetoNome, restanteMinutos, tags, tagMatchScore, null, false);
    }

    public static TarefaResponse from(
            Tarefa tarefa,
            String projetoNome,
            int restanteMinutos,
            List<TagResumoResponse> tags,
            Integer tagMatchScore,
            String parentTitulo,
            boolean temFilhos
    ) {
        return from(
                tarefa,
                projetoNome,
                restanteMinutos,
                tags,
                tagMatchScore,
                parentTitulo,
                temFilhos,
                List.of(),
                false
        );
    }

    public static TarefaResponse from(
            Tarefa tarefa,
            String projetoNome,
            int restanteMinutos,
            List<TagResumoResponse> tags,
            Integer tagMatchScore,
            String parentTitulo,
            boolean temFilhos,
            List<EsperaResumo> esperas,
            boolean bloqueada
    ) {
        return from(
                tarefa,
                projetoNome,
                restanteMinutos,
                tags,
                tagMatchScore,
                parentTitulo,
                temFilhos,
                esperas,
                bloqueada,
                List.of()
        );
    }

    public static TarefaResponse from(
            Tarefa tarefa,
            String projetoNome,
            int restanteMinutos,
            List<TagResumoResponse> tags,
            Integer tagMatchScore,
            String parentTitulo,
            boolean temFilhos,
            List<EsperaResumo> esperas,
            boolean bloqueada,
            List<ObjetivoResumoResponse> objetivos
    ) {
        List<TagResumoResponse> tagsSafe = tags == null ? List.of() : List.copyOf(tags);
        List<EsperaResumo> esperasSafe = esperas == null ? List.of() : List.copyOf(esperas);
        List<ObjetivoResumoResponse> objetivosSafe = objetivos == null ? List.of() : List.copyOf(objetivos);
        return new TarefaResponse(
                tarefa.getId(),
                tarefa.getTitulo(),
                tarefa.getDescricao(),
                tarefa.getPrioridade(),
                tarefa.getPrazo(),
                tarefa.getStatus(),
                tarefa.getDuracaoMinutos(),
                Math.max(0, restanteMinutos),
                tarefa.getOrdem(),
                tarefa.getProjetoId(),
                projetoNome,
                tagsSafe,
                tagMatchScore,
                tarefa.getCreatedAt(),
                tarefa.getUpdatedAt(),
                tarefa.getDeletedAt(),
                tarefa.getDeletedAt() != null,
                tarefa.getParentId(),
                parentTitulo,
                temFilhos,
                esperasSafe,
                bloqueada,
                objetivosSafe
        );
    }
}
