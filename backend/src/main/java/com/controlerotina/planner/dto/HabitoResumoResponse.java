package com.controlerotina.planner.dto;

import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

import com.controlerotina.capacidade.entity.DiaSemana;
import com.controlerotina.habito.entity.Habito;

public record HabitoResumoResponse(
        UUID id,
        String titulo,
        LocalTime horarioPreferencial,
        int streakAtual,
        Set<DiaSemana> diasSemana
) {
    public static HabitoResumoResponse from(Habito habito) {
        return new HabitoResumoResponse(
                habito.getId(),
                habito.getTitulo(),
                habito.getHorarioPreferencial(),
                habito.getStreakAtual(),
                habito.getDiasSemana()
        );
    }
}
