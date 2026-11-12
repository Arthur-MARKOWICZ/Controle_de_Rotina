package com.controlerotina.pendencia.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.controlerotina.pendencia.entity.Espera;
import com.controlerotina.pendencia.entity.TipoRecursoEspera;

public interface EsperaRepository extends JpaRepository<Espera, UUID> {

    boolean existsByUsuarioIdAndOrigemTipoAndOrigemIdAndAlvoTipoAndAlvoId(
            UUID usuarioId,
            TipoRecursoEspera origemTipo,
            UUID origemId,
            TipoRecursoEspera alvoTipo,
            UUID alvoId
    );

    List<Espera> findByUsuarioIdAndOrigemTipoAndOrigemIdOrderByCreatedAtAsc(
            UUID usuarioId,
            TipoRecursoEspera origemTipo,
            UUID origemId
    );

    Optional<Espera> findByIdAndUsuarioIdAndOrigemTipoAndOrigemId(
            UUID id,
            UUID usuarioId,
            TipoRecursoEspera origemTipo,
            UUID origemId
    );

    List<Espera> findByUsuarioIdAndOrigemTipoAndOrigemIdIn(
            UUID usuarioId,
            TipoRecursoEspera origemTipo,
            Collection<UUID> origemIds
    );

    List<Espera> findByUsuarioId(UUID usuarioId);

    void deleteByUsuarioIdAndOrigemTipoAndOrigemId(
            UUID usuarioId,
            TipoRecursoEspera origemTipo,
            UUID origemId
    );

    void deleteByUsuarioIdAndAlvoTipoAndAlvoId(
            UUID usuarioId,
            TipoRecursoEspera alvoTipo,
            UUID alvoId
    );
}
