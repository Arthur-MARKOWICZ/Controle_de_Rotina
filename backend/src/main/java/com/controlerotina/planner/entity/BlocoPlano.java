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

    @Column(name = "tarefa_id")
    private UUID tarefaId;

    @Column(name = "habito_id")
    private UUID habitoId;

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

    /** Bloco de tarefa (compatível com callers existentes). */
    public BlocoPlano(
            UUID id,
            UUID planoId,
            UUID tarefaId,
            int duracaoMinutos,
            int ordem,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(id, planoId, tarefaId, null, duracaoMinutos, ordem, createdAt, updatedAt);
    }

    public BlocoPlano(
            UUID id,
            UUID planoId,
            UUID tarefaId,
            UUID habitoId,
            int duracaoMinutos,
            int ordem,
            Instant createdAt,
            Instant updatedAt
    ) {
        if ((tarefaId == null) == (habitoId == null)) {
            throw new IllegalArgumentException("Bloco deve ter exatamente uma origem: tarefa ou hábito");
        }
        this.id = id;
        this.planoId = planoId;
        this.tarefaId = tarefaId;
        this.habitoId = habitoId;
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

    public UUID getHabitoId() {
        return habitoId;
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

    public void atualizarDuracao(int duracaoMinutos, Instant updatedAt) {
        this.duracaoMinutos = duracaoMinutos;
        this.updatedAt = updatedAt;
    }
}
