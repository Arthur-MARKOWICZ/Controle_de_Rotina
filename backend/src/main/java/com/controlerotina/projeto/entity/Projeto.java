package com.controlerotina.projeto.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "projeto")
public class Projeto {

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(nullable = false, length = 80)
    private String nome;

    @Column(length = 500)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusProjeto status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Projeto() {
    }

    public Projeto(
            UUID id,
            UUID usuarioId,
            String nome,
            String descricao,
            StatusProjeto status,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.nome = nome;
        this.descricao = descricao;
        this.status = status;
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

    public String getDescricao() {
        return descricao;
    }

    public StatusProjeto getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void atualizar(String nome, String descricao, Instant updatedAt) {
        if (nome != null) {
            this.nome = nome;
        }
        if (descricao != null) {
            this.descricao = descricao.isBlank() ? null : descricao;
        }
        this.updatedAt = updatedAt;
    }

    public void arquivar(Instant updatedAt) {
        this.status = StatusProjeto.ARQUIVADO;
        this.updatedAt = updatedAt;
    }

    public void reativar(Instant updatedAt) {
        this.status = StatusProjeto.ATIVO;
        this.updatedAt = updatedAt;
    }
}
