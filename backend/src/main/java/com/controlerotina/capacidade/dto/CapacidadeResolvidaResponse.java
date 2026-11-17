package com.controlerotina.capacidade.dto;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

import com.controlerotina.capacidade.entity.OrigemCapacidade;

public record CapacidadeResolvidaResponse(
        LocalDate data,
        @Nullable Integer horas,
        OrigemCapacidade origem
) {
}
