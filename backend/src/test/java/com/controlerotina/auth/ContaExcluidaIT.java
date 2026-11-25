package com.controlerotina.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.controlerotina.auth.jwt.JwtAuthenticationFilter;

/** Dois "dispositivos" (dois logins, dois jti) da mesma conta; o primeiro exclui a conta. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class ContaExcluidaIT {

    private static final String SENHA = "Senha@123";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    JdbcTemplate jdbc;

    private String dispositivo1;
    private String dispositivo2;

    @BeforeEach
    void cadastrarELogarDuasVezes() {
        String email = "conta-excluida-" + UUID.randomUUID() + "@email.com";
        assertThat(restTemplate.postForEntity("/usuarios",
                json(Map.of("nome", "Ana", "email", email, "senha", SENHA), null), Map.class)
                .getStatusCode()).isEqualTo(CREATED);
        dispositivo1 = login(email);
        dispositivo2 = login(email);
    }

    @Test
    void contaExistenteContinuaAutenticandoNormalmente() {
        ResponseEntity<Map> response = chamar(HttpMethod.GET, "/me", dispositivo2, null);

        assertThat(response.getStatusCode()).isEqualTo(OK);
    }

    @Test
    void outroDispositivoDeveReceber401ContaExcluidaEmLeitura() {
        excluirConta();

        ResponseEntity<Map> response = chamar(HttpMethod.GET, "/tarefas", dispositivo2, null);

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType().toString()).contains("application/problem+json");
        assertThat(response.getBody())
                .containsEntry("title", JwtAuthenticationFilter.TITULO_CONTA_EXCLUIDA)
                .containsEntry("detail", JwtAuthenticationFilter.DETALHE_CONTA_EXCLUIDA);
        // Os clientes comparam com este texto literal: mudar aqui quebra web, Android e MCP.
        assertThat(JwtAuthenticationFilter.TITULO_CONTA_EXCLUIDA).isEqualTo("Conta excluída");
    }

    @Test
    void escritaComTokenDeContaExcluidaDeveRetornar401SemInserirNada() {
        excluirConta();
        long tarefasAntes = jdbc.queryForObject("SELECT count(*) FROM tarefa", Long.class);

        ResponseEntity<Map> response = chamar(HttpMethod.POST, "/tarefas", dispositivo2,
                Map.of("titulo", "Nova", "duracaoMinutos", 30, "prioridade", "MEDIA"));

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("title", JwtAuthenticationFilter.TITULO_CONTA_EXCLUIDA);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tarefa", Long.class)).isEqualTo(tarefasAntes);
    }

    @Test
    void tokenDoProprioDispositivoQueExcluiuDeveRetornar401() {
        excluirConta();

        ResponseEntity<Map> response = chamar(HttpMethod.GET, "/me", dispositivo1, null);

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
    }

    @Test
    void tokenRevogadoPorLogoutDeveSerNaoAutorizadoENaoContaExcluida() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(dispositivo2);
        restTemplate.exchange("/auth/logout", HttpMethod.POST, new HttpEntity<>(headers), Void.class);

        ResponseEntity<Map> response = chamar(HttpMethod.GET, "/me", dispositivo2, null);

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("title", "Não autorizado");
    }

    @Test
    void tokenComAssinaturaInvalidaDeveSerNaoAutorizado() {
        ResponseEntity<Map> response = chamar(HttpMethod.GET, "/me", dispositivo2 + "x", null);

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("title", "Não autorizado");
    }

    @Test
    void semTokenDeveSerNaoAutorizado() {
        ResponseEntity<Map> response = chamar(HttpMethod.GET, "/me", null, null);

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("title", "Não autorizado");
    }

    private void excluirConta() {
        assertThat(chamar(HttpMethod.DELETE, "/me", dispositivo1, Map.of("senhaAtual", SENHA)).getStatusCode())
                .isEqualTo(NO_CONTENT);
    }

    private String login(String email) {
        ResponseEntity<Map> login = restTemplate.postForEntity(
                "/auth/login", json(Map.of("email", email, "senha", SENHA), null), Map.class);
        assertThat(login.getStatusCode()).isEqualTo(OK);
        return login.getBody().get("accessToken").toString();
    }

    private ResponseEntity<Map> chamar(HttpMethod method, String path, String token, Map<String, ?> corpo) {
        return restTemplate.exchange(path, method, json(corpo, token), Map.class);
    }

    private static HttpEntity<Map<String, ?>> json(Map<String, ?> body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return new HttpEntity<>(body, headers);
    }
}
