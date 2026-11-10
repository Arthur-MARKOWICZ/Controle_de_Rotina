package com.controlerotina.habito.repository;

import java.util.Collection;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.controlerotina.habito.entity.Habito;
import com.controlerotina.habito.entity.StatusHabito;
import com.controlerotina.tag.entity.HabitoTag;

import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

public final class HabitoSpecs {

    private HabitoSpecs() {
    }

    public static Specification<Habito> doUsuario(UUID usuarioId) {
        return (root, query, cb) -> cb.equal(root.get("usuarioId"), usuarioId);
    }

    public static Specification<Habito> comStatus(StatusHabito status) {
        return (root, query, cb) -> {
            if (status == null) {
                return cb.conjunction();
            }
            return cb.equal(root.get("status"), status);
        };
    }

    public static Specification<Habito> filtroDelecao(boolean incluirDeletadas, boolean somenteDeletadas) {
        return (root, query, cb) -> {
            if (somenteDeletadas) {
                return cb.isNotNull(root.get("deletedAt"));
            }
            if (incluirDeletadas) {
                return cb.conjunction();
            }
            return cb.isNull(root.get("deletedAt"));
        };
    }

    public static Specification<Habito> comAlgumaTag(Collection<UUID> tagIds) {
        return (root, query, cb) -> {
            if (tagIds == null || tagIds.isEmpty()) {
                return cb.conjunction();
            }
            Subquery<UUID> subquery = query.subquery(UUID.class);
            Root<HabitoTag> tagRoot = subquery.from(HabitoTag.class);
            subquery.select(tagRoot.get("habitoId"))
                    .where(tagRoot.get("tagId").in(tagIds));
            return root.get("id").in(subquery);
        };
    }
}
