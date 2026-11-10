package com.controlerotina.habito.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.controlerotina.habito.entity.Habito;
import com.controlerotina.habito.entity.StatusHabito;

public interface HabitoRepository extends JpaRepository<Habito, UUID>, JpaSpecificationExecutor<Habito> {

    Optional<Habito> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    List<Habito> findByUsuarioIdAndStatusAndDeletedAtIsNull(UUID usuarioId, StatusHabito status);

    Page<Habito> findByUsuarioIdAndDeletedAtIsNull(UUID usuarioId, Pageable pageable);

    Page<Habito> findByUsuarioIdAndStatusAndDeletedAtIsNull(
            UUID usuarioId,
            StatusHabito status,
            Pageable pageable
    );

    Page<Habito> findByUsuarioId(UUID usuarioId, Pageable pageable);

    Page<Habito> findByUsuarioIdAndStatus(UUID usuarioId, StatusHabito status, Pageable pageable);

    Page<Habito> findByUsuarioIdAndDeletedAtIsNotNull(UUID usuarioId, Pageable pageable);

    Page<Habito> findByUsuarioIdAndStatusAndDeletedAtIsNotNull(
            UUID usuarioId,
            StatusHabito status,
            Pageable pageable
    );
}
