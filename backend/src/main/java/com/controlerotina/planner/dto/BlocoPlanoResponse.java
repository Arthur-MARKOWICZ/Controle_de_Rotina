package com.controlerotina.planner.dto;

import java.util.UUID;

import com.controlerotina.planner.entity.BlocoPlano;
import com.controlerotina.tarefa.entity.Tarefa;

public record BlocoPlanoResponse(
        UUID id,
        int duracaoMinutos,
        int ordem,
        TarefaResumoResponse tarefa
) {
    public static BlocoPlanoResponse from(BlocoPlano bloco, Tarefa tarefa) {
        return new BlocoPlanoResponse(
                bloco.getId(),
                bloco.getDuracaoMinutos(),
                bloco.getOrdem(),
                TarefaResumoResponse.from(tarefa)
        );
    }
}
