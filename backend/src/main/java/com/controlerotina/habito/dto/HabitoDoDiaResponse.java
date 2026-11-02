package com.controlerotina.habito.dto;

import java.time.LocalTime;
import java.util.UUID;

public record HabitoDoDiaResponse(
        UUID id,
        String titulo,
        int duracaoMinutos,
        LocalTime horarioPreferencial,
        int streakAtual,
        boolean cumprido,
        boolean noPlanoAprovado,
        SlotExecucaoHabitoResponse execucaoAvulsa,
        SlotExecucaoHabitoResponse execucaoNoPlano
) {
}
