package com.controlerotina.tag.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AtualizarTagRequest(
        @Size(min = 1, max = 64)
        String nome,

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$")
        String cor
) {
    public AtualizarTagRequest {
        if (nome != null) {
            nome = nome.trim();
        }
    }
}
