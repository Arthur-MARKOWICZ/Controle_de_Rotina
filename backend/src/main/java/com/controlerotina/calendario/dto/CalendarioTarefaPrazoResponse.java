package com.controlerotina.calendario.dto;

import java.time.Instant;
import java.util.UUID;

import com.controlerotina.calendario.CalendarioAgregador;
import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.entity.StatusTarefa;

public record CalendarioTarefaPrazoResponse(
        UUID id,
        String titulo,
        Instant prazo,
        StatusTarefa status,
        Prioridade prioridade,
        int duracaoMinutos
) {
    public static CalendarioTarefaPrazoResponse from(CalendarioAgregador.TarefaPrazoInput tarefa) {
        return new CalendarioTarefaPrazoResponse(
                tarefa.id(),
                tarefa.titulo(),
                tarefa.prazo(),
                tarefa.status(),
                tarefa.prioridade(),
                tarefa.duracaoMinutos()
        );
    }
}
