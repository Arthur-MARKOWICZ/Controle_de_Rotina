package com.controlerotina.projeto.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.controlerotina.pendencia.dto.EsperaResumo;
import com.controlerotina.projeto.entity.Projeto;
import com.controlerotina.projeto.entity.StatusProjeto;
import com.controlerotina.tag.dto.TagResumoResponse;

public record ProjetoResponse(
        UUID id,
        String nome,
        String descricao,
        StatusProjeto status,
        int ordem,
        List<TagResumoResponse> tags,
        Integer tagMatchScore,
        Instant createdAt,
        Instant updatedAt,
        List<EsperaResumo> esperas,
        boolean bloqueada
) {
    public static ProjetoResponse from(Projeto projeto) {
        return from(projeto, List.of(), null);
    }

    public static ProjetoResponse from(
            Projeto projeto,
            List<TagResumoResponse> tags,
            Integer tagMatchScore
    ) {
        return from(projeto, tags, tagMatchScore, List.of(), false);
    }

    public static ProjetoResponse from(
            Projeto projeto,
            List<TagResumoResponse> tags,
            Integer tagMatchScore,
            List<EsperaResumo> esperas,
            boolean bloqueada
    ) {
        List<TagResumoResponse> tagsSafe = tags == null ? List.of() : List.copyOf(tags);
        List<EsperaResumo> esperasSafe = esperas == null ? List.of() : List.copyOf(esperas);
        return new ProjetoResponse(
                projeto.getId(),
                projeto.getNome(),
                projeto.getDescricao(),
                projeto.getStatus(),
                projeto.getOrdem(),
                tagsSafe,
                tagMatchScore,
                projeto.getCreatedAt(),
                projeto.getUpdatedAt(),
                esperasSafe,
                bloqueada
        );
    }
}
