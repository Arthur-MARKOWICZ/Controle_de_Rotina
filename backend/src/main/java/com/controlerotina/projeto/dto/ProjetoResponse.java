package com.controlerotina.projeto.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.controlerotina.projeto.entity.Projeto;
import com.controlerotina.projeto.entity.StatusProjeto;
import com.controlerotina.tag.dto.TagResumoResponse;

public record ProjetoResponse(
        UUID id,
        String nome,
        String descricao,
        StatusProjeto status,
        List<TagResumoResponse> tags,
        Integer tagMatchScore,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProjetoResponse from(Projeto projeto) {
        return from(projeto, List.of(), null);
    }

    public static ProjetoResponse from(
            Projeto projeto,
            List<TagResumoResponse> tags,
            Integer tagMatchScore
    ) {
        List<TagResumoResponse> tagsSafe = tags == null ? List.of() : List.copyOf(tags);
        return new ProjetoResponse(
                projeto.getId(),
                projeto.getNome(),
                projeto.getDescricao(),
                projeto.getStatus(),
                tagsSafe,
                tagMatchScore,
                projeto.getCreatedAt(),
                projeto.getUpdatedAt()
        );
    }
}
