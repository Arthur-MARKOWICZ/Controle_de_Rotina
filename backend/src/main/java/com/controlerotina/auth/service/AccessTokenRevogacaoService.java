package com.controlerotina.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Service
public class AccessTokenRevogacaoService {

    public static final String CACHE_ACCESS_TOKEN_REVOGADOS = "accessTokenRevogados";

    private final Cache cache;
    private final Clock clock;

    public AccessTokenRevogacaoService(CacheManager cacheManager, Clock clock) {
        this.cache = Objects.requireNonNull(
                cacheManager.getCache(CACHE_ACCESS_TOKEN_REVOGADOS),
                "Cache " + CACHE_ACCESS_TOKEN_REVOGADOS + " não configurado"
        );
        this.clock = clock;
    }

    public void revogar(String jti, Instant expiraEm) {
        if (jti == null || jti.isBlank() || expiraEm == null) {
            return;
        }
        if (!expiraEm.isAfter(clock.instant())) {
            return;
        }
        cache.put(jti, expiraEm);
    }

    public boolean estaRevogado(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        Cache.ValueWrapper wrapper = cache.get(jti);
        if (wrapper == null || wrapper.get() == null) {
            return false;
        }
        Instant expiraEm = (Instant) wrapper.get();
        if (!expiraEm.isAfter(clock.instant())) {
            cache.evict(jti);
            return false;
        }
        return true;
    }
}
