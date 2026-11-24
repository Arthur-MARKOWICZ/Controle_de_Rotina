package com.controlerotina.objetivo.repository;

import java.util.Collection;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.controlerotina.objetivo.entity.Objetivo;
import com.controlerotina.objetivo.entity.StatusObjetivo;

public final class ObjetivoSpecs {

    private ObjetivoSpecs() {
    }

    public static Specification<Objetivo> doUsuario(UUID usuarioId) {
        return (root, query, cb) -> cb.equal(root.get("usuarioId"), usuarioId);
    }

    public static Specification<Objetivo> comStatus(Collection<StatusObjetivo> status) {
        return (root, query, cb) -> root.get("status").in(status);
    }
}
