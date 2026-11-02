package com.controlerotina.habito.dto;

import java.time.Instant;
import java.util.UUID;

import com.controlerotina.execucao.entity.Execucao;
import com.controlerotina.execucao.entity.StatusExecucao;
import com.controlerotina.planner.entity.BlocoPlano;

public record SlotExecucaoHabitoResponse(
        UUID blocoId,
        UUID execucaoId,
        StatusExecucao status,
        Instant iniciadoEm
) {
    public static SlotExecucaoHabitoResponse de(BlocoPlano bloco, Execucao execucao) {
        if (execucao == null) {
            return new SlotExecucaoHabitoResponse(
                    bloco.getId(),
                    null,
                    StatusExecucao.NAO_INICIADO,
                    null
            );
        }
        return new SlotExecucaoHabitoResponse(
                bloco.getId(),
                execucao.getId(),
                execucao.getStatus(),
                execucao.getIniciadoEm()
        );
    }
}
