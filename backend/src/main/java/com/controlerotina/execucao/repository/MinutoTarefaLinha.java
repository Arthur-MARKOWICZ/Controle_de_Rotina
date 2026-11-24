package com.controlerotina.execucao.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Uma execução {@code CONCLUIDO} de tarefa: o minuto canônico é a duração do bloco, a mesma base do progresso.
 * {@code planoData} serve de dia de reserva quando {@code concluidoEm} vier nulo.
 */
public record MinutoTarefaLinha(
        UUID tarefaId,
        Instant concluidoEm,
        LocalDate planoData,
        int minutos
) {
}
