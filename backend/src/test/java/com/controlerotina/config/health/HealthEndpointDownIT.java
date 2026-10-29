package com.controlerotina.config.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.ResponseEntity;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class HealthEndpointDownIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void deveRetornarDownQuandoPostgresFicaInacessivel() {
        ResponseEntity<Map> up = restTemplate.getForEntity("/actuator/health", Map.class);
        assertThat(up.getStatusCode()).isEqualTo(OK);
        assertThat(up.getBody()).isNotNull();
        assertThat(up.getBody().get("status")).isEqualTo("UP");

        postgres.stop();

        ResponseEntity<Map> down = awaitHealthDown();

        assertThat(down.getStatusCode()).isEqualTo(SERVICE_UNAVAILABLE);
        assertThat(down.getBody()).isNotNull();
        assertThat(down.getBody().get("status")).isEqualTo("DOWN");
    }

    private ResponseEntity<Map> awaitHealthDown() {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        ResponseEntity<Map> last = null;
        while (System.nanoTime() < deadline) {
            last = restTemplate.getForEntity("/actuator/health", Map.class);
            if (last.getStatusCode().isSameCodeAs(SERVICE_UNAVAILABLE)
                    && last.getBody() != null
                    && "DOWN".equals(last.getBody().get("status"))) {
                return last;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrompido aguardando health DOWN", e);
            }
        }
        return last;
    }
}
