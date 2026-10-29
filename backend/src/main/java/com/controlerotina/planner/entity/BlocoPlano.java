package com.controlerotina.planner.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "bloco_plano")
public class BlocoPlano {

    @Id
    private UUID id;

    @Column(name = "plano_id", nullable = false)
    private UUID planoId;

    @Column(name = "tarefa_id", nullable = false)
    private UUID tarefaId;

    @Column(name = "duracao_minutos", nullable = false)
    private int duracaoMinutos;

    @Column(nullable = false)
    private int ordem;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BlocoPlano() {
    }

    public BlocoPlano(
            UUID id,
            UUID planoId,
            UUID tarefaId,
            int duracaoMinutos,
            int ordem,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.planoId = planoId;
        this.tarefaId = tarefaId;
        this.duracaoMinutos = duracaoMinutos;
        this.ordem = ordem;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPlanoId() {
        return planoId;
    }

    public UUID getTarefaId() {
        return tarefaId;
    }

    public int getDuracaoMinutos() {
        return duracaoMinutos;
    }

    public int getOrdem() {
        return ordem;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void atualizarOrdem(int ordem, Instant updatedAt) {
        this.ordem = ordem;
        this.updatedAt = updatedAt;
    }
}
