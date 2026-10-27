package com.controlerotina.execucao.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record IniciarExecucaoRequest(
        @NotNull
        UUID blocoId
) {
}
