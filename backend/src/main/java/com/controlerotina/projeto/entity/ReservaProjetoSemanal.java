package com.controlerotina.projeto.entity;

import java.time.Instant;
import java.util.UUID;

import com.controlerotina.capacidade.entity.DiaSemana;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "reserva_projeto_semanal")
public class ReservaProjetoSemanal {

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "projeto_id", nullable = false)
    private UUID projetoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "dia_semana", nullable = false, length = 20)
    private DiaSemana diaSemana;

    @Column(name = "minutos_reservados", nullable = false)
    private int minutosReservados;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ReservaProjetoSemanal() {
    }

    public ReservaProjetoSemanal(
            UUID id,
            UUID usuarioId,
            UUID projetoId,
            DiaSemana diaSemana,
            int minutosReservados,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.projetoId = projetoId;
        this.diaSemana = diaSemana;
        this.minutosReservados = minutosReservados;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public UUID getProjetoId() {
        return projetoId;
    }

    public DiaSemana getDiaSemana() {
        return diaSemana;
    }

    public int getMinutosReservados() {
        return minutosReservados;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void atualizar(DiaSemana diaSemana, Integer minutosReservados, Instant updatedAt) {
        if (diaSemana != null) {
            this.diaSemana = diaSemana;
        }
        if (minutosReservados != null) {
            this.minutosReservados = minutosReservados;
        }
        this.updatedAt = updatedAt;
    }
}
