package com.controlerotina.pendencia.dto;

import java.util.UUID;

import com.controlerotina.pendencia.entity.TipoRecursoEspera;

import jakarta.validation.constraints.NotNull;

public record CriarEsperaRequest(
        @NotNull TipoRecursoEspera alvoTipo,
        @NotNull UUID alvoId
) {
}
