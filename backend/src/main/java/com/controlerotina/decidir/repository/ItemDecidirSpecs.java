package com.controlerotina.decidir.repository;

import java.util.Collection;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.controlerotina.decidir.entity.ItemDecidir;
import com.controlerotina.tag.entity.DecidirTag;

import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

public final class ItemDecidirSpecs {

    private ItemDecidirSpecs() {
    }

    public static Specification<ItemDecidir> doUsuario(UUID usuarioId) {
        return (root, query, cb) -> cb.equal(root.get("usuarioId"), usuarioId);
    }

    public static Specification<ItemDecidir> abertos() {
        return (root, query, cb) -> cb.isNull(root.get("tarefaId"));
    }

    public static Specification<ItemDecidir> promovidos() {
        return (root, query, cb) -> cb.isNotNull(root.get("tarefaId"));
    }

    public static Specification<ItemDecidir> comAlgumaTag(Collection<UUID> tagIds) {
        return (root, query, cb) -> {
            if (tagIds == null || tagIds.isEmpty()) {
                return cb.conjunction();
            }
            Subquery<UUID> subquery = query.subquery(UUID.class);
            Root<DecidirTag> tagRoot = subquery.from(DecidirTag.class);
            subquery.select(tagRoot.get("itemDecidirId"))
                    .where(tagRoot.get("tagId").in(tagIds));
            return root.get("id").in(subquery);
        };
    }
}
