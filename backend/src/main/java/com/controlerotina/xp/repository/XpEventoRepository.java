package com.controlerotina.xp.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.controlerotina.xp.entity.TipoXpEvento;
import com.controlerotina.xp.entity.XpEvento;

public interface XpEventoRepository extends JpaRepository<XpEvento, UUID> {

    boolean existsByTipoAndOrigemId(TipoXpEvento tipo, UUID origemId);

    @Query("""
            SELECT COUNT(e)
              FROM XpEvento e
             WHERE e.usuarioId = :usuarioId
               AND e.tipo = :tipo
               AND e.createdAt >= :inicio
               AND e.createdAt < :fim
            """)
    long contarPorTipoNoPeriodo(
            @Param("usuarioId") UUID usuarioId,
            @Param("tipo") TipoXpEvento tipo,
            @Param("inicio") Instant inicio,
            @Param("fim") Instant fim
    );

    @Query("""
            SELECT COALESCE(SUM(e.quantidade), 0)
              FROM XpEvento e
             WHERE e.usuarioId = :usuarioId
               AND e.createdAt >= :inicio
               AND e.createdAt < :fim
            """)
    long somarQuantidadeNoPeriodo(
            @Param("usuarioId") UUID usuarioId,
            @Param("inicio") Instant inicio,
            @Param("fim") Instant fim
    );
}
