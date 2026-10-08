package com.controlerotina.capacidade.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "capacidade_override")
public class CapacidadeOverride {

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(nullable = false)
    private LocalDate data;

    @Column(nullable = false)
    private int horas;

    @Column(nullable = false)
    private boolean ativo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CapacidadeOverride() {
    }

    public CapacidadeOverride(
            UUID id,
            UUID usuarioId,
            LocalDate data,
            int horas,
            boolean ativo,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.data = data;
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

    public LocalDate getData() {
        return data;
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

    public void atualizar(LocalDate data, Integer horas, Instant updatedAt) {
        if (data != null) {
            this.data = data;
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
