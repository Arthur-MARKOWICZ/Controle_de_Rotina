package com.controlerotina.decidir.dto;

import java.time.Instant;

import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.validation.DuracaoMinutosValida;

import jakarta.validation.constraints.Size;

public record PromoverDecidirRequest(
        @Size(min = 1, max = 120)
        String titulo,

        @Size(max = 2000)
        String descricao,

        Prioridade prioridade,

        Instant prazo,

        @DuracaoMinutosValida
        Integer duracaoMinutos
) {
    public PromoverDecidirRequest {
        if (titulo != null) {
            titulo = titulo.trim();
        }
        if (descricao != null && descricao.isBlank()) {
            descricao = null;
        } else if (descricao != null) {
            descricao = descricao.trim();
        }
    }
}
