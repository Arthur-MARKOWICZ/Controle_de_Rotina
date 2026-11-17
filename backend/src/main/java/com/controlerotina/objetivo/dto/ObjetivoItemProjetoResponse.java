package com.controlerotina.objetivo.dto;

import java.util.UUID;

import com.controlerotina.projeto.entity.StatusProjeto;

public record ObjetivoItemProjetoResponse(
        UUID id,
        String nome,
        StatusProjeto status,
        OrigemVinculoObjetivo origem
) {
}
