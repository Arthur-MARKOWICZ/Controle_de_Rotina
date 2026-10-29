package com.controlerotina.config.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IpRateLimiterTest {

    private static final Instant INICIO = Instant.parse("2026-10-29T12:00:00Z");

    private MutableClock clock;
    private IpRateLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(INICIO);
        limiter = new IpRateLimiter(5, Duration.ofMinutes(1), clock);
    }

    @Test
    void devePermitirAteCincoRequestsNaMesmaJanela() {
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire("10.0.0.1")).isTrue();
        }
    }

    @Test
    void deveBloquearSextaRequestNaMesmaJanela() {
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire("10.0.0.1");
        }

        assertThat(limiter.tryAcquire("10.0.0.1")).isFalse();
    }

    @Test
    void deveLiberarNovamenteAposJanelaDeUmMinuto() {
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire("10.0.0.1");
        }
        assertThat(limiter.tryAcquire("10.0.0.1")).isFalse();

        clock.advance(Duration.ofMinutes(1));

        assertThat(limiter.tryAcquire("10.0.0.1")).isTrue();
    }

    @Test
    void deveIsolarContadoresPorIp() {
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire("10.0.0.1")).isTrue();
        }
        assertThat(limiter.tryAcquire("10.0.0.1")).isFalse();

        assertThat(limiter.tryAcquire("10.0.0.2")).isTrue();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
