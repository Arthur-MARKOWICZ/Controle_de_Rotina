package com.controlerotina.pendencia.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "espera")
public class Espera {

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem_tipo", nullable = false, length = 20)
    private TipoRecursoEspera origemTipo;

    @Column(name = "origem_id", nullable = false)
    private UUID origemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "alvo_tipo", nullable = false, length = 20)
    private TipoRecursoEspera alvoTipo;

    @Column(name = "alvo_id", nullable = false)
    private UUID alvoId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Espera() {
    }

    public Espera(
            UUID id,
            UUID usuarioId,
            TipoRecursoEspera origemTipo,
            UUID origemId,
            TipoRecursoEspera alvoTipo,
            UUID alvoId,
            Instant createdAt
    ) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.origemTipo = origemTipo;
        this.origemId = origemId;
        this.alvoTipo = alvoTipo;
        this.alvoId = alvoId;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public TipoRecursoEspera getOrigemTipo() {
        return origemTipo;
    }

    public UUID getOrigemId() {
        return origemId;
    }

    public TipoRecursoEspera getAlvoTipo() {
        return alvoTipo;
    }

    public UUID getAlvoId() {
        return alvoId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
