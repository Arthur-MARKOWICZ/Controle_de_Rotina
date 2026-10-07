package com.controlerotina.tarefa.dto;

import java.time.Instant;
import java.util.List;

import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.entity.StatusTarefa;

public record TarefaFiltro(
        List<StatusTarefa> status,
        List<Prioridade> prioridade,
        Instant prazoDe,
        Instant prazoAte,
        Integer duracaoDe,
        Integer duracaoAte,
        Instant criadoDe,
        Instant criadoAte,
        Instant atualizadoDe,
        Instant atualizadoAte,
        boolean incluirDeletadas,
        boolean somenteDeletadas
) {
}
