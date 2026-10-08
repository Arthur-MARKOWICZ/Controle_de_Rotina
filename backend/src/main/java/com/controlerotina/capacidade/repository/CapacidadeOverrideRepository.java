package com.controlerotina.capacidade.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.controlerotina.capacidade.entity.CapacidadeOverride;

public interface CapacidadeOverrideRepository extends JpaRepository<CapacidadeOverride, UUID> {

    Optional<CapacidadeOverride> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    boolean existsByUsuarioIdAndDataAndAtivoTrue(UUID usuarioId, LocalDate data);

    boolean existsByUsuarioIdAndDataAndAtivoTrueAndIdNot(UUID usuarioId, LocalDate data, UUID id);

    List<CapacidadeOverride> findByUsuarioIdAndAtivoTrueOrderByDataAsc(UUID usuarioId);

    List<CapacidadeOverride> findByUsuarioIdOrderByDataAsc(UUID usuarioId);

    List<CapacidadeOverride> findByUsuarioIdAndAtivoFalseOrderByDataAsc(UUID usuarioId);

    List<CapacidadeOverride> findByUsuarioIdAndAtivoTrueAndDataBetweenOrderByDataAsc(
            UUID usuarioId,
            LocalDate dataDe,
            LocalDate dataAte
    );
}