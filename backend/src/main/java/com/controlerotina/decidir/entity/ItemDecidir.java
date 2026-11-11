package com.controlerotina.decidir.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "item_decidir")
public class ItemDecidir {

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(nullable = false, length = 120)
    private String titulo;

    @Column(length = 2000)
    private String descricao;

    @Column(name = "decidir_ate")
    private Instant decidirAte;

    @Column(name = "projeto_id")
    private UUID projetoId;

    @Column(name = "tarefa_id")
    private UUID tarefaId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ItemDecidir() {
    }

    public ItemDecidir(
            UUID id,
            UUID usuarioId,
            String titulo,
            String descricao,
            Instant decidirAte,
            UUID projetoId,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.titulo = titulo;
        this.descricao = descricao;
        this.decidirAte = decidirAte;
        this.projetoId = projetoId;
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

    public Instant getDecidirAte() {
        return decidirAte;
    }

    public UUID getProjetoId() {
        return projetoId;
    }

    public UUID getTarefaId() {
        return tarefaId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isPromovido() {
        return tarefaId != null;
    }

    public void atualizar(
            String titulo,
            String descricao,
            boolean descricaoPresente,
            Instant decidirAte,
            boolean decidirAtePresente,
            UUID projetoId,
            boolean projetoIdPresente,
            Instant updatedAt
    ) {
        if (titulo != null) {
            this.titulo = titulo;
        }
        if (descricaoPresente) {
            this.descricao = descricao == null || descricao.isBlank() ? null : descricao;
        }
        if (decidirAtePresente) {
            this.decidirAte = decidirAte;
        }
        if (projetoIdPresente) {
            this.projetoId = projetoId;
        }
        this.updatedAt = updatedAt;
    }

    public void promover(UUID tarefaId, Instant updatedAt) {
        this.tarefaId = tarefaId;
        this.updatedAt = updatedAt;
    }

    public void reabrir(Instant updatedAt) {
        this.tarefaId = null;
        this.updatedAt = updatedAt;
    }

    public void limparProjetoId(Instant updatedAt) {
        this.projetoId = null;
        this.updatedAt = updatedAt;
    }
}
