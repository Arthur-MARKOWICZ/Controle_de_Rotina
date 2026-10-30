package com.controlerotina.capacidade.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.controlerotina.capacidade.entity.CapacidadeSemanal;
import com.controlerotina.capacidade.entity.DiaSemana;

public interface CapacidadeSemanalRepository extends JpaRepository<CapacidadeSemanal, UUID> {

    Optional<CapacidadeSemanal> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    boolean existsByUsuarioIdAndDiaSemanaAndAtivoTrue(UUID usuarioId, DiaSemana diaSemana);

    boolean existsByUsuarioIdAndDiaSemanaAndAtivoTrueAndIdNot(UUID usuarioId, DiaSemana diaSemana, UUID id);

    Optional<CapacidadeSemanal> findByUsuarioIdAndDiaSemanaAndAtivoTrue(UUID usuarioId, DiaSemana diaSemana);

    List<CapacidadeSemanal> findByUsuarioIdAndAtivoTrueOrderByDiaSemanaAsc(UUID usuarioId);

    List<CapacidadeSemanal> findByUsuarioIdOrderByDiaSemanaAsc(UUID usuarioId);

    List<CapacidadeSemanal> findByUsuarioIdAndAtivoFalseOrderByDiaSemanaAsc(UUID usuarioId);
}
