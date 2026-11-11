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
@Table(name = "decidir_tag")
@IdClass(DecidirTag.DecidirTagId.class)
public class DecidirTag {

    @Id
    @Column(name = "item_decidir_id", nullable = false)
    private UUID itemDecidirId;

    @Id
    @Column(name = "tag_id", nullable = false)
    private UUID tagId;

    protected DecidirTag() {
    }

    public DecidirTag(UUID itemDecidirId, UUID tagId) {
        this.itemDecidirId = itemDecidirId;
        this.tagId = tagId;
    }

    public UUID getItemDecidirId() {
        return itemDecidirId;
    }

    public UUID getTagId() {
        return tagId;
    }

    public static class DecidirTagId implements Serializable {
        private UUID itemDecidirId;
        private UUID tagId;

        public DecidirTagId() {
        }

        public DecidirTagId(UUID itemDecidirId, UUID tagId) {
            this.itemDecidirId = itemDecidirId;
            this.tagId = tagId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof DecidirTagId that)) {
                return false;
            }
            return Objects.equals(itemDecidirId, that.itemDecidirId) && Objects.equals(tagId, that.tagId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(itemDecidirId, tagId);
        }
    }
}
