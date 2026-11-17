package com.controlerotina.objetivo.dto;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.controlerotina.objetivo.entity.Objetivo;
import com.controlerotina.objetivo.entity.StatusObjetivo;

public record ObjetivoResponse(
        UUID id,
        String titulo,
        @Nullable String descricao,
        @Nullable LocalDate prazo,
        StatusObjetivo status,
        @Nullable Instant concluidoEm,
        @Nullable Instant arquivadoEm,
        Instant createdAt,
        Instant updatedAt
) {
    public static ObjetivoResponse from(Objetivo objetivo) {
        return new ObjetivoResponse(
                objetivo.getId(),
                objetivo.getTitulo(),
                objetivo.getDescricao(),
                objetivo.getPrazo(),
                objetivo.getStatus(),
                objetivo.getConcluidoEm(),
                objetivo.getArquivadoEm(),
                objetivo.getCreatedAt(),
                objetivo.getUpdatedAt()
        );
    }
}
