package com.controlerotina.capacidade.entity;

import java.time.LocalDate;

public enum DiaSemana {
    SEGUNDA,
    TERCA,
    QUARTA,
    QUINTA,
    SEXTA,
    SABADO,
    DOMINGO;

    public static DiaSemana de(LocalDate data) {
        return values()[data.getDayOfWeek().getValue() - 1];
    }
}
