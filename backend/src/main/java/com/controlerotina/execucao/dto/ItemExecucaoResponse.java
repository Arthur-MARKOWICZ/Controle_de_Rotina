package com.controlerotina.execucao.dto;

import com.controlerotina.planner.dto.HabitoResumoResponse;
import com.controlerotina.planner.dto.TarefaResumoResponse;

public record ItemExecucaoResponse(
        BlocoExecucaoResponse bloco,
        TarefaResumoResponse tarefaResumo,
        HabitoResumoResponse habitoResumo,
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
