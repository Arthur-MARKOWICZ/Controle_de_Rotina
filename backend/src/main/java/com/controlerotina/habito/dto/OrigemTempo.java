package com.controlerotina.habito.dto;

/** De onde vieram os minutos dedicados de um dia do histórico do hábito. */
public enum OrigemTempo {
    /** Todas as execuções do dia foram pelo timer (fim − início). */
    TIMER,
    /** Todas as execuções do dia tiveram minutos informados no concluir-direto. */
    INFORMADO,
    /** Mistura de timer e minutos informados no mesmo dia. */
    MISTO
}
