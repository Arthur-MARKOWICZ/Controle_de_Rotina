package com.controlerotina.tarefa.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.validation.DuracaoMinutosValida;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CriarTarefaRequest(
        @NotBlank
        @Size(max = 120)
        String titulo,

        @Size(max = 2000)
        String descricao,

        Prioridade prioridade,

        Instant prazo,

        @DuracaoMinutosValida
        Integer duracaoMinutos,

        UUID projetoId,

        List<UUID> tagIds
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
