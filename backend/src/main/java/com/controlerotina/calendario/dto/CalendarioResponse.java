package com.controlerotina.calendario.dto;

import java.util.List;

public record CalendarioResponse(
        PeriodoCalendarioResponse periodo,
        List<CalendarioDiaResponse> dias
) {
}
