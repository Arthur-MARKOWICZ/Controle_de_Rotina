package com.controlerotina.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
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
class AuthSessaoIT {

    private static final String SENHA = "Senha@123";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private String email;

    @BeforeEach
    void cadastrarUsuario() {
        email = "user-" + UUID.randomUUID() + "@email.com";
        var body = Map.of(
                "nome", "Usuario Teste",
                "email", email,
                "senha", SENHA
        );
        assertThat(restTemplate.postForEntity("/usuarios", json(body), Map.class).getStatusCode())
                .isEqualTo(CREATED);
    }

    @Test
    void deveLogarComAccessJwtERefreshHttpOnly() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/auth/login",
                json(Map.of("email", email, "senha", SENHA)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("accessToken")).isInstanceOf(String.class);
        assertThat(response.getBody().get("accessToken").toString()).isNotBlank();
        assertThat(response.getBody()).doesNotContainKeys("refreshToken", "senha", "senhaHash");

        String setCookie = refreshSetCookie(response);
        assertThat(setCookie).contains("refresh_token=");
        assertThat(setCookie).containsIgnoringCase("HttpOnly");
        assertThat(setCookie).doesNotContain("refresh_token=;");

        Instant now = Instant.now();
        Map<String, Object> claims = decodeJwtPayload(response.getBody().get("accessToken").toString());
        Instant exp = Instant.ofEpochSecond(((Number) claims.get("exp")).longValue());
        Instant iat = Instant.ofEpochSecond(((Number) claims.get("iat")).longValue());
        assertThat(exp.getEpochSecond() - iat.getEpochSecond()).isEqualTo(15 * 60);
        assertThat(exp).isAfter(now.plusSeconds(14 * 60));
        assertThat(exp).isBefore(now.plusSeconds(16 * 60));

        Integer maxAge = maxAgeSeconds(setCookie);
        assertThat(maxAge).isEqualTo(5 * 24 * 60 * 60);

        Integer ativos = jdbcTemplate.queryForObject(
                "select count(*) from refresh_token where revoked_at is null", Integer.class);
        assertThat(ativos).isGreaterThanOrEqualTo(1);
    }

    @Test
    void deveRetornar401GenericoQuandoEmailNaoExiste() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/auth/login",
                json(Map.of("email", "naoexiste@email.com", "senha", SENHA)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).isNullOrEmpty();
    }

    @Test
    void deveRetornar401GenericoQuandoSenhaIncorreta() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/auth/login",
                json(Map.of("email", email, "senha", "Senha@999")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).isNullOrEmpty();
    }

    @Test
    void deveRenovarAccessComRefreshCookieERotacionarRefresh() {
        ResponseEntity<Map> login = restTemplate.postForEntity(
                "/auth/login",
                json(Map.of("email", email, "senha", SENHA)),
                Map.class);
        String cookie = refreshCookieValue(login);
        String accessAntigo = login.getBody().get("accessToken").toString();

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "refresh_token=" + cookie);

        ResponseEntity<Map> refresh = restTemplate.exchange(
                "/auth/refresh",
                HttpMethod.POST,
                new HttpEntity<>(null, headers),
                Map.class);

        assertThat(refresh.getStatusCode()).isEqualTo(OK);
        assertThat(refresh.getBody().get("accessToken").toString()).isNotBlank();
        assertThat(refresh.getBody().get("accessToken").toString()).isNotEqualTo(accessAntigo);

        String novoCookie = refreshCookieValue(refresh);
        assertThat(novoCookie).isNotEqualTo(cookie);

        Integer revogados = jdbcTemplate.queryForObject(
                """
                select count(*) from refresh_token
                where revoked_at is not null
                """,
                Integer.class);
        assertThat(revogados).isGreaterThanOrEqualTo(1);
    }

    @Test
    void deveRejeitarRefreshSemCookieCom401() {
        ResponseEntity<Map> response = restTemplate.postForEntity("/auth/refresh", null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
    }

    @Test
    void deveRevogarRefreshNoLogoutELimparCookie() {
        ResponseEntity<Map> login = restTemplate.postForEntity(
                "/auth/login",
                json(Map.of("email", email, "senha", SENHA)),
                Map.class);
        String cookie = refreshCookieValue(login);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "refresh_token=" + cookie);

        ResponseEntity<Void> logout = restTemplate.exchange(
                "/auth/logout",
                HttpMethod.POST,
                new HttpEntity<>(null, headers),
                Void.class);

        assertThat(logout.getStatusCode().is2xxSuccessful()).isTrue();
        String cleared = refreshSetCookie(logout);
        assertThat(cleared).containsIgnoringCase("Max-Age=0");

        Integer ativos = jdbcTemplate.queryForObject(
                """
                select count(*) from refresh_token rt
                join usuario u on u.id = rt.usuario_id
                where u.email = ? and rt.revoked_at is null
                """,
                Integer.class,
                email);
        assertThat(ativos).isZero();

        ResponseEntity<Map> refreshAposLogout = restTemplate.exchange(
                "/auth/refresh",
                HttpMethod.POST,
                new HttpEntity<>(null, headers),
                Map.class);
        assertThat(refreshAposLogout.getStatusCode()).isEqualTo(UNAUTHORIZED);
    }

    private static HttpEntity<Map<String, String>> json(Map<String, String> body) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private static String refreshSetCookie(ResponseEntity<?> response) {
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).isNotNull().isNotEmpty();
        return cookies.stream()
                .filter(c -> c.startsWith("refresh_token="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Cookie refresh_token ausente: " + cookies));
    }

    private static String refreshCookieValue(ResponseEntity<?> response) {
        String setCookie = refreshSetCookie(response);
        String valuePart = setCookie.substring("refresh_token=".length());
        int end = valuePart.indexOf(';');
        return end >= 0 ? valuePart.substring(0, end) : valuePart;
    }

    private static Integer maxAgeSeconds(String setCookie) {
        for (String part : setCookie.split(";")) {
            String trimmed = part.trim();
            if (trimmed.regionMatches(true, 0, "Max-Age=", 0, 8)) {
                return Integer.parseInt(trimmed.substring(8));
            }
        }
        return null;
    }

    private static Map<String, Object> decodeJwtPayload(String jwt) {
        String[] parts = jwt.split("\\.");
        assertThat(parts).hasSize(3);
        byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
        JsonNode node = JSON.readTree(new String(decoded, StandardCharsets.UTF_8));
        return Map.of(
                "sub", node.get("sub").asText(),
                "iat", node.get("iat").asLong(),
                "exp", node.get("exp").asLong()
        );
    }
}
