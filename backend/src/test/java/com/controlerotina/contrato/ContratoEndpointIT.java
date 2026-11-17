package com.controlerotina.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Endpoint do spec ligado (perfil de teste): exige autenticação e serve OpenAPI 3.1. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class ContratoEndpointIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    TestRestTemplate rest;

    @Test
    void deveServirSpecOpenApi31ComTokenValido() {
        String token = ContratoTestSupport.tokenNovoUsuario(rest);

        ResponseEntity<String> response = ContratoTestSupport.get(rest, "/v3/api-docs", token);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        JsonNode spec = JsonMapper.shared().readTree(response.getBody());
        assertThat(spec.path("openapi").asString()).startsWith("3.1");
        assertThat(spec.path("paths").has("/tarefas")).isTrue();
    }

    @Test
    void deveRetornar401QuandoSemToken() {
        ResponseEntity<String> response = ContratoTestSupport.get(rest, "/v3/api-docs", null);

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
        assertThat(response.getBody()).contains("Não autorizado");
    }

    @Test
    void deveRetornar401QuandoTokenInvalido() {
        ResponseEntity<String> response = ContratoTestSupport.get(rest, "/v3/api-docs", "token.invalido");

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
        assertThat(response.getBody()).contains("Não autorizado");
    }

    @Test
    void naoDeveExporSwaggerUi() {
        String token = ContratoTestSupport.tokenNovoUsuario(rest);

        ResponseEntity<String> response = ContratoTestSupport.get(rest, "/swagger-ui/index.html", token);

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }
}
