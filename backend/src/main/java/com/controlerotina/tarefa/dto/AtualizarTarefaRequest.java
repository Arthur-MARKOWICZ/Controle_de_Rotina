package com.controlerotina.tarefa.dto;

import java.time.Instant;

import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.entity.StatusTarefa;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record AtualizarTarefaRequest(
        @Size(max = 120)
        String titulo,

        @Size(max = 2000)
        String descricao,

        Prioridade prioridade,

        Instant prazo,

        StatusTarefa status,

        @Min(1)
        Integer duracaoHoras
) {
    public AtualizarTarefaRequest {
        if (titulo != null) {
            titulo = titulo.trim();
        }
        if (descricao != null) {
            descricao = descricao.trim();
        }
    }
}
