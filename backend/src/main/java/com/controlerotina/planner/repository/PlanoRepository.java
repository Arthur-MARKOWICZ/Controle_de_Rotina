package com.controlerotina.planner.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.controlerotina.planner.entity.Plano;
import com.controlerotina.planner.entity.StatusPlano;

public interface PlanoRepository extends JpaRepository<Plano, UUID> {

    Optional<Plano> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    Optional<Plano> findByUsuarioIdAndDataAndVersao(UUID usuarioId, LocalDate data, int versao);

    Optional<Plano> findFirstByUsuarioIdAndDataOrderByVersaoDesc(UUID usuarioId, LocalDate data);

    Optional<Plano> findFirstByUsuarioIdAndDataAndStatusInOrderByVersaoDesc(
            UUID usuarioId,
            LocalDate data,
            Collection<StatusPlano> status
    );

    Optional<Plano> findFirstByUsuarioIdAndDataAndStatusOrderByVersaoDesc(
            UUID usuarioId,
            LocalDate data,
            StatusPlano status
    );

    List<Plano> findByUsuarioIdAndPropostaIdAndStatus(
            UUID usuarioId,
            UUID propostaId,
            StatusPlano status
    );

    boolean existsByUsuarioIdAndDataAndStatus(UUID usuarioId, LocalDate data, StatusPlano status);

    @Query("""
            SELECT COALESCE(MAX(p.versao), 0)
              FROM Plano p
             WHERE p.usuarioId = :usuarioId
               AND p.data = :data
            """)
    int findMaxVersaoByUsuarioIdAndData(
            @Param("usuarioId") UUID usuarioId,
            @Param("data") LocalDate data
    );
}
