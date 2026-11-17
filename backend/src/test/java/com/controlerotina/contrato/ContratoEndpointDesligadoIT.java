package com.controlerotina.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.NOT_FOUND;
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

/** Configuração de produção (springdoc.api-docs.enabled=false): o spec não é servido. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "springdoc.api-docs.enabled=false")
@AutoConfigureTestRestTemplate
@Testcontainers
class ContratoEndpointDesligadoIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    TestRestTemplate rest;

    @Test
    void naoDeveServirSpecMesmoComTokenValido() {
        String token = ContratoTestSupport.tokenNovoUsuario(rest);

        ResponseEntity<String> response = ContratoTestSupport.get(rest, "/v3/api-docs", token);

        assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND);
        assertThat(response.getBody()).doesNotContain("\"openapi\"");
    }

    @Test
    void naoDeveServirSpecSemToken() {
        ResponseEntity<String> response = ContratoTestSupport.get(rest, "/v3/api-docs", null);

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
        assertThat(response.getBody()).doesNotContain("\"openapi\"");
    }

    @Test
    void naoDeveServirVarianteYaml() {
        String token = ContratoTestSupport.tokenNovoUsuario(rest);

        ResponseEntity<String> response = ContratoTestSupport.get(rest, "/v3/api-docs.yaml", token);

        assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND);
        assertThat(response.getBody()).doesNotContain("openapi:");
    }
}
