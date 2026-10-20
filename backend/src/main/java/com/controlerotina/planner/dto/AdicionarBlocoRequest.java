package com.controlerotina.planner.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record AdicionarBlocoRequest(
        @NotNull
        UUID tarefaId
) {
}
