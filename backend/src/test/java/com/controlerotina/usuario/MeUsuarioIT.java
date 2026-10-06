package com.controlerotina.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.util.HashMap;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class MeUsuarioIT {

    private static final String SENHA = "Senha@123";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private String email;
    private String nome;
    private String accessToken;

    @BeforeEach
    void cadastrarELogar() {
        email = "me-" + UUID.randomUUID() + "@email.com";
        nome = "Usuario Me";
        assertThat(restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", nome, "email", email, "senha", SENHA)),
                Map.class).getStatusCode()).isEqualTo(CREATED);

        ResponseEntity<Map> login = restTemplate.postForEntity(
                "/auth/login",
                json(Map.of("email", email, "senha", SENHA)),
                Map.class);
        assertThat(login.getStatusCode()).isEqualTo(OK);
        accessToken = login.getBody().get("accessToken").toString();
    }

    @Test
    void deveRetornar401EmGetMeSemToken() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/me", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
    }

    @Test
    void deveRetornar401EmGetMeComTokenInvalido() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("token.invalido.aqui");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/me",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
    }

    @Test
    void deveRetornarNomeEEmailDoUsuarioAutenticado() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/me",
                HttpMethod.GET,
                autenticado(),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("nome")).isEqualTo(nome);
        assertThat(response.getBody().get("email")).isEqualTo(email);
        assertThat(response.getBody()).doesNotContainKeys("senha", "senhaHash");
    }

    @Test
    void deveRetornar401EmPutMeSemToken() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/me",
                HttpMethod.PUT,
                json(Map.of("senhaAtual", SENHA, "nome", "Novo Nome")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
    }

    @Test
    void deveRejeitarAtualizacaoSemSenhaAtualCom400() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/me",
                HttpMethod.PUT,
                autenticadoJson(Map.of("nome", "Novo Nome")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
    }

    @Test
    void deveRejeitarSenhaAtualIncorretaCom401() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/me",
                HttpMethod.PUT,
                autenticadoJson(Map.of("senhaAtual", "Senha@999", "nome", "Novo Nome")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
    }

    @Test
    void deveAtualizarNomeComSenhaAtual() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/me",
                HttpMethod.PUT,
                autenticadoJson(Map.of("senhaAtual", SENHA, "nome", "Nome Atualizado")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody().get("nome")).isEqualTo("Nome Atualizado");
        assertThat(response.getBody().get("email")).isEqualTo(email);
    }

    @Test
    void deveAtualizarEmailComSenhaAtual() {
        String novoEmail = "novo-" + UUID.randomUUID() + "@email.com";

        ResponseEntity<Map> response = restTemplate.exchange(
                "/me",
                HttpMethod.PUT,
                autenticadoJson(Map.of("senhaAtual", SENHA, "email", "  " + novoEmail.toUpperCase() + "  ")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody().get("email")).isEqualTo(novoEmail.toLowerCase());
        assertThat(response.getBody().get("nome")).isEqualTo(nome);
    }

    @Test
    void deveRejeitarEmailDuplicadoCom409() {
        String outroEmail = "outro-" + UUID.randomUUID() + "@email.com";
        assertThat(restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Outro", "email", outroEmail, "senha", SENHA)),
                Map.class).getStatusCode()).isEqualTo(CREATED);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/me",
                HttpMethod.PUT,
                autenticadoJson(Map.of("senhaAtual", SENHA, "email", outroEmail)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(CONFLICT);
    }

    @Test
    void deveAtualizarSenhaComSenhaAtual() {
        String novaSenha = "NovaSenha@456";

        ResponseEntity<Map> response = restTemplate.exchange(
                "/me",
                HttpMethod.PUT,
                autenticadoJson(Map.of("senhaAtual", SENHA, "novaSenha", novaSenha)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(OK);

        String hash = jdbcTemplate.queryForObject(
                "select senha_hash from usuario where email = ?",
                String.class,
                email);
        assertThat(new BCryptPasswordEncoder().matches(novaSenha, hash)).isTrue();

        ResponseEntity<Map> loginAntiga = restTemplate.postForEntity(
                "/auth/login",
                json(Map.of("email", email, "senha", SENHA)),
                Map.class);
        assertThat(loginAntiga.getStatusCode()).isEqualTo(UNAUTHORIZED);

        ResponseEntity<Map> loginNova = restTemplate.postForEntity(
                "/auth/login",
                json(Map.of("email", email, "senha", novaSenha)),
                Map.class);
        assertThat(loginNova.getStatusCode()).isEqualTo(OK);
    }

    @Test
    void deveRejeitarNovaSenhaFracaCom400() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/me",
                HttpMethod.PUT,
                autenticadoJson(Map.of("senhaAtual", SENHA, "novaSenha", "fraca")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
    }

    @Test
    void deveManterCadastroELoginPublicos() {
        String novoEmail = "publico-" + UUID.randomUUID() + "@email.com";

        assertThat(restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Publico", "email", novoEmail, "senha", SENHA)),
                Map.class).getStatusCode()).isEqualTo(CREATED);

        assertThat(restTemplate.postForEntity(
                "/auth/login",
                json(Map.of("email", novoEmail, "senha", SENHA)),
                Map.class).getStatusCode()).isEqualTo(OK);
    }

    private HttpEntity<Void> autenticado() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return new HttpEntity<>(headers);
    }

    private HttpEntity<Map<String, String>> autenticadoJson(Map<String, String> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        return new HttpEntity<>(new HashMap<>(body), headers);
    }

    private static HttpEntity<Map<String, String>> json(Map<String, String> body) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
