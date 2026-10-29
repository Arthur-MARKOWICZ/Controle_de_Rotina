package com.controlerotina.calendario.dto;

import java.util.UUID;

import com.controlerotina.calendario.CalendarioAgregador;
import com.controlerotina.tarefa.entity.Prioridade;

public record CalendarioBlocoResponse(
        UUID id,
        UUID tarefaId,
        String titulo,
        int duracaoMinutos,
        int ordem,
        Prioridade prioridade
) {
    public static CalendarioBlocoResponse from(CalendarioAgregador.BlocoInput bloco) {
        return new CalendarioBlocoResponse(
                bloco.id(),
                bloco.tarefaId(),
                bloco.titulo(),
                bloco.duracaoMinutos(),
                bloco.ordem(),
                bloco.prioridade()
        );
    }
}
