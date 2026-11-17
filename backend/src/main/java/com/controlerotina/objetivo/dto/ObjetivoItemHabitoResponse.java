package com.controlerotina.objetivo.dto;

import java.util.UUID;

import com.controlerotina.habito.entity.StatusHabito;

public record ObjetivoItemHabitoResponse(
        UUID id,
        String titulo,
        StatusHabito status,
        OrigemVinculoObjetivo origem
) {
}
