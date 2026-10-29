package com.controlerotina.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

class AccessTokenRevogacaoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-10-29T12:00:00Z");

    private AccessTokenRevogacaoService service;

    @BeforeEach
    void setUp() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(
                AccessTokenRevogacaoService.CACHE_ACCESS_TOKEN_REVOGADOS
        );
        service = new AccessTokenRevogacaoService(cacheManager, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @Test
    void deveMarcarJtiComoRevogado() {
        assertThat(service.estaRevogado("jti-1")).isFalse();

        service.revogar("jti-1", AGORA.plusSeconds(900));

        assertThat(service.estaRevogado("jti-1")).isTrue();
    }

    @Test
    void naoDeveConsiderarRevogadoAposExpiracao() {
        service.revogar("jti-expirado", AGORA.minusSeconds(1));

        assertThat(service.estaRevogado("jti-expirado")).isFalse();
    }
}
