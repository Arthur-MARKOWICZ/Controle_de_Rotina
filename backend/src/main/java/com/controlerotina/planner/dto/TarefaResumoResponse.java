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
        Instant prazo
) {
    public static TarefaResumoResponse from(Tarefa tarefa) {
        return new TarefaResumoResponse(
                tarefa.getId(),
                tarefa.getTitulo(),
                tarefa.getPrioridade(),
                tarefa.getStatus(),
                tarefa.getDuracaoMinutos(),
                tarefa.getPrazo()
        );
    }
}
