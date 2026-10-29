package com.controlerotina.shared.lock;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PostgresAdvisoryLockIT {

    private static final long CHAVE_TESTE = 9_001_902L;

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    PostgresAdvisoryLock advisoryLock;

    @Test
    void segundaSessaoNaoExecutaEnquantoPrimeiraSeguraOLock() throws Exception {
        CountDownLatch dentroDoLock = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        AtomicInteger execucoes = new AtomicInteger();

        Thread detentora = new Thread(() -> advisoryLock.tryWithLock(CHAVE_TESTE, () -> {
            execucoes.incrementAndGet();
            dentroDoLock.countDown();
            try {
                assertThat(liberar.await(5, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        detentora.start();
        assertThat(dentroDoLock.await(5, TimeUnit.SECONDS)).isTrue();

        boolean segundaExecutou = advisoryLock.tryWithLock(CHAVE_TESTE, execucoes::incrementAndGet);
        assertThat(segundaExecutou).isFalse();
        assertThat(execucoes.get()).isEqualTo(1);

        liberar.countDown();
        detentora.join(5_000);

        boolean aposLiberar = advisoryLock.tryWithLock(CHAVE_TESTE, execucoes::incrementAndGet);
        assertThat(aposLiberar).isTrue();
        assertThat(execucoes.get()).isEqualTo(2);
    }
}
