package com.controlerotina.tarefa.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.controlerotina.tag.dto.TagResumoResponse;
import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;

public record TarefaResponse(
        UUID id,
        String titulo,
        String descricao,
        Prioridade prioridade,
        Instant prazo,
        StatusTarefa status,
        int duracaoMinutos,
        int restanteMinutos,
        UUID projetoId,
        String projetoNome,
        List<TagResumoResponse> tags,
        Integer tagMatchScore,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt,
        boolean deletada
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
        List<TagResumoResponse> tagsSafe = tags == null ? List.of() : List.copyOf(tags);
        return new TarefaResponse(
                tarefa.getId(),
                tarefa.getTitulo(),
                tarefa.getDescricao(),
                tarefa.getPrioridade(),
                tarefa.getPrazo(),
                tarefa.getStatus(),
                tarefa.getDuracaoMinutos(),
                Math.max(0, restanteMinutos),
                tarefa.getProjetoId(),
                projetoNome,
                tagsSafe,
                tagMatchScore,
                tarefa.getCreatedAt(),
                tarefa.getUpdatedAt(),
                tarefa.getDeletedAt(),
                tarefa.getDeletedAt() != null
        );
    }
}
