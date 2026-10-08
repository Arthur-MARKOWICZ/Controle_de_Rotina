package com.controlerotina.capacidade.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Min;

public record AtualizarCapacidadeOverrideRequest(
        LocalDate data,

        @Min(0)
        Integer horas
) {
}
