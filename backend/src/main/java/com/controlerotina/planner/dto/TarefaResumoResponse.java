package com.controlerotina.planner.dto;

import java.time.Instant;
import java.util.UUID;

import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;

public record TarefaResumoResponse(
        UUID id,
        String titulo,
        Prioridade prioridade,
        StatusTarefa status,
        int duracaoMinutos,
        Instant prazo,
        UUID projetoId,
        String projetoNome
) {
    public static TarefaResumoResponse from(Tarefa tarefa) {
        return from(tarefa, null);
    }

    public static TarefaResumoResponse from(Tarefa tarefa, String projetoNome) {
        return new TarefaResumoResponse(
                tarefa.getId(),
                tarefa.getTitulo(),
                tarefa.getPrioridade(),
                tarefa.getStatus(),
                tarefa.getDuracaoMinutos(),
                tarefa.getPrazo(),
                tarefa.getProjetoId(),
                projetoNome
        );
    }
}
