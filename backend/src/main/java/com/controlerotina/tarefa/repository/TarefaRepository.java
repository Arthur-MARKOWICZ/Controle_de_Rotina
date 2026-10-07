package com.controlerotina.tarefa.repository;

import java.time.Instant;
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

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Tarefa t
               SET t.status = :novoStatus, t.updatedAt = :agora
             WHERE t.status = :statusAtual
               AND t.prazo < :agora
               AND t.deletedAt IS NULL
            """)
    int atualizarStatusQuandoPrazoPassou(
            @Param("statusAtual") StatusTarefa statusAtual,
            @Param("novoStatus") StatusTarefa novoStatus,
            @Param("agora") Instant agora
    );

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Tarefa t
               SET t.status = :novoStatus, t.updatedAt = :agora
             WHERE t.status = :statusAtual
               AND t.prazo >= :agora
               AND t.deletedAt IS NULL
            """)
    int atualizarStatusQuandoPrazoNaoPassou(
            @Param("statusAtual") StatusTarefa statusAtual,
            @Param("novoStatus") StatusTarefa novoStatus,
            @Param("agora") Instant agora
    );
}
