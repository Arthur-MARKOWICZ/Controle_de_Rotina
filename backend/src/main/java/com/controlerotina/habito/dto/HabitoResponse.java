package com.controlerotina.habito.dto;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

import com.controlerotina.capacidade.entity.DiaSemana;
import com.controlerotina.habito.entity.Habito;
import com.controlerotina.habito.entity.StatusHabito;

public record HabitoResponse(
        UUID id,
        String titulo,
        String descricao,
        int duracaoMinutos,
        Set<DiaSemana> diasSemana,
        LocalTime horarioPreferencial,
        StatusHabito status,
        int streakAtual,
        int melhorStreak,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt,
        boolean deletada
) {
    public static HabitoResponse from(Habito habito) {
        return new HabitoResponse(
                habito.getId(),
                habito.getTitulo(),
                habito.getDescricao(),
                habito.getDuracaoMinutos(),
                habito.getDiasSemana(),
                habito.getHorarioPreferencial(),
                habito.getStatus(),
                habito.getStreakAtual(),
                habito.getMelhorStreak(),
                habito.getCreatedAt(),
                habito.getUpdatedAt(),
                habito.getDeletedAt(),
                habito.isDeletado()
        );
    }
}
