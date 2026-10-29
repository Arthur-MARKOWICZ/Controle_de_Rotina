package com.controlerotina.execucao.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.controlerotina.execucao.entity.Execucao;
import com.controlerotina.execucao.entity.StatusExecucao;

public interface ExecucaoRepository extends JpaRepository<Execucao, UUID> {

    Optional<Execucao> findByBlocoPlanoId(UUID blocoPlanoId);

    Optional<Execucao> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    boolean existsByBlocoPlanoId(UUID blocoPlanoId);

    List<Execucao> findByUsuarioIdAndBlocoPlanoIdIn(UUID usuarioId, Collection<UUID> blocoPlanoIds);

    @Query("""
            SELECT COALESCE(SUM(b.duracaoMinutos), 0)
              FROM Execucao e, BlocoPlano b
             WHERE e.blocoPlanoId = b.id
               AND b.tarefaId = :tarefaId
               AND e.usuarioId = :usuarioId
               AND e.status = :status
            """)
    long somarDuracaoMinutosPorTarefaEStatus(
            @Param("tarefaId") UUID tarefaId,
            @Param("usuarioId") UUID usuarioId,
            @Param("status") StatusExecucao status
    );

    @Query("""
            SELECT e, b.duracaoMinutos
              FROM Execucao e, BlocoPlano b
             WHERE e.blocoPlanoId = b.id
               AND b.tarefaId = :tarefaId
               AND e.usuarioId = :usuarioId
               AND e.status = :status
            """)
    List<Object[]> listarConcluidasComDuracaoBlocoPorTarefa(
            @Param("tarefaId") UUID tarefaId,
            @Param("usuarioId") UUID usuarioId,
            @Param("status") StatusExecucao status
    );

    @Query("""
            SELECT COUNT(e)
              FROM Execucao e
             WHERE e.usuarioId = :usuarioId
               AND e.status = :status
               AND e.concluidoEm >= :inicio
               AND e.concluidoEm < :fim
            """)
    long contarConcluidasNoPeriodo(
            @Param("usuarioId") UUID usuarioId,
            @Param("status") StatusExecucao status,
            @Param("inicio") Instant inicio,
            @Param("fim") Instant fim
    );

    @Query("""
            SELECT COALESCE(SUM(b.duracaoMinutos), 0)
              FROM Execucao e, BlocoPlano b
             WHERE e.blocoPlanoId = b.id
               AND e.usuarioId = :usuarioId
               AND e.status = :status
               AND e.concluidoEm >= :inicio
               AND e.concluidoEm < :fim
            """)
    long somarDuracaoMinutosConcluidasNoPeriodo(
            @Param("usuarioId") UUID usuarioId,
            @Param("status") StatusExecucao status,
            @Param("inicio") Instant inicio,
            @Param("fim") Instant fim
    );
}
