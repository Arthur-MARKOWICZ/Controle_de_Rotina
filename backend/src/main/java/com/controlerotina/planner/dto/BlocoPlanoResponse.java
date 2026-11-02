package com.controlerotina.planner.dto;

import java.util.UUID;

import com.controlerotina.habito.entity.Habito;
import com.controlerotina.planner.entity.BlocoPlano;
import com.controlerotina.tarefa.entity.Tarefa;

public record BlocoPlanoResponse(
        UUID id,
        int duracaoMinutos,
        int ordem,
        OrigemBloco origem,
        UUID tarefaId,
        UUID habitoId,
        TarefaResumoResponse tarefa,
        HabitoResumoResponse habito
) {
    public static BlocoPlanoResponse from(BlocoPlano bloco, Tarefa tarefa) {
        return from(bloco, tarefa, null);
    }

    public static BlocoPlanoResponse from(BlocoPlano bloco, Tarefa tarefa, String projetoNome) {
        return new BlocoPlanoResponse(
                bloco.getId(),
                bloco.getDuracaoMinutos(),
                bloco.getOrdem(),
                OrigemBloco.TAREFA,
                bloco.getTarefaId(),
                null,
                TarefaResumoResponse.from(tarefa, projetoNome),
                null
        );
    }

    public static BlocoPlanoResponse fromHabito(BlocoPlano bloco, Habito habito) {
        return new BlocoPlanoResponse(
                bloco.getId(),
                bloco.getDuracaoMinutos(),
                bloco.getOrdem(),
                OrigemBloco.HABITO,
                null,
                bloco.getHabitoId(),
                null,
                HabitoResumoResponse.from(habito)
        );
    }
}
