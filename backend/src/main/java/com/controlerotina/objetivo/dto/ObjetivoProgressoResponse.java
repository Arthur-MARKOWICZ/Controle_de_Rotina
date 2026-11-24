package com.controlerotina.objetivo.dto;

public record ObjetivoProgressoResponse(
        int percentual,
        int minutosConcluidos,
        int minutosTotais
) {
}
