package com.controlerotina.usuario.dto;

import jakarta.validation.constraints.NotBlank;

public record ExcluirContaRequest(
        @NotBlank
        String senhaAtual
) {
}
