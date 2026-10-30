package com.controlerotina.tarefa.dto;

import java.time.Instant;
import java.util.UUID;

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
        UUID projetoId,
        String projetoNome,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt,
        boolean deletada
) {
    public static TarefaResponse from(Tarefa tarefa) {
        return from(tarefa, null);
    }

    public static TarefaResponse from(Tarefa tarefa, String projetoNome) {
        return new TarefaResponse(
                tarefa.getId(),
                tarefa.getTitulo(),
                tarefa.getDescricao(),
                tarefa.getPrioridade(),
                tarefa.getPrazo(),
                tarefa.getStatus(),
                tarefa.getDuracaoMinutos(),
                tarefa.getProjetoId(),
                projetoNome,
                tarefa.getCreatedAt(),
                tarefa.getUpdatedAt(),
                tarefa.getDeletedAt(),
                tarefa.getDeletedAt() != null
        );
    }
}
