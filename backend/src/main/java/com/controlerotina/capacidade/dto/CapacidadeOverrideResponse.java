package com.controlerotina.capacidade.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.controlerotina.capacidade.entity.CapacidadeOverride;

public record CapacidadeOverrideResponse(
        UUID id,
        LocalDate data,
        int horas,
        boolean ativo,
        Instant createdAt,
        Instant updatedAt
) {
    public static CapacidadeOverrideResponse from(CapacidadeOverride override) {
        return new CapacidadeOverrideResponse(
                override.getId(),
                override.getData(),
                override.getHoras(),
                override.isAtivo(),
                override.getCreatedAt(),
                override.getUpdatedAt()
        );
    }
}
