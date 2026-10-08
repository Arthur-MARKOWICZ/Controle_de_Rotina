package com.controlerotina.capacidade.dto;

import com.controlerotina.capacidade.entity.DiaSemana;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CriarCapacidadeSemanalItemRequest(
        @NotNull
        DiaSemana diaSemana,

        @NotNull
        @Min(0)
        Integer horas
) {
}
