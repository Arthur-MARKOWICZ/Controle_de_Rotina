package com.controlerotina.projeto.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.controlerotina.capacidade.entity.DiaSemana;
import com.controlerotina.projeto.entity.ReservaProjetoSemanal;

public interface ReservaProjetoSemanalRepository extends JpaRepository<ReservaProjetoSemanal, UUID> {

    Optional<ReservaProjetoSemanal> findByIdAndUsuarioIdAndProjetoId(UUID id, UUID usuarioId, UUID projetoId);

    List<ReservaProjetoSemanal> findByUsuarioIdAndProjetoIdOrderByDiaSemanaAsc(UUID usuarioId, UUID projetoId);

    List<ReservaProjetoSemanal> findByUsuarioId(UUID usuarioId);

    List<ReservaProjetoSemanal> findByUsuarioIdAndDiaSemana(UUID usuarioId, DiaSemana diaSemana);

    boolean existsByUsuarioIdAndProjetoIdAndDiaSemana(UUID usuarioId, UUID projetoId, DiaSemana diaSemana);

    boolean existsByUsuarioIdAndProjetoIdAndDiaSemanaAndIdNot(
            UUID usuarioId,
            UUID projetoId,
            DiaSemana diaSemana,
            UUID id
    );
}
