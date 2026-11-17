package com.controlerotina.execucao.dto;

import org.jspecify.annotations.Nullable;

import com.controlerotina.planner.dto.HabitoResumoResponse;
import com.controlerotina.planner.dto.TarefaResumoResponse;

public record ItemExecucaoResponse(
        BlocoExecucaoResponse bloco,
        @Nullable TarefaResumoResponse tarefaResumo,
        @Nullable HabitoResumoResponse habitoResumo,
        ExecucaoResponse execucao
) {
    public ItemExecucaoResponse(
            BlocoExecucaoResponse bloco,
            TarefaResumoResponse tarefaResumo,
            ExecucaoResponse execucao
    ) {
        this(bloco, tarefaResumo, null, execucao);
    }
}
