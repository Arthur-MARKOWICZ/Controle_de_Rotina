package com.controlerotina.decidir.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.controlerotina.decidir.entity.ItemDecidir;
import com.controlerotina.tag.dto.TagResumoResponse;

public record DecidirResponse(
        UUID id,
        String titulo,
        String descricao,
        Instant decidirAte,
        UUID projetoId,
        UUID tarefaId,
        List<TagResumoResponse> tags,
        Integer tagMatchScore,
        Instant createdAt,
        Instant updatedAt
) {
    public static DecidirResponse from(ItemDecidir item) {
        return from(item, List.of(), null);
    }

    public static DecidirResponse from(
            ItemDecidir item,
            List<TagResumoResponse> tags,
            Integer tagMatchScore
    ) {
        List<TagResumoResponse> tagsSafe = tags == null ? List.of() : List.copyOf(tags);
        return new DecidirResponse(
                item.getId(),
                item.getTitulo(),
                item.getDescricao(),
                item.getDecidirAte(),
                item.getProjetoId(),
                item.getTarefaId(),
                tagsSafe,
                tagMatchScore,
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}
