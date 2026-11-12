package com.controlerotina.pendencia.dto;

import java.util.UUID;

import com.controlerotina.pendencia.entity.Espera;
import com.controlerotina.pendencia.entity.TipoRecursoEspera;

public record EsperaResumo(
        UUID id,
        TipoRecursoEspera alvoTipo,
        UUID alvoId,
        String alvoTitulo,
        boolean resolvida
) {
    public static EsperaResumo from(Espera espera, String alvoTitulo, boolean resolvida) {
        return new EsperaResumo(
                espera.getId(),
                espera.getAlvoTipo(),
                espera.getAlvoId(),
                alvoTitulo,
                resolvida
        );
    }
}
