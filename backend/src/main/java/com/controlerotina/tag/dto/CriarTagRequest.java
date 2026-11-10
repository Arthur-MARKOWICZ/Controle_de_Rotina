package com.controlerotina.tag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CriarTagRequest(
        @NotBlank
        @Size(max = 64)
        String nome,

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$")
        String cor
) {
    public static final String COR_DEFAULT = "#6B7280";

    public CriarTagRequest {
        if (nome != null) {
            nome = nome.trim();
        }
        if (cor == null || cor.isBlank()) {
            cor = COR_DEFAULT;
        }
    }
}
