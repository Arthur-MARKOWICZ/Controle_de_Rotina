package com.controlerotina.xp.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "xp_evento",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_xp_evento_tipo_origem",
                columnNames = {"tipo", "origem_id"}
        )
)
public class XpEvento {

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoXpEvento tipo;

    @Column(name = "origem_id", nullable = false)
    private UUID origemId;

    @Column(nullable = false)
    private int quantidade;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected XpEvento() {
    }

    public XpEvento(
            UUID id,
            UUID usuarioId,
            TipoXpEvento tipo,
            UUID origemId,
            int quantidade,
            Instant createdAt
    ) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.tipo = tipo;
        this.origemId = origemId;
        this.quantidade = quantidade;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public TipoXpEvento getTipo() {
        return tipo;
    }

    public UUID getOrigemId() {
        return origemId;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
