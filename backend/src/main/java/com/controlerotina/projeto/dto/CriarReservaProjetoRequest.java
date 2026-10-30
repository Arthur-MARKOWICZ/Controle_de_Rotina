package com.controlerotina.projeto.dto;

import com.controlerotina.capacidade.entity.DiaSemana;
import com.controlerotina.tarefa.validation.DuracaoMinutosValida;

import jakarta.validation.constraints.NotNull;

public record CriarReservaProjetoRequest(
        @NotNull
        DiaSemana diaSemana,

        @NotNull
        @DuracaoMinutosValida
        Integer minutosReservados
) {
}
