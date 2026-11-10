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
@Table(name = "habito_tag")
@IdClass(HabitoTag.HabitoTagId.class)
public class HabitoTag {

    @Id
    @Column(name = "habito_id", nullable = false)
    private UUID habitoId;

    @Id
    @Column(name = "tag_id", nullable = false)
    private UUID tagId;

    protected HabitoTag() {
    }

    public HabitoTag(UUID habitoId, UUID tagId) {
        this.habitoId = habitoId;
        this.tagId = tagId;
    }

    public UUID getHabitoId() {
        return habitoId;
    }

    public UUID getTagId() {
        return tagId;
    }

    public static class HabitoTagId implements Serializable {
        private UUID habitoId;
        private UUID tagId;

        public HabitoTagId() {
        }

        public HabitoTagId(UUID habitoId, UUID tagId) {
            this.habitoId = habitoId;
            this.tagId = tagId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof HabitoTagId that)) {
                return false;
            }
            return Objects.equals(habitoId, that.habitoId) && Objects.equals(tagId, that.tagId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(habitoId, tagId);
        }
    }
}
