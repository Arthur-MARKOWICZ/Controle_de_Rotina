package com.controlerotina.auth.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.controlerotina.auth.entity.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByUsuarioIdAndRevokedAtIsNull(UUID usuarioId);

    @Modifying(clearAutomatically = true)
    @Query("""
            delete from RefreshToken r
            where (r.expiresAt < :limite)
               or (r.revokedAt is not null and r.revokedAt < :limite)
            """)
    int deleteExpiradosOuRevogadosAntesDe(@Param("limite") Instant limite);
}
