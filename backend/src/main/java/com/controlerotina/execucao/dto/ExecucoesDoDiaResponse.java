package com.controlerotina.execucao.dto;

import org.jspecify.annotations.Nullable;

import java.util.List;

public record ExecucoesDoDiaResponse(
        @Nullable PlanoAprovadoResumoResponse planoAprovado,
        List<ItemExecucaoResponse> itens
) {
}
