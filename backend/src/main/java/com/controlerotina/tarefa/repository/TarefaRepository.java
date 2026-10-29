package com.controlerotina.tarefa.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
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

    List<Tarefa> findByUsuarioIdAndDeletedAtIsNullAndStatusIn(
            UUID usuarioId,
            Collection<StatusTarefa> status
    );

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
            """)
    int atualizarStatusQuandoPrazoPassou(
            @Param("statusAtual") StatusTarefa statusAtual,
            @Param("novoStatus") StatusTarefa novoStatus,
            @Param("inicioDoDia") Instant inicioDoDia,
            @Param("agora") Instant agora
    );
}
