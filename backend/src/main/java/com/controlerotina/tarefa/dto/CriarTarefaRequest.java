package com.controlerotina.tarefa.dto;

import java.time.Instant;

import com.controlerotina.tarefa.entity.Prioridade;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CriarTarefaRequest(
        @NotBlank
        @Size(max = 120)
        String titulo,

        @NotBlank
        @Size(max = 2000)
        String descricao,

        @NotNull
        Prioridade prioridade,

        @NotNull
        Instant prazo,

        @NotNull
        @Min(1)
        Integer duracaoHoras
) {
    public CriarTarefaRequest {
        if (titulo != null) {
            titulo = titulo.trim();
        }
        if (descricao != null) {
            descricao = descricao.trim();
        }
    }
}
