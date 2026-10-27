package com.controlerotina.execucao.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.controlerotina.planner.entity.Plano;

public record PlanoAprovadoResumoResponse(
        UUID id,
        LocalDate data,
        int versao
) {
    public static PlanoAprovadoResumoResponse from(Plano plano) {
        return new PlanoAprovadoResumoResponse(
                plano.getId(),
                plano.getData(),
                plano.getVersao()
        );
    }
}
