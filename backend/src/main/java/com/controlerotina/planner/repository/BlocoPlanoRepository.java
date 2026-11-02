package com.controlerotina.planner.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.controlerotina.planner.entity.BlocoPlano;

public interface BlocoPlanoRepository extends JpaRepository<BlocoPlano, UUID> {

    List<BlocoPlano> findByPlanoIdOrderByOrdemAsc(UUID planoId);

    List<BlocoPlano> findByPlanoIdInOrderByOrdemAsc(Collection<UUID> planoIds);

    Optional<BlocoPlano> findByIdAndPlanoId(UUID id, UUID planoId);

    boolean existsByPlanoIdAndTarefaId(UUID planoId, UUID tarefaId);

    boolean existsByPlanoIdAndHabitoId(UUID planoId, UUID habitoId);

    Optional<BlocoPlano> findByPlanoIdAndHabitoId(UUID planoId, UUID habitoId);

    @Query("""
            SELECT COALESCE(MAX(b.ordem), 0)
              FROM BlocoPlano b
             WHERE b.planoId = :planoId
            """)
    int findMaxOrdemByPlanoId(@Param("planoId") UUID planoId);
}
