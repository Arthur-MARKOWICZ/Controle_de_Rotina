package com.controlerotina.execucao.dto;

import java.util.List;

public record ExecucoesDoDiaResponse(
        PlanoAprovadoResumoResponse planoAprovado,
        List<ItemExecucaoResponse> itens
) {
}
