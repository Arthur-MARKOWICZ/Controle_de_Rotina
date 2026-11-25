package com.controlerotina.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
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
class ExcluirContaIT {

    private static final String SENHA = "Senha@123";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    JdbcTemplate jdbc;

    private String email;
    private UUID usuarioId;
    private String accessToken;
    private String refreshCookie;

    @BeforeEach
    void cadastrarELogar() {
        email = "excluir-" + UUID.randomUUID() + "@email.com";
        assertThat(restTemplate.postForEntity("/usuarios",
                json(Map.of("nome", "Ana", "email", email, "senha", SENHA)), Map.class)
                .getStatusCode()).isEqualTo(CREATED);
        ResponseEntity<Map> login = login(email);
        accessToken = login.getBody().get("accessToken").toString();
        refreshCookie = refreshCookieDe(login);
        usuarioId = UUID.fromString(get("/me", accessToken).getBody().get("id").toString());
        new DadosUsuarioSeeder(jdbc).popularTudo(usuarioId);
    }

    @Test
    void deveExcluirContaComSenhaCorretaEApagarTudo() {
        ResponseEntity<Map> response = excluir(accessToken, Map.of("senhaAtual", SENHA));

        assertThat(response.getStatusCode()).isEqualTo(NO_CONTENT);
        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).startsWith("refresh_token=;").containsIgnoringCase("Max-Age=0");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuario WHERE id = ?", Long.class, usuarioId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tarefa WHERE usuario_id = ?", Long.class, usuarioId))
                .isZero();
        assertThat(restTemplate.postForEntity("/usuarios",
                json(Map.of("nome", "Ana de novo", "email", email, "senha", SENHA)), Map.class)
                .getStatusCode()).isEqualTo(CREATED);
    }

    @Test
    void segundaExclusaoComOMesmoTokenDeveRetornar401() {
        assertThat(excluir(accessToken, Map.of("senhaAtual", SENHA)).getStatusCode()).isEqualTo(NO_CONTENT);

        ResponseEntity<Map> segunda = excluir(accessToken, Map.of("senhaAtual", SENHA));

        assertThat(segunda.getStatusCode()).isEqualTo(UNAUTHORIZED);
    }

    @Test
    void refreshDepoisDaExclusaoDeveRetornar401SemEmitirToken() {
        assertThat(excluir(accessToken, Map.of("senhaAtual", SENHA)).getStatusCode()).isEqualTo(NO_CONTENT);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "refresh_token=" + refreshCookie);
        ResponseEntity<Map> refresh = restTemplate.exchange(
                "/auth/refresh", HttpMethod.POST, new HttpEntity<>(headers), Map.class);

        assertThat(refresh.getStatusCode()).isEqualTo(UNAUTHORIZED);
        assertThat(refresh.getHeaders().get(HttpHeaders.SET_COOKIE)).isNullOrEmpty();
    }

    @Test
    void falhaNoCommitNaoDeveApagarNadaNemDeslogar() {
        // Um plano de outro usuário apontando para uma tarefa deste viola a FK adiável no commit.
        UUID outro = new DadosUsuarioSeeder(jdbc).criarUsuario("outro-" + UUID.randomUUID() + "@email.com");
        UUID tarefa = jdbc.queryForObject(
                "SELECT id FROM tarefa WHERE usuario_id = ? AND parent_id IS NULL AND deleted_at IS NULL",
                UUID.class, usuarioId);
        UUID plano = UUID.randomUUID();
        Timestamp agora = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO plano (id, usuario_id, data, versao, status, tem_conflito, gerado_em, created_at, updated_at)
                VALUES (?, ?, ?, 1, 'RASCUNHO', false, ?, ?, ?)
                """, plano, outro, LocalDate.of(2026, 11, 25), agora, agora, agora);
        jdbc.update("""
                INSERT INTO bloco_plano (id, plano_id, tarefa_id, duracao_minutos, ordem, created_at, updated_at)
                VALUES (?, ?, ?, 30, 1, ?, ?)
                """, UUID.randomUUID(), plano, tarefa, agora, agora);
        long tarefasAntes = jdbc.queryForObject("SELECT count(*) FROM tarefa WHERE usuario_id = ?", Long.class,
                usuarioId);

        ResponseEntity<Map> response = excluir(accessToken, Map.of("senhaAtual", SENHA));

        assertThat(response.getStatusCode()).isEqualTo(INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("title", "Erro de integridade");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tarefa WHERE usuario_id = ?", Long.class, usuarioId))
                .isEqualTo(tarefasAntes);
        assertThat(get("/me", accessToken).getStatusCode()).isEqualTo(OK);
    }

    @Test
    void senhaErradaDeveRetornar401CredenciaisInvalidasSemApagar() {
        ResponseEntity<Map> response = excluir(accessToken, Map.of("senhaAtual", "Errada@123"));

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
        assertThat(response.getBody())
                .containsEntry("title", "Não autorizado")
                .containsEntry("detail", "Credenciais inválidas.");
        assertDadosIntactos();
    }

    @Test
    void semSenhaDeveRetornar400ApontandoSenhaAtual() {
        ResponseEntity<Map> response = excluir(accessToken, Map.of("senhaAtual", "  "));

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("title", "Dados inválidos");
        assertThat((List<Map<String, String>>) response.getBody().get("errors"))
                .extracting(e -> e.get("property"))
                .contains("senhaAtual");
        assertDadosIntactos();
    }

    @Test
    void corpoSemSenhaAtualDeveRetornar400ApontandoSenhaAtual() {
        ResponseEntity<Map> response = excluir(accessToken, Map.of());

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("title", "Dados inválidos");
        assertThat((List<Map<String, String>>) response.getBody().get("errors"))
                .extracting(e -> e.get("property"))
                .contains("senhaAtual");
        assertDadosIntactos();
    }

    @Test
    void jsonMalformadoDeveRetornar400RequisicaoInvalida() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        ResponseEntity<Map> response = restTemplate.exchange(
                "/me", HttpMethod.DELETE, new HttpEntity<>("{\"senhaAtual\":", headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("title", "Requisição inválida");
        assertDadosIntactos();
    }

    @Test
    void semTokenDeveRetornar401() {
        ResponseEntity<Map> response = excluir(null, Map.of("senhaAtual", SENHA));

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("title", "Não autorizado");
        assertDadosIntactos();
    }

    private void assertDadosIntactos() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuario WHERE id = ?", Long.class, usuarioId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tarefa WHERE usuario_id = ?", Long.class, usuarioId))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM execucao WHERE usuario_id = ?", Long.class, usuarioId))
                .isEqualTo(1);
    }

    private ResponseEntity<Map> excluir(String token, Map<String, ?> corpo) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return restTemplate.exchange("/me", HttpMethod.DELETE, new HttpEntity<>(corpo, headers), Map.class);
    }

    private ResponseEntity<Map> get(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }

    private ResponseEntity<Map> login(String email) {
        ResponseEntity<Map> login = restTemplate.postForEntity(
                "/auth/login", json(Map.of("email", email, "senha", SENHA)), Map.class);
        assertThat(login.getStatusCode()).isEqualTo(OK);
        return login;
    }

    private static String refreshCookieDe(ResponseEntity<?> response) {
        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        String valor = setCookie.substring("refresh_token=".length());
        return valor.substring(0, valor.indexOf(';'));
    }

    private static HttpEntity<Map<String, ?>> json(Map<String, ?> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
