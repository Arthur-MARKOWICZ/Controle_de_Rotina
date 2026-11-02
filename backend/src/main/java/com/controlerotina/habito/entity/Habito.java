package com.controlerotina.habito.entity;

import java.time.Instant;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.controlerotina.capacidade.entity.DiaSemana;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "habito")
public class Habito {

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(nullable = false, length = 120)
    private String titulo;

    @Column(length = 2000)
    private String descricao;

    @Column(name = "duracao_minutos", nullable = false)
    private int duracaoMinutos;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "habito_dia_semana", joinColumns = @JoinColumn(name = "habito_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "dia_semana", nullable = false, length = 20)
    private Set<DiaSemana> diasSemana = new HashSet<>();

    @Column(name = "horario_preferencial")
    private LocalTime horarioPreferencial;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusHabito status;

    @Column(name = "streak_atual", nullable = false)
    private int streakAtual;

    @Column(name = "melhor_streak", nullable = false)
    private int melhorStreak;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Habito() {
    }

    public Habito(
            UUID id,
            UUID usuarioId,
            String titulo,
            String descricao,
            int duracaoMinutos,
            Set<DiaSemana> diasSemana,
            LocalTime horarioPreferencial,
            StatusHabito status,
            int streakAtual,
            int melhorStreak,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.titulo = titulo;
        this.descricao = descricao;
        this.duracaoMinutos = duracaoMinutos;
        this.diasSemana = diasSemana == null ? new HashSet<>() : new HashSet<>(diasSemana);
        this.horarioPreferencial = horarioPreferencial;
        this.status = status;
        this.streakAtual = streakAtual;
        this.melhorStreak = melhorStreak;
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

    public int getDuracaoMinutos() {
        return duracaoMinutos;
    }

    public Set<DiaSemana> getDiasSemana() {
        return Set.copyOf(diasSemana);
    }

    public LocalTime getHorarioPreferencial() {
        return horarioPreferencial;
    }

    public StatusHabito getStatus() {
        return status;
    }

    public int getStreakAtual() {
        return streakAtual;
    }

    public int getMelhorStreak() {
        return melhorStreak;
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

    public boolean isDeletado() {
        return deletedAt != null;
    }

    public void atualizar(
            String titulo,
            String descricao,
            Integer duracaoMinutos,
            Set<DiaSemana> diasSemana,
            LocalTime horarioPreferencial,
            boolean limparHorario,
            Instant updatedAt
    ) {
        if (titulo != null) {
            this.titulo = titulo;
        }
        if (descricao != null) {
            this.descricao = descricao.isBlank() ? null : descricao;
        }
        if (duracaoMinutos != null) {
            this.duracaoMinutos = duracaoMinutos;
        }
        if (diasSemana != null) {
            this.diasSemana = new HashSet<>(diasSemana);
        }
        if (limparHorario) {
            this.horarioPreferencial = null;
        } else if (horarioPreferencial != null) {
            this.horarioPreferencial = horarioPreferencial;
        }
        this.updatedAt = updatedAt;
    }

    public void pausar(Instant updatedAt) {
        this.status = StatusHabito.PAUSADO;
        this.updatedAt = updatedAt;
    }

    public void reativar(Instant updatedAt) {
        this.status = StatusHabito.ATIVO;
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

    public void atualizarStreak(int streakAtual, int melhorStreak, Instant updatedAt) {
        this.streakAtual = streakAtual;
        this.melhorStreak = melhorStreak;
        this.updatedAt = updatedAt;
    }
}
