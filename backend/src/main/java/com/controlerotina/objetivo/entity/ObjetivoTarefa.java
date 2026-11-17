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
@Table(name = "objetivo_tarefa")
@IdClass(ObjetivoTarefa.ObjetivoTarefaId.class)
public class ObjetivoTarefa {

    @Id
    @Column(name = "objetivo_id", nullable = false)
    private UUID objetivoId;

    @Id
    @Column(name = "tarefa_id", nullable = false)
    private UUID tarefaId;

    protected ObjetivoTarefa() {
    }

    public ObjetivoTarefa(UUID objetivoId, UUID tarefaId) {
        this.objetivoId = objetivoId;
        this.tarefaId = tarefaId;
    }

    public UUID getObjetivoId() {
        return objetivoId;
    }

    public UUID getTarefaId() {
        return tarefaId;
    }

    public static class ObjetivoTarefaId implements Serializable {
        private UUID objetivoId;
        private UUID tarefaId;

        public ObjetivoTarefaId() {
        }

        public ObjetivoTarefaId(UUID objetivoId, UUID tarefaId) {
            this.objetivoId = objetivoId;
            this.tarefaId = tarefaId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ObjetivoTarefaId that)) {
                return false;
            }
            return Objects.equals(objetivoId, that.objetivoId) && Objects.equals(tarefaId, that.tarefaId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(objetivoId, tarefaId);
        }
    }
}
