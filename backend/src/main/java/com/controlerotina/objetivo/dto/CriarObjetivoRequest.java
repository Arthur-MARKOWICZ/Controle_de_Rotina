package com.controlerotina.objetivo.dto;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CriarObjetivoRequest(
        @NotBlank
        @Size(max = 80)
        String titulo,

        @Size(max = 500)
        @Nullable
        String descricao,

        @Nullable
        LocalDate prazo
) {
    public CriarObjetivoRequest {
        if (titulo != null) {
            titulo = titulo.trim();
        }
        if (descricao != null) {
            descricao = descricao.isBlank() ? null : descricao.trim();
        }
    }
}
