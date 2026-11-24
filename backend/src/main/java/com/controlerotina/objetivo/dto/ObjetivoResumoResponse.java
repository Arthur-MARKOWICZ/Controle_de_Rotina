package com.controlerotina.objetivo.dto;

import java.util.UUID;

import com.controlerotina.objetivo.entity.Objetivo;
import com.controlerotina.objetivo.entity.StatusObjetivo;

public record ObjetivoResumoResponse(
        UUID id,
        String titulo,
        StatusObjetivo status
) {
    public static ObjetivoResumoResponse from(Objetivo objetivo) {
        return new ObjetivoResumoResponse(objetivo.getId(), objetivo.getTitulo(), objetivo.getStatus());
    }
}
