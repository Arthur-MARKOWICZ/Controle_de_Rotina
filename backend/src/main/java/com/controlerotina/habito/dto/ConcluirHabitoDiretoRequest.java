package com.controlerotina.habito.dto;

/** Body opcional do concluir-direto de hábito; {@code minutos} ausente = duração do bloco. */
public record ConcluirHabitoDiretoRequest(
        Integer minutos
) {
}
