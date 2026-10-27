package com.controlerotina.execucao.entity;

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
        name = "execucao",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_execucao_bloco_plano",
                columnNames = {"bloco_plano_id"}
        )
)
public class Execucao {

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "bloco_plano_id", nullable = false)
    private UUID blocoPlanoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusExecucao status;

    @Column(name = "iniciado_em")
    private Instant iniciadoEm;

    @Column(name = "concluido_em")
    private Instant concluidoEm;

    @Column(name = "abortado_em")
    private Instant abortadoEm;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Execucao() {
    }

    public Execucao(
            UUID id,
            UUID usuarioId,
            UUID blocoPlanoId,
            StatusExecucao status,
            Instant iniciadoEm,
            Instant concluidoEm,
            Instant abortadoEm,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.blocoPlanoId = blocoPlanoId;
        this.status = status;
        this.iniciadoEm = iniciadoEm;
        this.concluidoEm = concluidoEm;
        this.abortadoEm = abortadoEm;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public UUID getBlocoPlanoId() {
        return blocoPlanoId;
    }

    public StatusExecucao getStatus() {
        return status;
    }

    public Instant getIniciadoEm() {
        return iniciadoEm;
    }

    public Instant getConcluidoEm() {
        return concluidoEm;
    }

    public Instant getAbortadoEm() {
        return abortadoEm;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public static Execucao iniciarNova(
            UUID id,
            UUID usuarioId,
            UUID blocoPlanoId,
            Instant agora
    ) {
        return new Execucao(
                id,
                usuarioId,
                blocoPlanoId,
                StatusExecucao.EM_ANDAMENTO,
                agora,
                null,
                null,
                agora,
                agora
        );
    }

    public void reiniciar(Instant agora) {
        this.status = StatusExecucao.EM_ANDAMENTO;
        this.iniciadoEm = agora;
        this.abortadoEm = null;
        this.updatedAt = agora;
    }

    public void abortar(Instant agora) {
        this.status = StatusExecucao.ABORTADO;
        this.abortadoEm = agora;
        this.updatedAt = agora;
    }

    public void concluir(Instant agora) {
        this.status = StatusExecucao.CONCLUIDO;
        this.concluidoEm = agora;
        this.updatedAt = agora;
    }
}
