package com.controlerotina.objetivo.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.controlerotina.objetivo.entity.Objetivo;

public interface ObjetivoRepository extends JpaRepository<Objetivo, UUID>, JpaSpecificationExecutor<Objetivo> {

    Optional<Objetivo> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    List<Objetivo> findByUsuarioIdAndIdIn(UUID usuarioId, Collection<UUID> ids);
}
