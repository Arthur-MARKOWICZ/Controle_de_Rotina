package com.controlerotina.decidir.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CriarDecidirRequest(
        @NotBlank
        @Size(max = 120)
        String titulo,

        @Size(max = 2000)
        String descricao,

        Instant decidirAte,

        UUID projetoId,

        List<UUID> tagIds
) {
    public CriarDecidirRequest {
        if (titulo != null) {
            titulo = titulo.trim();
        }
        if (descricao != null && descricao.isBlank()) {
            descricao = null;
        } else if (descricao != null) {
            descricao = descricao.trim();
        }
    }
}
