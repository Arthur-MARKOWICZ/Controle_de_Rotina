package com.controlerotina.projeto.dto;

import jakarta.validation.constraints.Size;

public record AtualizarProjetoRequest(
        @Size(min = 1, max = 80)
        String nome,

        @Size(max = 500)
        String descricao
) {
    public AtualizarProjetoRequest {
        if (nome != null) {
            nome = nome.trim();
        }
        if (descricao != null) {
            descricao = descricao.trim();
        }
    }
}
