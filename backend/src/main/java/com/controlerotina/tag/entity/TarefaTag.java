package com.controlerotina.tag.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "tarefa_tag")
@IdClass(TarefaTag.TarefaTagId.class)
public class TarefaTag {

    @Id
    @Column(name = "tarefa_id", nullable = false)
    private UUID tarefaId;

    @Id
    @Column(name = "tag_id", nullable = false)
    private UUID tagId;

    protected TarefaTag() {
    }

    public TarefaTag(UUID tarefaId, UUID tagId) {
        this.tarefaId = tarefaId;
        this.tagId = tagId;
    }

    public UUID getTarefaId() {
        return tarefaId;
    }

    public UUID getTagId() {
        return tagId;
    }

    public static class TarefaTagId implements Serializable {
        private UUID tarefaId;
        private UUID tagId;

        public TarefaTagId() {
        }

        public TarefaTagId(UUID tarefaId, UUID tagId) {
            this.tarefaId = tarefaId;
            this.tagId = tagId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof TarefaTagId that)) {
                return false;
            }
            return Objects.equals(tarefaId, that.tarefaId) && Objects.equals(tagId, that.tagId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tarefaId, tagId);
        }
    }
}
