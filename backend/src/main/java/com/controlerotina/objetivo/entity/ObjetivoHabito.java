package com.controlerotina.objetivo.entity;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "objetivo_habito")
@IdClass(ObjetivoHabito.ObjetivoHabitoId.class)
public class ObjetivoHabito {

    @Id
    @Column(name = "objetivo_id", nullable = false)
    private UUID objetivoId;

    @Id
    @Column(name = "habito_id", nullable = false)
    private UUID habitoId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ObjetivoHabito() {
    }

    public ObjetivoHabito(UUID objetivoId, UUID habitoId, Instant createdAt) {
        this.objetivoId = objetivoId;
        this.habitoId = habitoId;
        this.createdAt = createdAt;
    }

    public UUID getObjetivoId() {
        return objetivoId;
    }

    public UUID getHabitoId() {
        return habitoId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public static class ObjetivoHabitoId implements Serializable {
        private UUID objetivoId;
        private UUID habitoId;

        public ObjetivoHabitoId() {
        }

        public ObjetivoHabitoId(UUID objetivoId, UUID habitoId) {
            this.objetivoId = objetivoId;
            this.habitoId = habitoId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ObjetivoHabitoId that)) {
                return false;
            }
            return Objects.equals(objetivoId, that.objetivoId) && Objects.equals(habitoId, that.habitoId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(objetivoId, habitoId);
        }
    }
}
