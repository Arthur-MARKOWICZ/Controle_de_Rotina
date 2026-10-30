package com.controlerotina.projeto.dto;

import java.time.Instant;
import java.util.UUID;

import com.controlerotina.capacidade.entity.DiaSemana;
import com.controlerotina.projeto.entity.ReservaProjetoSemanal;

public record ReservaProjetoResponse(
        UUID id,
        UUID projetoId,
        DiaSemana diaSemana,
        int minutosReservados,
        Instant createdAt,
        Instant updatedAt
) {
    public static ReservaProjetoResponse from(ReservaProjetoSemanal reserva) {
        return new ReservaProjetoResponse(
                reserva.getId(),
                reserva.getProjetoId(),
                reserva.getDiaSemana(),
                reserva.getMinutosReservados(),
                reserva.getCreatedAt(),
                reserva.getUpdatedAt()
        );
    }
}
