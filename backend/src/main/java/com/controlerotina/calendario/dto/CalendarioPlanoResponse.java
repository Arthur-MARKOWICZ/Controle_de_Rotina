package com.controlerotina.calendario.dto;

import java.util.List;
import java.util.UUID;

import com.controlerotina.calendario.CalendarioAgregador;

public record CalendarioPlanoResponse(
        UUID id,
        int versao,
        boolean temConflito,
        List<CalendarioBlocoResponse> blocos
) {
    public static CalendarioPlanoResponse from(CalendarioAgregador.PlanoMontado plano) {
        return new CalendarioPlanoResponse(
                plano.id(),
                plano.versao(),
                plano.temConflito(),
                plano.blocos().stream().map(CalendarioBlocoResponse::from).toList()
        );
    }
}
