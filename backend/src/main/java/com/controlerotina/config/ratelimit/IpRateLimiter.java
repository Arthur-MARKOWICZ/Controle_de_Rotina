package com.controlerotina.config.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Contador por chave (IP) em janela fixa, sem cache distribuído.
 * Estado fica só em memória do processo — adequado a instância única / MVP.
 */
final class IpRateLimiter {

    private final int maxRequests;
    private final long windowMillis;
    private final Clock clock;
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

    IpRateLimiter(int maxRequests, Duration window, Clock clock) {
        if (maxRequests < 1) {
            throw new IllegalArgumentException("maxRequests deve ser >= 1");
        }
        this.maxRequests = maxRequests;
        this.windowMillis = window.toMillis();
        this.clock = clock;
    }

    boolean tryAcquire(String key) {
        long now = clock.millis();
        boolean[] allowed = {false};
        windows.compute(key, (ignored, current) -> {
            if (current == null || now - current.windowStartMillis >= windowMillis) {
                allowed[0] = true;
                return new Window(now, 1);
            }
            if (current.count >= maxRequests) {
                allowed[0] = false;
                return current;
            }
            allowed[0] = true;
            return new Window(current.windowStartMillis, current.count + 1);
        });
        return allowed[0];
    }

    private record Window(long windowStartMillis, int count) {
    }
}
