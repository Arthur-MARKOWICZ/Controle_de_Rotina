package com.controlerotina.tarefa.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;

public interface TarefaRepository extends JpaRepository<Tarefa, UUID>, JpaSpecificationExecutor<Tarefa> {

    Optional<Tarefa> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    boolean existsByParentIdAndDeletedAtIsNull(UUID parentId);

    List<Tarefa> findByParentIdAndDeletedAtIsNull(UUID parentId);

    @Query("""
            SELECT COALESCE(MAX(t.ordem), -1)
              FROM Tarefa t
             WHERE t.usuarioId = :usuarioId
               AND t.deletedAt IS NULL
               AND ((:parentId IS NULL AND t.parentId IS NULL) OR t.parentId = :parentId)
            """)
    int findMaxOrdemIrmaos(@Param("usuarioId") UUID usuarioId, @Param("parentId") UUID parentId);

    @Query("""
            SELECT t FROM Tarefa t
             WHERE t.usuarioId = :usuarioId
               AND ((:parentId IS NULL AND t.parentId IS NULL) OR t.parentId = :parentId)
               AND (
                     (:deletadas = true AND t.deletedAt IS NOT NULL)
                     OR
                     (:deletadas = false AND t.deletedAt IS NULL)
                   )
             ORDER BY t.ordem ASC, t.createdAt ASC
            """)
    List<Tarefa> findIrmaosOrdenados(
            @Param("usuarioId") UUID usuarioId,
            @Param("parentId") UUID parentId,
            @Param("deletadas") boolean deletadas
    );

    List<Tarefa> findByParentIdAndDeletedAtIsNotNull(UUID parentId);

    boolean existsByParentIdAndDeletedAtIsNullAndStatusNot(UUID parentId, StatusTarefa status);

    @Query("""
            SELECT COALESCE(SUM(t.duracaoMinutos), 0)
              FROM Tarefa t
             WHERE t.parentId = :parentId
               AND t.deletedAt IS NULL
            """)
    long somarDuracaoFilhosNaoDeletados(@Param("parentId") UUID parentId);

    @Query("""
            SELECT DISTINCT t.parentId
              FROM Tarefa t
             WHERE t.parentId IN :ids
               AND t.deletedAt IS NULL
            """)
    Set<UUID> findParentIdsComFilhosNaoDeletados(@Param("ids") Collection<UUID> ids);

    List<Tarefa> findByUsuarioIdAndDeletedAtIsNullAndStatusIn(
            UUID usuarioId,
            Collection<StatusTarefa> status
    );

    List<Tarefa> findByProjetoIdAndDeletedAtIsNull(UUID projetoId);

    List<Tarefa> findByProjetoIdInAndDeletedAtIsNull(Collection<UUID> projetoIds);

    @Query("""
            SELECT t FROM Tarefa t
             WHERE t.usuarioId = :usuarioId
               AND t.deletedAt IS NULL
               AND t.status <> :finalizada
               AND t.prazo IS NOT NULL
               AND t.prazo >= :inicio
               AND t.prazo < :fim
             ORDER BY t.prazo ASC, t.titulo ASC
            """)
    List<Tarefa> findAtivasComPrazoNoPeriodo(
            @Param("usuarioId") UUID usuarioId,
            @Param("finalizada") StatusTarefa finalizada,
            @Param("inicio") Instant inicio,
            @Param("fim") Instant fim
    );

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Tarefa t
               SET t.status = :novoStatus, t.updatedAt = :agora
             WHERE t.status = :statusAtual
               AND t.prazo < :inicioDoDia
               AND t.deletedAt IS NULL
               AND NOT EXISTS (
                     SELECT 1 FROM Tarefa f
                      WHERE f.parentId = t.id
                        AND f.deletedAt IS NULL
                   )
            """)
    int atualizarStatusQuandoPrazoPassou(
            @Param("statusAtual") StatusTarefa statusAtual,
            @Param("novoStatus") StatusTarefa novoStatus,
            @Param("inicioDoDia") Instant inicioDoDia,
            @Param("agora") Instant agora
    );
}
