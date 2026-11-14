package com.controlerotina.execucao.repository;

import java.time.Instant;
import java.time.LocalDate;
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
            SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END
              FROM Execucao e, BlocoPlano b
             WHERE e.blocoPlanoId = b.id
               AND b.tarefaId = :tarefaId
               AND e.status = :status
            """)
    boolean existsPorTarefaEStatus(
            @Param("tarefaId") UUID tarefaId,
            @Param("status") StatusExecucao status
    );

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
            SELECT b.tarefaId, COALESCE(SUM(b.duracaoMinutos), 0)
              FROM Execucao e, BlocoPlano b
             WHERE e.blocoPlanoId = b.id
               AND b.tarefaId IN :tarefaIds
               AND e.usuarioId = :usuarioId
               AND e.status = :status
             GROUP BY b.tarefaId
            """)
    List<Object[]> somarDuracaoMinutosPorTarefasEStatus(
            @Param("tarefaIds") Collection<UUID> tarefaIds,
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

    /**
     * Amostras para calibração: [iniciadoEm, concluidoEm, duracaoMinutos do bloco, tarefaId, projetoId].
     * Só blocos de tarefa (o join com Tarefa exclui hábitos).
     */
    @Query("""
            SELECT e.iniciadoEm, e.concluidoEm, b.duracaoMinutos, t.id, t.projetoId
              FROM Execucao e, BlocoPlano b, Tarefa t
             WHERE e.blocoPlanoId = b.id
               AND b.tarefaId = t.id
               AND e.usuarioId = :usuarioId
               AND e.status = :status
               AND e.iniciadoEm IS NOT NULL
               AND e.concluidoEm >= :inicio
               AND e.concluidoEm < :fim
            """)
    List<Object[]> listarAmostrasCalibracao(
            @Param("usuarioId") UUID usuarioId,
            @Param("status") StatusExecucao status,
            @Param("inicio") Instant inicio,
            @Param("fim") Instant fim
    );

    @Query("""
            SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END
              FROM Execucao e, BlocoPlano b, Plano p
             WHERE e.blocoPlanoId = b.id
               AND b.planoId = p.id
               AND b.habitoId = :habitoId
               AND p.data = :data
               AND e.status = :status
            """)
    boolean existsCumprimentoHabitoNaData(
            @Param("habitoId") UUID habitoId,
            @Param("data") LocalDate data,
            @Param("status") StatusExecucao status
    );

    @Query("""
            SELECT DISTINCT b.habitoId, p.data
              FROM Execucao e, BlocoPlano b, Plano p
             WHERE e.blocoPlanoId = b.id
               AND b.planoId = p.id
               AND b.habitoId IN :habitoIds
               AND p.data IN :datas
               AND e.status = :status
            """)
    List<Object[]> listarCumprimentosHabitoNasDatas(
            @Param("habitoIds") Collection<UUID> habitoIds,
            @Param("datas") Collection<LocalDate> datas,
            @Param("status") StatusExecucao status
    );

    @Query("""
            SELECT new com.controlerotina.execucao.repository.CumprimentoHabitoLinha(
                       p.data, b.duracaoMinutos, e.iniciadoEm, e.concluidoEm, e.minutosInformados)
              FROM Execucao e, BlocoPlano b, Plano p
             WHERE e.blocoPlanoId = b.id
               AND b.planoId = p.id
               AND b.habitoId = :habitoId
               AND e.usuarioId = :usuarioId
               AND e.status = :status
               AND p.data BETWEEN :dataDe AND :dataAte
             ORDER BY p.data DESC
            """)
    List<CumprimentoHabitoLinha> listarCumprimentosHabitoNoIntervalo(
            @Param("usuarioId") UUID usuarioId,
            @Param("habitoId") UUID habitoId,
            @Param("dataDe") LocalDate dataDe,
            @Param("dataAte") LocalDate dataAte,
            @Param("status") StatusExecucao status
    );
}
