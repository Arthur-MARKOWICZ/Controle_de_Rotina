package com.controlerotina.objetivo.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "objetivo")
public class Objetivo {

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(nullable = false, length = 80)
    private String titulo;

    @Column(length = 500)
    private String descricao;

    @Column
    private LocalDate prazo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusObjetivo status;

    @Column(name = "concluido_em")
    private Instant concluidoEm;

    @Column(name = "arquivado_em")
    private Instant arquivadoEm;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Objetivo() {
    }

    public Objetivo(
            UUID id,
            UUID usuarioId,
            String titulo,
            String descricao,
            LocalDate prazo,
            Instant createdAt
    ) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.titulo = titulo;
        this.descricao = descricao;
        this.prazo = prazo;
        this.status = StatusObjetivo.ATIVO;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
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

    public LocalDate getPrazo() {
        return prazo;
    }

    public StatusObjetivo getStatus() {
        return status;
    }

    public Instant getConcluidoEm() {
        return concluidoEm;
    }

    public Instant getArquivadoEm() {
        return arquivadoEm;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void atualizar(
            String titulo,
            String descricao,
            boolean prazoPresente,
            LocalDate prazo,
            Instant updatedAt
    ) {
        if (titulo != null) {
            this.titulo = titulo;
        }
        if (descricao != null) {
            this.descricao = descricao.isBlank() ? null : descricao;
        }
        if (prazoPresente) {
            this.prazo = prazo;
        }
        this.updatedAt = updatedAt;
    }

    public void concluir(Instant agora) {
        this.status = StatusObjetivo.CONCLUIDO;
        this.concluidoEm = agora;
        this.updatedAt = agora;
    }

    public void arquivar(Instant agora) {
        this.status = StatusObjetivo.ARQUIVADO;
        this.arquivadoEm = agora;
        this.updatedAt = agora;
    }

    public void reabrir(Instant agora) {
        this.status = StatusObjetivo.ATIVO;
        this.concluidoEm = null;
        this.arquivadoEm = null;
        this.updatedAt = agora;
    }
}
