package com.controlerotina.projeto.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CriarProjetoRequest(
        @NotBlank
        @Size(max = 80)
        String nome,

        @Size(max = 500)
        String descricao,

        List<UUID> tagIds
) {
    public CriarProjetoRequest {
        if (nome != null) {
            nome = nome.trim();
        }
        if (descricao != null) {
            descricao = descricao.isBlank() ? null : descricao.trim();
        }
    }
}
