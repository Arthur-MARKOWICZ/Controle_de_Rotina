package com.controlerotina.execucao.dto;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

import com.controlerotina.execucao.entity.Execucao;
import com.controlerotina.execucao.entity.StatusExecucao;
import com.controlerotina.xp.dto.XpConclusaoResponse;

public record ExecucaoResponse(
        @Nullable UUID id,
        @Nullable UUID blocoPlanoId,
        StatusExecucao status,
        @Nullable Instant iniciadoEm,
        @Nullable Instant concluidoEm,
        @Nullable Instant abortadoEm,
        @Nullable XpConclusaoResponse xp
) {
    public static ExecucaoResponse from(Execucao execucao) {
        return from(execucao, null);
    }

    public static ExecucaoResponse from(Execucao execucao, XpConclusaoResponse xp) {
        return new ExecucaoResponse(
                execucao.getId(),
                execucao.getBlocoPlanoId(),
                execucao.getStatus(),
                execucao.getIniciadoEm(),
                execucao.getConcluidoEm(),
                execucao.getAbortadoEm(),
                xp
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
                    null,
                    null
            );
        }
        return from(execucao);
    }
}
