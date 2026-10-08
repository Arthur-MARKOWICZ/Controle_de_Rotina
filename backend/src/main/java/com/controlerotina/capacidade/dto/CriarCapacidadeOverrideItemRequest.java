package com.controlerotina.capacidade.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CriarCapacidadeOverrideItemRequest(
        @NotNull
        LocalDate data,

        @NotNull
        @Min(0)
        Integer horas
) {
}
