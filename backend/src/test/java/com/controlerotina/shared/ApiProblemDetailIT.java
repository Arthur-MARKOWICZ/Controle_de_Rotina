package com.controlerotina.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.util.List;
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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class ApiProblemDetailIT {

    private static final String SENHA = "Senha@123";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private String accessToken;

    @BeforeEach
    void cadastrarELogar() {
        String email = "problem-" + UUID.randomUUID() + "@email.com";
        assertThat(restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Problem Detail", "email", email, "senha", SENHA)),
                Map.class).getStatusCode()).isEqualTo(CREATED);

        ResponseEntity<Map> login = restTemplate.postForEntity(
                "/auth/login",
                json(Map.of("email", email, "senha", SENHA)),
                Map.class);
        assertThat(login.getStatusCode()).isEqualTo(OK);
        accessToken = login.getBody().get("accessToken").toString();
    }

    @Test
    void t1_bodyInvalidoDeveRetornarProblemDetail400ComErrors() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/capacidades/semanal",
                HttpMethod.POST,
                autenticadoJson(Map.of("diaSemana", "SEGUNDA", "horas", -1)),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
        JsonNode body = lerJson(response.getBody());
        assertProblemDetail(body, 400);
        assertThat(body.get("errors").isArray()).isTrue();
        assertThat(body.get("errors")).isNotEmpty();
        assertThat(body.get("errors").get(0).has("property")).isTrue();
        assertThat(body.get("errors").get(0).has("message")).isTrue();
    }

    @Test
    void t2_recursoNaoEncontradoDeveRetornarProblemDetail404() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/tarefas/" + UUID.randomUUID(),
                HttpMethod.GET,
                autenticado(),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND);
        JsonNode body = lerJson(response.getBody());
        assertProblemDetail(body, 404);
        assertThat(body.path("title").asString()).isEqualTo("Tarefa não encontrada");
    }

    @Test
    void t3_emailDuplicadoDeveRetornarProblemDetail409() {
        String email = "dup-" + UUID.randomUUID() + "@email.com";
        assertThat(restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Primeiro", "email", email, "senha", SENHA)),
                Map.class).getStatusCode()).isEqualTo(CREATED);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Segundo", "email", email, "senha", SENHA)),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(CONFLICT);
        JsonNode body = lerJson(response.getBody());
        assertProblemDetail(body, 409);
        assertThat(body.get("title").asString()).isEqualTo("Email já cadastrado");
    }

    @Test
    void t3_uniqueCapacidadeSemanalViaIntegridadeDeveRetornar409() {
        Map<String, Object> item = Map.of("diaSemana", "SEGUNDA", "horas", 3);
        assertThat(restTemplate.exchange(
                "/capacidades/semanal",
                HttpMethod.POST,
                autenticadoJson(item),
                List.class).getStatusCode()).isEqualTo(CREATED);

        ResponseEntity<String> conflito = restTemplate.exchange(
                "/capacidades/semanal",
                HttpMethod.POST,
                autenticadoJson(item),
                String.class);

        assertThat(conflito.getStatusCode()).isEqualTo(CONFLICT);
        JsonNode body = lerJson(conflito.getBody());
        assertProblemDetail(body, 409);
        assertThat(body.get("title").asString()).isEqualTo("Capacidade semanal já existe");
    }

    @Test
    void t4_semTokenDeveRetornarProblemDetail401() {
        ResponseEntity<String> response = restTemplate.getForEntity("/me", String.class);

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
        JsonNode body = lerJson(response.getBody());
        assertProblemDetail(body, 401);
        assertThat(body.get("title").asString()).isEqualTo("Não autorizado");
        assertThat(body.get("detail").asString()).isEqualTo("Não autenticado.");
    }

    @Test
    void t5_jsonMalformadoDeveRetornarProblemDetail400() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.exchange(
                "/capacidades/semanal",
                HttpMethod.POST,
                new HttpEntity<>("{", headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
        JsonNode body = lerJson(response.getBody());
        assertProblemDetail(body, 400);
        assertThat(body.path("title").asString()).isEqualTo("Requisição inválida");
        assertThat(body.path("detail").asString()).isEqualTo("O corpo da requisição está malformado ou ilegível.");
    }

    @Test
    void t6_uuidMalformadoNoPathDeveRetornarProblemDetail400() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/tarefas/nao-e-uuid",
                HttpMethod.GET,
                autenticado(),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
        JsonNode body = lerJson(response.getBody());
        assertProblemDetail(body, 400);
        assertThat(body.path("title").asString()).isEqualTo("Parâmetro inválido");
        assertThat(body.path("detail").asString()).isEqualTo("O valor informado não é compatível com o tipo esperado.");
    }

    @Test
    void t7_queryObrigatoriaAusenteDeveRetornarProblemDetail400() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/planos",
                HttpMethod.GET,
                autenticado(),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
        JsonNode body = lerJson(response.getBody());
        assertProblemDetail(body, 400);
        assertThat(body.path("title").asString()).isEqualTo("Parâmetro obrigatório ausente");
        assertThat(body.path("detail").asString()).isEqualTo("Um parâmetro obrigatório não foi informado.");
    }

    @Test
    void t8_capacidadeSemanalAceitaItemUnicoEListaERejeitaInvalido() {
        ResponseEntity<List> unitario = restTemplate.exchange(
                "/capacidades/semanal",
                HttpMethod.POST,
                autenticadoJson(Map.of("diaSemana", "TERCA", "horas", 2)),
                List.class);
        assertThat(unitario.getStatusCode()).isEqualTo(CREATED);
        assertThat(unitario.getBody()).hasSize(1);

        ResponseEntity<List> lote = restTemplate.exchange(
                "/capacidades/semanal",
                HttpMethod.POST,
                autenticadoJson(List.of(
                        Map.of("diaSemana", "QUARTA", "horas", 1),
                        Map.of("diaSemana", "QUINTA", "horas", 2))),
                List.class);
        assertThat(lote.getStatusCode()).isEqualTo(CREATED);
        assertThat(lote.getBody()).hasSize(2);

        ResponseEntity<String> invalido = restTemplate.exchange(
                "/capacidades/semanal",
                HttpMethod.POST,
                autenticadoJson(Map.of("diaSemana", "SEXTA")),
                String.class);
        assertThat(invalido.getStatusCode()).isEqualTo(BAD_REQUEST);
        assertProblemDetail(lerJson(invalido.getBody()), 400);
    }

    private static void assertProblemDetail(JsonNode body, int status) {
        assertThat(body).isNotNull();
        assertThat(body.path("title").asString()).isNotBlank();
        assertThat(body.path("detail").asString()).isNotBlank();
        assertThat(body.path("status").asInt()).isEqualTo(status);
    }

    private static JsonNode lerJson(String body) {
        assertThat(body).isNotBlank();
        return JSON.readTree(body);
    }

    private HttpEntity<Void> autenticado() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return new HttpEntity<>(headers);
    }

    private HttpEntity<?> autenticadoJson(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private static HttpEntity<Map<String, String>> json(Map<String, String> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
