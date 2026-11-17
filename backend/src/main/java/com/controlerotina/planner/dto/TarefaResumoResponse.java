package com.controlerotina.planner.dto;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.controlerotina.pendencia.dto.EsperaResumo;
import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;

public record TarefaResumoResponse(
        UUID id,
        String titulo,
        Prioridade prioridade,
        StatusTarefa status,
        int duracaoMinutos,
        @Nullable Instant prazo,
        @Nullable UUID projetoId,
        @Nullable String projetoNome,
        List<EsperaResumo> esperas,
        boolean bloqueada
) {
    public static TarefaResumoResponse from(Tarefa tarefa) {
        return from(tarefa, null);
    }

    public static TarefaResumoResponse from(Tarefa tarefa, String projetoNome) {
        return from(tarefa, projetoNome, List.of(), false);
    }

    public static TarefaResumoResponse from(
            Tarefa tarefa,
            String projetoNome,
            List<EsperaResumo> esperas,
            boolean bloqueada
    ) {
        return new TarefaResumoResponse(
                tarefa.getId(),
                tarefa.getTitulo(),
                tarefa.getPrioridade(),
                tarefa.getStatus(),
                tarefa.getDuracaoMinutos(),
                tarefa.getPrazo(),
                tarefa.getProjetoId(),
                projetoNome,
                esperas == null ? List.of() : List.copyOf(esperas),
                bloqueada
        );
    }
}
