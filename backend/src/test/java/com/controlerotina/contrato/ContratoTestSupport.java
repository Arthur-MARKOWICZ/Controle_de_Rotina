package com.controlerotina.contrato;

import java.util.Map;
import java.util.UUID;

import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Cadastro + login e GET autenticado, compartilhados pelos ITs do contrato. */
final class ContratoTestSupport {

    private static final String SENHA = "Senha@123";

    private ContratoTestSupport() {
    }

    static String tokenNovoUsuario(TestRestTemplate rest) {
        String email = "contrato-" + UUID.randomUUID() + "@email.com";
        rest.postForEntity("/usuarios", json(Map.of("nome", "Contrato", "email", email, "senha", SENHA)), Map.class);
        ResponseEntity<Map> login = rest.postForEntity(
                "/auth/login", json(Map.of("email", email, "senha", SENHA)), Map.class);
        return (String) login.getBody().get("accessToken");
    }

    static ResponseEntity<String> get(TestRestTemplate rest, String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private static HttpEntity<Map<String, String>> json(Map<String, String> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
