package com.controlerotina.habito.dto;

import java.time.LocalDate;

public record HabitoHistoricoDiaResponse(
        LocalDate data,
        int minutosDedicados,
        int metaMinutos,
        boolean suficiente,
        OrigemTempo origem
) {
}
