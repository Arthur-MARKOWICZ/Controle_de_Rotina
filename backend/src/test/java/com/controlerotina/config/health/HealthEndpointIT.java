package com.controlerotina.config.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

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
class HealthEndpointIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void deveRetornarUpComDbQuandoPostgresAcessivel() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/actuator/health", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("UP");

        @SuppressWarnings("unchecked")
        Map<String, Object> components = (Map<String, Object>) response.getBody().get("components");
        assertThat(components).as("components in %s", response.getBody()).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> db = (Map<String, Object>) components.get("db");
        assertThat(db).isNotNull();
        assertThat(db.get("status")).isEqualTo("UP");
        assertThat(db.get("details")).isNull();
    }

    @Test
    void naoDeveExporEndpointSensivelDoActuatorSemAutenticacao() {
        ResponseEntity<Map> env = restTemplate.getForEntity("/actuator/env", Map.class);

        assertThat(env.getStatusCode()).isEqualTo(UNAUTHORIZED);
    }
}
