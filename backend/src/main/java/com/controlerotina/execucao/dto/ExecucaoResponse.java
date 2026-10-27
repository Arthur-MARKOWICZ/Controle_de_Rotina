package com.controlerotina.execucao.dto;

import java.time.Instant;
import java.util.UUID;

import com.controlerotina.execucao.entity.Execucao;
import com.controlerotina.execucao.entity.StatusExecucao;

public record ExecucaoResponse(
        UUID id,
        UUID blocoPlanoId,
        StatusExecucao status,
        Instant iniciadoEm,
        Instant concluidoEm,
        Instant abortadoEm
) {
    public static ExecucaoResponse from(Execucao execucao) {
        return new ExecucaoResponse(
                execucao.getId(),
                execucao.getBlocoPlanoId(),
                execucao.getStatus(),
                execucao.getIniciadoEm(),
                execucao.getConcluidoEm(),
                execucao.getAbortadoEm()
        );
    }

    public static ExecucaoResponse efetiva(Execucao execucao) {
        if (execucao == null) {
            return new ExecucaoResponse(
                    null,
                    null,
                    StatusExecucao.NAO_INICIADO,
                    null,
                    null,
                    null
            );
        }
        return from(execucao);
    }
}
