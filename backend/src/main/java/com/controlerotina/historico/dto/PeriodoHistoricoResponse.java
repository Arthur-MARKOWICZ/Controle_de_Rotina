package com.controlerotina.historico.dto;

import java.time.LocalDate;

public record PeriodoHistoricoResponse(
        String escopo,
        LocalDate inicio,
        LocalDate fim
) {
}
