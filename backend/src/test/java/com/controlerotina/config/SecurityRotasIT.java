package com.controlerotina.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class SecurityRotasIT {

    private static final String SENHA = "Senha@123";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void healthDeveSerPublico() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/actuator/health", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("UP");
    }

    @Test
    void cadastroDeveSerPublico() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/usuarios",
                json(Map.of(
                        "nome", "Seg",
                        "email", "seg-" + UUID.randomUUID() + "@email.com",
                        "senha", SENHA
                )),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(CREATED);
    }

    @Test
    void loginDeveSerPublico() {
        String email = "seg-login-" + UUID.randomUUID() + "@email.com";
        restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Seg", "email", email, "senha", SENHA)),
                Map.class);
        ResponseEntity<Map> login = restTemplate.postForEntity(
                "/auth/login",
                json(Map.of("email", email, "senha", SENHA)),
                Map.class);
        assertThat(login.getStatusCode()).isEqualTo(OK);
    }

    @Test
    void rotaProtegidaSemTokenDeveRetornar401ComTitle() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/tarefas", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("title")).isEqualTo("Não autorizado");
        assertThat(response.getBody().get("status")).isEqualTo(401);
    }

    @Test
    void rotaProtegidaComTokenInvalidoDeveRetornar401SemAlterarRecurso() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("token.invalido");
        ResponseEntity<Map> response = restTemplate.exchange(
                "/me",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("title")).isEqualTo("Não autorizado");
    }

    @Test
    void getEmLoginNaoDeveAutenticar() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/auth/login", Map.class);
        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    void optionsEmRotaProtegidaNaoDeveExporCorsPermissivo() {
        ResponseEntity<Void> response = restTemplate.exchange(
                "/tarefas",
                HttpMethod.OPTIONS,
                new HttpEntity<>(new HttpHeaders()),
                Void.class);
        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isNull();
        assertThat(response.getHeaders().getAccessControlAllowMethods()).isNullOrEmpty();
    }

    @Test
    void originEstranhaNaoDeveReceberAllowOrigin() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ORIGIN, "https://evil.example");
        ResponseEntity<Map> response = restTemplate.exchange(
                "/actuator/health",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isNull();
    }

    private static HttpEntity<Map<String, String>> json(Map<String, String> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
