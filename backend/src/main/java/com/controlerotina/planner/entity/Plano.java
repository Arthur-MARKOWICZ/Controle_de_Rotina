package com.controlerotina.planner.entity;

import java.time.Instant;
import java.time.LocalDate;
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
        name = "plano",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_plano_usuario_data_versao",
                columnNames = {"usuario_id", "data", "versao"}
        )
)
public class Plano {

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(nullable = false)
    private LocalDate data;

    @Column(nullable = false)
    private int versao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusPlano status;

    @Column(name = "tem_conflito", nullable = false)
    private boolean temConflito;

    @Column(length = 500)
    private String mensagem;

    @Column(name = "gerado_em", nullable = false)
    private Instant geradoEm;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Plano() {
    }

    public Plano(
            UUID id,
            UUID usuarioId,
            LocalDate data,
            int versao,
            StatusPlano status,
            boolean temConflito,
            String mensagem,
            Instant geradoEm,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.data = data;
        this.versao = versao;
        this.status = status;
        this.temConflito = temConflito;
        this.mensagem = mensagem;
        this.geradoEm = geradoEm;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public LocalDate getData() {
        return data;
    }

    public int getVersao() {
        return versao;
    }

    public StatusPlano getStatus() {
        return status;
    }

    public boolean isTemConflito() {
        return temConflito;
    }

    public String getMensagem() {
        return mensagem;
    }

    public Instant getGeradoEm() {
        return geradoEm;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void atualizarConflito(boolean temConflito, String mensagem, Instant updatedAt) {
        this.temConflito = temConflito;
        this.mensagem = mensagem;
        this.updatedAt = updatedAt;
    }
}
