package com.controlerotina.projeto.dto;

import java.time.Instant;
import java.util.UUID;

import com.controlerotina.projeto.entity.Projeto;
import com.controlerotina.projeto.entity.StatusProjeto;

public record ProjetoResponse(
        UUID id,
        String nome,
        String descricao,
        StatusProjeto status,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProjetoResponse from(Projeto projeto) {
        return new ProjetoResponse(
                projeto.getId(),
                projeto.getNome(),
                projeto.getDescricao(),
                projeto.getStatus(),
                projeto.getCreatedAt(),
                projeto.getUpdatedAt()
        );
    }
}
