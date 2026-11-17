package com.controlerotina.objetivo.dto;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

import com.controlerotina.tarefa.entity.StatusTarefa;

public record ObjetivoItemTarefaResponse(
        UUID id,
        String titulo,
        StatusTarefa status,
        OrigemVinculoObjetivo origem,
        @Nullable UUID projetoId,
        @Nullable String projetoNome
) {
}
