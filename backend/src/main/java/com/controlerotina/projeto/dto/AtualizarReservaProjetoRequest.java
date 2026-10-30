package com.controlerotina.projeto.dto;

import com.controlerotina.capacidade.entity.DiaSemana;
import com.controlerotina.tarefa.validation.DuracaoMinutosValida;

public record AtualizarReservaProjetoRequest(
        DiaSemana diaSemana,

        @DuracaoMinutosValida
        Integer minutosReservados
) {
}
