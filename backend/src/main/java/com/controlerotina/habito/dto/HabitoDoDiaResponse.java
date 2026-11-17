package com.controlerotina.habito.dto;

import org.jspecify.annotations.Nullable;

import java.time.LocalTime;
import java.util.UUID;

public record HabitoDoDiaResponse(
        UUID id,
        String titulo,
        int duracaoMinutos,
        @Nullable LocalTime horarioPreferencial,
        int streakAtual,
        boolean cumprido,
        boolean noPlanoAprovado,
        @Nullable SlotExecucaoHabitoResponse execucaoAvulsa,
        @Nullable SlotExecucaoHabitoResponse execucaoNoPlano
) {
}
