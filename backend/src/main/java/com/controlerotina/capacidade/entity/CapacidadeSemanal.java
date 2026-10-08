package com.controlerotina.capacidade.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "capacidade_semanal")
public class CapacidadeSemanal {

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "dia_semana", nullable = false, length = 20)
    private DiaSemana diaSemana;

    @Column(nullable = false)
    private int horas;

    @Column(nullable = false)
    private boolean ativo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CapacidadeSemanal() {
    }

    public CapacidadeSemanal(
            UUID id,
            UUID usuarioId,
            DiaSemana diaSemana,
            int horas,
            boolean ativo,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.diaSemana = diaSemana;
        this.horas = horas;
        this.ativo = ativo;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public DiaSemana getDiaSemana() {
        return diaSemana;
    }

    public int getHoras() {
        return horas;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void atualizar(DiaSemana diaSemana, Integer horas, Instant updatedAt) {
        if (diaSemana != null) {
            this.diaSemana = diaSemana;
        }
        if (horas != null) {
            this.horas = horas;
        }
        this.updatedAt = updatedAt;
    }

    public void softDelete(Instant updatedAt) {
        this.ativo = false;
        this.updatedAt = updatedAt;
    }

    public void restore(Instant updatedAt) {
        this.ativo = true;
        this.updatedAt = updatedAt;
    }
}
