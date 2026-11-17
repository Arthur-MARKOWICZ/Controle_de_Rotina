package com.controlerotina.objetivo.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "objetivo_projeto")
@IdClass(ObjetivoProjeto.ObjetivoProjetoId.class)
public class ObjetivoProjeto {

    @Id
    @Column(name = "objetivo_id", nullable = false)
    private UUID objetivoId;

    @Id
    @Column(name = "projeto_id", nullable = false)
    private UUID projetoId;

    protected ObjetivoProjeto() {
    }

    public ObjetivoProjeto(UUID objetivoId, UUID projetoId) {
        this.objetivoId = objetivoId;
        this.projetoId = projetoId;
    }

    public UUID getObjetivoId() {
        return objetivoId;
    }

    public UUID getProjetoId() {
        return projetoId;
    }

    public static class ObjetivoProjetoId implements Serializable {
        private UUID objetivoId;
        private UUID projetoId;

        public ObjetivoProjetoId() {
        }

        public ObjetivoProjetoId(UUID objetivoId, UUID projetoId) {
            this.objetivoId = objetivoId;
            this.projetoId = projetoId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ObjetivoProjetoId that)) {
                return false;
            }
            return Objects.equals(objetivoId, that.objetivoId) && Objects.equals(projetoId, that.projetoId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(objetivoId, projetoId);
        }
    }
}
