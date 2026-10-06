package com.controlerotina.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.CREATED;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class CadastroUsuarioIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void deveCadastrarUsuarioComSenhaHasheadaERetornar201SemJwt() {
        var body = Map.of(
                "nome", "Ana Silva",
                "email", "  Ana.Silva@Email.COM  ",
                "senha", "Senha@123"
        );

        ResponseEntity<Map> response = restTemplate.postForEntity("/usuarios", json(body), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("id")).isNotNull();
        assertThat(response.getBody().get("nome")).isEqualTo("Ana Silva");
        assertThat(response.getBody().get("email")).isEqualTo("ana.silva@email.com");
        assertThat(response.getBody()).doesNotContainKeys("senha", "senhaHash", "accessToken", "token", "jwt");
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).isNullOrEmpty();

        UUID id = UUID.fromString(response.getBody().get("id").toString());
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select email, senha_hash, nome from usuario where id = ?", id);

        assertThat(row.get("email")).isEqualTo("ana.silva@email.com");
        assertThat(row.get("nome")).isEqualTo("Ana Silva");
        assertThat(row.get("senha_hash").toString()).isNotEqualTo("Senha@123");
        assertThat(new BCryptPasswordEncoder().matches("Senha@123", row.get("senha_hash").toString())).isTrue();
    }

    @Test
    void deveRejeitarEmailDuplicadoCom409() {
        var primeiro = Map.of(
                "nome", "Primeiro",
                "email", "duplicado@email.com",
                "senha", "Senha@123"
        );
        var segundo = Map.of(
                "nome", "Segundo",
                "email", "Duplicado@Email.com",
                "senha", "Outra@123"
        );

        assertThat(restTemplate.postForEntity("/usuarios", json(primeiro), Map.class).getStatusCode())
                .isEqualTo(CREATED);

        ResponseEntity<Map> response = restTemplate.postForEntity("/usuarios", json(segundo), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(CONFLICT);
    }

    @Test
    void deveRejeitarSenhaFracaCom400() {
        var body = Map.of(
                "nome", "Ana",
                "email", "fraca@email.com",
                "senha", "senhafraca"
        );

        ResponseEntity<Map> response = restTemplate.postForEntity("/usuarios", json(body), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
    }

    @Test
    void deveRejeitarEmailInvalidoCom400() {
        var body = Map.of(
                "nome", "Ana",
                "email", "nao-e-email",
                "senha", "Senha@123"
        );

        ResponseEntity<Map> response = restTemplate.postForEntity("/usuarios", json(body), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
    }

    private static HttpEntity<Map<String, String>> json(Map<String, String> body) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
