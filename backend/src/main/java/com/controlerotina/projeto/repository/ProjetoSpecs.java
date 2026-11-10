package com.controlerotina.projeto.repository;

import java.util.Collection;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.controlerotina.projeto.entity.Projeto;
import com.controlerotina.projeto.entity.StatusProjeto;
import com.controlerotina.tag.entity.ProjetoTag;

import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

public final class ProjetoSpecs {

    private ProjetoSpecs() {
    }

    public static Specification<Projeto> doUsuario(UUID usuarioId) {
        return (root, query, cb) -> cb.equal(root.get("usuarioId"), usuarioId);
    }

    public static Specification<Projeto> comStatus(StatusProjeto status) {
        return (root, query, cb) -> {
            if (status == null) {
                return cb.conjunction();
            }
            return cb.equal(root.get("status"), status);
        };
    }

    public static Specification<Projeto> comAlgumaTag(Collection<UUID> tagIds) {
        return (root, query, cb) -> {
            if (tagIds == null || tagIds.isEmpty()) {
                return cb.conjunction();
            }
            Subquery<UUID> subquery = query.subquery(UUID.class);
            Root<ProjetoTag> tagRoot = subquery.from(ProjetoTag.class);
            subquery.select(tagRoot.get("projetoId"))
                    .where(tagRoot.get("tagId").in(tagIds));
            return root.get("id").in(subquery);
        };
    }
}
