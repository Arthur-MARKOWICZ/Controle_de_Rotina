package com.controlerotina.decidir.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.controlerotina.decidir.entity.ItemDecidir;

public interface ItemDecidirRepository
        extends JpaRepository<ItemDecidir, UUID>, JpaSpecificationExecutor<ItemDecidir> {

    Optional<ItemDecidir> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ItemDecidir i
            set i.tarefaId = null, i.updatedAt = :updatedAt
            where i.tarefaId = :tarefaId and i.usuarioId = :usuarioId
            """)
    int reabrirPorTarefa(
            @Param("usuarioId") UUID usuarioId,
            @Param("tarefaId") UUID tarefaId,
            @Param("updatedAt") Instant updatedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ItemDecidir i
            set i.projetoId = null, i.updatedAt = :updatedAt
            where i.projetoId = :projetoId
            """)
    int zerarProjetoId(
            @Param("projetoId") UUID projetoId,
            @Param("updatedAt") Instant updatedAt
    );
}
