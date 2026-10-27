package com.controlerotina.execucao.dto;

import java.util.UUID;

import com.controlerotina.planner.entity.BlocoPlano;

public record BlocoExecucaoResponse(
        UUID id,
        int duracaoMinutos,
        int ordem
) {
    public static BlocoExecucaoResponse from(BlocoPlano bloco) {
        return new BlocoExecucaoResponse(
                bloco.getId(),
                bloco.getDuracaoMinutos(),
                bloco.getOrdem()
        );
    }
}
