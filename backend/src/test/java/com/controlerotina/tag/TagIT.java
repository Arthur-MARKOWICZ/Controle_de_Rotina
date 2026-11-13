package com.controlerotina.tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NO_CONTENT;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class TagIT {

    private static final String SENHA = "Senha@123";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private String accessToken;
    private UUID usuarioId;

    @BeforeEach
    void cadastrarELogar() {
        String email = "tag-" + UUID.randomUUID() + "@email.com";
        assertThat(restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Dono Tag", "email", email, "senha", SENHA)),
                Map.class).getStatusCode()).isEqualTo(CREATED);

        ResponseEntity<Map> login = restTemplate.postForEntity(
                "/auth/login",
                json(Map.of("email", email, "senha", SENHA)),
                Map.class);
        assertThat(login.getStatusCode()).isEqualTo(OK);
        accessToken = login.getBody().get("accessToken").toString();
        usuarioId = jdbcTemplate.queryForObject(
                "select id from usuario where email = ?",
                UUID.class,
                email);
    }

    @Test
    void deveRetornar401AoCriarSemToken() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/tags",
                json(Map.of("nome", "estudo")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
    }

    @Test
    void deveCriarTagComCorDefault() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/tags",
                HttpMethod.POST,
                autenticadoJson(Map.of("nome", "estudo_conceito")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("nome")).isEqualTo("estudo_conceito");
        assertThat(response.getBody().get("cor")).isEqualTo("#6B7280");
        assertThat(response.getBody().get("id")).isNotNull();
    }

    @Test
    void deveRejeitarNomeDuplicadoCaseInsensitive() {
        assertThat(restTemplate.exchange(
                "/tags",
                HttpMethod.POST,
                autenticadoJson(Map.of("nome", "estudo_conceito", "cor", "#6E75F4")),
                Map.class).getStatusCode()).isEqualTo(CREATED);

        ResponseEntity<Map> duplicada = restTemplate.exchange(
                "/tags",
                HttpMethod.POST,
                autenticadoJson(Map.of("nome", "Estudo_Conceito")),
                Map.class);

        assertThat(duplicada.getStatusCode()).isEqualTo(CONFLICT);
    }

    @Test
    void deveListarSomenteTagsDoUsuario() {
        String emailOutro = "tag-outro-" + UUID.randomUUID() + "@email.com";
        assertThat(restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Outro", "email", emailOutro, "senha", SENHA)),
                Map.class).getStatusCode()).isEqualTo(CREATED);
        String tokenOutro = restTemplate.postForEntity(
                "/auth/login",
                json(Map.of("email", emailOutro, "senha", SENHA)),
                Map.class).getBody().get("accessToken").toString();

        restTemplate.exchange(
                "/tags",
                HttpMethod.POST,
                autenticadoJson(Map.of("nome", "minha"), accessToken),
                Map.class);
        restTemplate.exchange(
                "/tags",
                HttpMethod.POST,
                autenticadoJson(Map.of("nome", "outra"), tokenOutro),
                Map.class);

        ResponseEntity<List> lista = restTemplate.exchange(
                "/tags",
                HttpMethod.GET,
                autenticado(accessToken),
                List.class);

        assertThat(lista.getStatusCode()).isEqualTo(OK);
        assertThat(lista.getBody()).hasSize(1);
        assertThat(((Map<?, ?>) lista.getBody().get(0)).get("nome")).isEqualTo("minha");
    }

    @Test
    void deveAtualizarNomeECor() {
        UUID id = UUID.fromString(restTemplate.exchange(
                "/tags",
                HttpMethod.POST,
                autenticadoJson(Map.of("nome", "hold", "cor", "#EF2D24")),
                Map.class).getBody().get("id").toString());

        ResponseEntity<Map> response = restTemplate.exchange(
                "/tags/" + id,
                HttpMethod.PATCH,
                autenticadoJson(Map.of("nome", "hold_paused", "cor", "#A7C9B9")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody().get("nome")).isEqualTo("hold_paused");
        assertThat(response.getBody().get("cor")).isEqualTo("#A7C9B9");
    }

    @Test
    void deveApagarTagERemoverVinculos() {
        UUID tagId = UUID.fromString(restTemplate.exchange(
                "/tags",
                HttpMethod.POST,
                autenticadoJson(Map.of("nome", "controle_rotina", "cor", "#F2B04B")),
                Map.class).getBody().get("id").toString());

        ResponseEntity<Map> tarefa = restTemplate.exchange(
                "/tarefas",
                HttpMethod.POST,
                autenticadoJson(Map.of("titulo", "Mobile")),
                Map.class);
        assertThat(tarefa.getStatusCode()).isEqualTo(CREATED);
        UUID tarefaId = UUID.fromString(tarefa.getBody().get("id").toString());

        jdbcTemplate.update(
                "insert into tarefa_tag (tarefa_id, tag_id) values (?, ?)",
                tarefaId,
                tagId);

        ResponseEntity<Void> delete = restTemplate.exchange(
                "/tags/" + tagId,
                HttpMethod.DELETE,
                autenticado(accessToken),
                Void.class);

        assertThat(delete.getStatusCode()).isEqualTo(NO_CONTENT);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from tag where id = ?",
                Integer.class,
                tagId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from tarefa_tag where tag_id = ?",
                Integer.class,
                tagId)).isZero();
    }

    @Test
    void deveRetornar404AoApagarTagDeOutroUsuario() {
        String emailOutro = "tag-404-" + UUID.randomUUID() + "@email.com";
        restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Outro", "email", emailOutro, "senha", SENHA)),
                Map.class);
        String tokenOutro = restTemplate.postForEntity(
                "/auth/login",
                json(Map.of("email", emailOutro, "senha", SENHA)),
                Map.class).getBody().get("accessToken").toString();

        UUID tagId = UUID.fromString(restTemplate.exchange(
                "/tags",
                HttpMethod.POST,
                autenticadoJson(Map.of("nome", "secreta"), tokenOutro),
                Map.class).getBody().get("id").toString());

        ResponseEntity<Map> response = restTemplate.exchange(
                "/tags/" + tagId,
                HttpMethod.DELETE,
                autenticado(accessToken),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND);
    }

    @Test
    void deveRejeitarNomeDuplicadoComAcentoNaMesmaLetra() {
        assertThat(restTemplate.exchange(
                "/tags", HttpMethod.POST, autenticadoJson(Map.of("nome", "ação")), Map.class)
                .getStatusCode()).isEqualTo(CREATED);

        ResponseEntity<Map> duplicada = restTemplate.exchange(
                "/tags",
                HttpMethod.POST,
                autenticadoJson(Map.of("nome", "AÇÃO")),
                Map.class);

        assertThat(duplicada.getStatusCode()).isEqualTo(CONFLICT);
        assertThat(duplicada.getBody().get("title")).isEqualTo("Tag já existe");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from tag where usuario_id = ?", Integer.class, usuarioId)).isEqualTo(1);
    }

    @Test
    void deveRejeitarNomeVazio() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/tags",
                HttpMethod.POST,
                autenticadoJson(Map.of("nome", "  ")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
        assertThat(response.getBody().get("title")).isIn("Requisição inválida", "Dados inválidos");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from tag where usuario_id = ?", Integer.class, usuarioId)).isZero();
    }

    @Test
    void deveRejeitarNomeLongoDemais() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/tags",
                HttpMethod.POST,
                autenticadoJson(Map.of("nome", "a".repeat(65))),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
        assertThat(response.getBody().get("title")).isIn("Requisição inválida", "Dados inválidos");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from tag where usuario_id = ?", Integer.class, usuarioId)).isZero();
    }

    @Test
    void deveListarVazioQuandoUsuarioNaoTemTags() {
        ResponseEntity<List> lista = restTemplate.exchange(
                "/tags",
                HttpMethod.GET,
                autenticado(accessToken),
                List.class);

        assertThat(lista.getStatusCode()).isEqualTo(OK);
        assertThat(lista.getBody()).isEmpty();
    }

    private HttpEntity<Void> autenticado(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    private HttpEntity<Map<String, Object>> autenticadoJson(Map<String, Object> body) {
        return autenticadoJson(body, accessToken);
    }

    private HttpEntity<Map<String, Object>> autenticadoJson(Map<String, Object> body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    private static HttpEntity<Map<String, Object>> json(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
