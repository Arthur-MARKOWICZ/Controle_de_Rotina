package com.controlerotina.capacidade.dto;

import com.controlerotina.capacidade.entity.DiaSemana;

import jakarta.validation.constraints.Min;

public record AtualizarCapacidadeSemanalRequest(
        DiaSemana diaSemana,

        @Min(0)
        Integer horas
) {
}
