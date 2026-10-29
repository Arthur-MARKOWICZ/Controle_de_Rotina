package com.controlerotina.calendario.dto;

import java.time.LocalDate;

public record PeriodoCalendarioResponse(
        LocalDate inicio,
        LocalDate fim
) {
}
