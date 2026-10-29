package com.controlerotina.tarefa.dto;

import java.time.Instant;

import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.validation.DuracaoMinutosValida;

import jakarta.validation.constraints.Size;

public record AtualizarTarefaRequest(
        @Size(max = 120)
        String titulo,

        @Size(max = 2000)
        String descricao,

        Prioridade prioridade,

        Instant prazo,

        StatusTarefa status,

        @DuracaoMinutosValida
        Integer duracaoMinutos
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
