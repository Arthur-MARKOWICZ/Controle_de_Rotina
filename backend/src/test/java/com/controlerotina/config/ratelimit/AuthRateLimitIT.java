package com.controlerotina.config.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.rate-limit.enabled=true",
                "app.rate-limit.requests-per-minute=5"
        }
)
@AutoConfigureTestRestTemplate
@Testcontainers
class AuthRateLimitIT {

    private static final String SENHA = "Senha@123";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void devePermitirUsoNormalEBloquearRajadaDeLogin() {
        String ip = "203.0.113.10";
        String email = "rate-" + UUID.randomUUID() + "@email.com";

        assertThat(restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Usuario Rate", "email", email, "senha", SENHA), ip),
                Map.class
        ).getStatusCode()).isEqualTo(CREATED);

        for (int i = 0; i < 4; i++) {
            ResponseEntity<Map> login = restTemplate.postForEntity(
                    "/auth/login",
                    json(Map.of("email", email, "senha", SENHA), ip),
                    Map.class
            );
            assertThat(login.getStatusCode()).isEqualTo(OK);
        }

        ResponseEntity<Map> bloqueado = restTemplate.postForEntity(
                "/auth/login",
                json(Map.of("email", email, "senha", "SenhaErrada@1"), ip),
                Map.class
        );
        assertThat(bloqueado.getStatusCode()).isEqualTo(TOO_MANY_REQUESTS);
        assertThat(bloqueado.getBody()).isNotNull();
        assertThat(bloqueado.getBody().get("status")).isEqualTo(429);
        assertThat(bloqueado.getBody().get("title").toString()).containsIgnoringCase("muitas");
    }

    @Test
    void deveBloquearRajadaDeCadastro() {
        String ip = "203.0.113.20";

        for (int i = 0; i < 5; i++) {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "/usuarios",
                    json(Map.of(
                            "nome", "Usuario " + i,
                            "email", "cad-" + i + "-" + UUID.randomUUID() + "@email.com",
                            "senha", SENHA
                    ), ip),
                    Map.class
            );
            assertThat(response.getStatusCode()).isEqualTo(CREATED);
        }

        ResponseEntity<Map> bloqueado = restTemplate.postForEntity(
                "/usuarios",
                json(Map.of(
                        "nome", "Usuario Extra",
                        "email", "cad-extra-" + UUID.randomUUID() + "@email.com",
                        "senha", SENHA
                ), ip),
                Map.class
        );
        assertThat(bloqueado.getStatusCode()).isEqualTo(TOO_MANY_REQUESTS);
    }

    private static HttpEntity<Map<String, String>> json(Map<String, String> body, String forwardedFor) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Forwarded-For", forwardedFor);
        return new HttpEntity<>(body, headers);
    }
}
