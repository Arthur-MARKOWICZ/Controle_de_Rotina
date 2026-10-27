package com.controlerotina.execucao.dto;

import com.controlerotina.planner.dto.TarefaResumoResponse;

public record ItemExecucaoResponse(
        BlocoExecucaoResponse bloco,
        TarefaResumoResponse tarefaResumo,
        ExecucaoResponse execucao
) {
}
