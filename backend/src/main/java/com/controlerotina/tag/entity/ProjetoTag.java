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
@Table(name = "projeto_tag")
@IdClass(ProjetoTag.ProjetoTagId.class)
public class ProjetoTag {

    @Id
    @Column(name = "projeto_id", nullable = false)
    private UUID projetoId;

    @Id
    @Column(name = "tag_id", nullable = false)
    private UUID tagId;

    protected ProjetoTag() {
    }

    public ProjetoTag(UUID projetoId, UUID tagId) {
        this.projetoId = projetoId;
        this.tagId = tagId;
    }

    public UUID getProjetoId() {
        return projetoId;
    }

    public UUID getTagId() {
        return tagId;
    }

    public static class ProjetoTagId implements Serializable {
        private UUID projetoId;
        private UUID tagId;

        public ProjetoTagId() {
        }

        public ProjetoTagId(UUID projetoId, UUID tagId) {
            this.projetoId = projetoId;
            this.tagId = tagId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ProjetoTagId that)) {
                return false;
            }
            return Objects.equals(projetoId, that.projetoId) && Objects.equals(tagId, that.tagId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(projetoId, tagId);
        }
    }
}
