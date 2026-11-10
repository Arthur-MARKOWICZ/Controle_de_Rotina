package com.controlerotina.tag.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tag")
public class Tag {

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(nullable = false, length = 64)
    private String nome;

    @Column(name = "nome_normalizado", nullable = false, length = 64)
    private String nomeNormalizado;

    @Column(nullable = false, length = 7)
    private String cor;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Tag() {
    }

    public Tag(
            UUID id,
            UUID usuarioId,
            String nome,
            String nomeNormalizado,
            String cor,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.nome = nome;
        this.nomeNormalizado = nomeNormalizado;
        this.cor = cor;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public String getNome() {
        return nome;
    }

    public String getNomeNormalizado() {
        return nomeNormalizado;
    }

    public String getCor() {
        return cor;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void atualizar(String nome, String nomeNormalizado, String cor, Instant updatedAt) {
        if (nome != null) {
            this.nome = nome;
            this.nomeNormalizado = nomeNormalizado;
        }
        if (cor != null) {
            this.cor = cor;
        }
        this.updatedAt = updatedAt;
    }
}
