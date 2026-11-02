package com.controlerotina.habito.dto;

import java.time.LocalTime;
import java.util.Set;

import com.controlerotina.capacidade.entity.DiaSemana;
import com.controlerotina.tarefa.validation.DuracaoMinutosValida;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CriarHabitoRequest(
        @NotBlank
        @Size(max = 120)
        String titulo,

        @Size(max = 2000)
        String descricao,

        @NotNull
        @DuracaoMinutosValida
        Integer duracaoMinutos,

        @NotEmpty
        Set<DiaSemana> diasSemana,

        LocalTime horarioPreferencial
) {
    public CriarHabitoRequest {
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
