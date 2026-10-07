package com.controlerotina.tarefa.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tarefa")
public class Tarefa {

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(nullable = false, length = 120)
    private String titulo;

    @Column(nullable = false, length = 2000)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Prioridade prioridade;

    @Column(nullable = false)
    private Instant prazo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusTarefa status;

    @Column(name = "duracao_horas", nullable = false)
    private int duracaoHoras;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Tarefa() {
    }

    public Tarefa(
            UUID id,
            UUID usuarioId,
            String titulo,
            String descricao,
            Prioridade prioridade,
            Instant prazo,
            StatusTarefa status,
            int duracaoHoras,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.titulo = titulo;
        this.descricao = descricao;
        this.prioridade = prioridade;
        this.prazo = prazo;
        this.status = status;
        this.duracaoHoras = duracaoHoras;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getDescricao() {
        return descricao;
    }

    public Prioridade getPrioridade() {
        return prioridade;
    }

    public Instant getPrazo() {
        return prazo;
    }

    public StatusTarefa getStatus() {
        return status;
    }

    public int getDuracaoHoras() {
        return duracaoHoras;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void atualizar(
            String titulo,
            String descricao,
            Prioridade prioridade,
            Instant prazo,
            StatusTarefa status,
            Integer duracaoHoras,
            Instant updatedAt
    ) {
        if (titulo != null) {
            this.titulo = titulo;
        }
        if (descricao != null) {
            this.descricao = descricao;
        }
        if (prioridade != null) {
            this.prioridade = prioridade;
        }
        if (prazo != null) {
            this.prazo = prazo;
        }
        if (status != null) {
            this.status = status;
        }
        if (duracaoHoras != null) {
            this.duracaoHoras = duracaoHoras;
        }
        this.updatedAt = updatedAt;
    }

    public void softDelete(Instant deletedAt) {
        this.deletedAt = deletedAt;
        this.updatedAt = deletedAt;
    }

    public void restore(Instant updatedAt) {
        this.deletedAt = null;
        this.updatedAt = updatedAt;
    }
}
