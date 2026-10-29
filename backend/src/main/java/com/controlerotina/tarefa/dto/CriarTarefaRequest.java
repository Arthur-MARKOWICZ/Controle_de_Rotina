package com.controlerotina.tarefa.dto;

import java.time.Instant;

import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.validation.DuracaoMinutosValida;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CriarTarefaRequest(
        @NotBlank
        @Size(max = 120)
        String titulo,

        @Size(max = 2000)
        String descricao,

        @NotNull
        Prioridade prioridade,

        Instant prazo,

        @NotNull
        @DuracaoMinutosValida
        Integer duracaoMinutos
) {
    public CriarTarefaRequest {
        if (titulo != null) {
            titulo = titulo.trim();
        }
        if (descricao == null || descricao.isBlank()) {
            descricao = "";
        } else {
            descricao = descricao.trim();
        }
    }
}
