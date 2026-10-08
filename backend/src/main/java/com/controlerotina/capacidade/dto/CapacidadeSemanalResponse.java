package com.controlerotina.capacidade.dto;

import java.time.Instant;
import java.util.UUID;

import com.controlerotina.capacidade.entity.CapacidadeSemanal;
import com.controlerotina.capacidade.entity.DiaSemana;

public record CapacidadeSemanalResponse(
        UUID id,
        DiaSemana diaSemana,
        int horas,
        boolean ativo,
        Instant createdAt,
        Instant updatedAt
) {
    public static CapacidadeSemanalResponse from(CapacidadeSemanal capacidade) {
        return new CapacidadeSemanalResponse(
                capacidade.getId(),
                capacidade.getDiaSemana(),
                capacidade.getHoras(),
                capacidade.isAtivo(),
                capacidade.getCreatedAt(),
                capacidade.getUpdatedAt()
        );
    }
}
