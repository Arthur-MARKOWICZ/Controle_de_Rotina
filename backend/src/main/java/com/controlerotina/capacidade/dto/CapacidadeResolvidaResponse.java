package com.controlerotina.capacidade.dto;

import java.time.LocalDate;

import com.controlerotina.capacidade.entity.OrigemCapacidade;

public record CapacidadeResolvidaResponse(
        LocalDate data,
        Integer horas,
        OrigemCapacidade origem
) {
}
