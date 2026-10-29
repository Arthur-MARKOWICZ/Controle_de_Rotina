package com.controlerotina.planner.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.controlerotina.planner.entity.Plano;
import com.controlerotina.planner.entity.StatusPlano;

public record PlanoResponse(
        UUID id,
        LocalDate data,
        int versao,
        StatusPlano status,
        boolean temConflito,
        String mensagem,
        UUID propostaId,
        boolean propostaPendente,
        Instant geradoEm,
        Instant createdAt,
        Instant updatedAt,
        List<BlocoPlanoResponse> blocos
) {
    public static PlanoResponse from(Plano plano, List<BlocoPlanoResponse> blocos, boolean propostaPendente) {
        return new PlanoResponse(
                plano.getId(),
                plano.getData(),
                plano.getVersao(),
                plano.getStatus(),
                plano.isTemConflito(),
                plano.getMensagem(),
                plano.getPropostaId(),
                propostaPendente,
                plano.getGeradoEm(),
                plano.getCreatedAt(),
                plano.getUpdatedAt(),
                blocos
        );
    }
}
