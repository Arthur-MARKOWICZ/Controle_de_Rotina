package com.controlerotina.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
        assertThat(setCookie).containsIgnoringCase("Path=/auth");
        assertThat(setCookie).containsIgnoringCase("SameSite=Lax");
        assertThat(setCookie).doesNotContainIgnoringCase("Secure");
        assertThat(setCookie).doesNotContain("refresh_token=;");

        Instant now = Instant.now();
        Map<String, Object> claims = decodeJwtPayload(response.getBody().get("accessToken").toString());
        Instant exp = Instant.ofEpochSecond(((Number) claims.get("exp")).longValue());
        Instant iat = Instant.ofEpochSecond(((Number) claims.get("iat")).longValue());
        assertThat(exp.getEpochSecond() - iat.getEpochSecond()).isEqualTo(15 * 60);
        assertThat(exp).isAfter(now.plusSeconds(14 * 60));
        assertThat(exp).isBefore(now.plusSeconds(16 * 60));
        assertThat(claims.get("iss")).isEqualTo("controle-rotina");
        assertThat(claims.get("aud")).isEqualTo("controle-rotina-api");

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
    void t2_loginEmailInexistenteESenhaErradaDevemRetornarMesmo401Generico() {
        ResponseEntity<Map> emailInexistente = restTemplate.postForEntity(
                "/auth/login",
                json(Map.of("email", "naoexiste-" + UUID.randomUUID() + "@email.com", "senha", SENHA)),
                Map.class);
        ResponseEntity<Map> senhaErrada = restTemplate.postForEntity(
                "/auth/login",
                json(Map.of("email", email, "senha", "Senha@999")),
                Map.class);

        assertThat(emailInexistente.getStatusCode()).isEqualTo(UNAUTHORIZED);
        assertThat(senhaErrada.getStatusCode()).isEqualTo(UNAUTHORIZED);
        assertThat(emailInexistente.getHeaders().get(HttpHeaders.SET_COOKIE)).isNullOrEmpty();
        assertThat(senhaErrada.getHeaders().get(HttpHeaders.SET_COOKIE)).isNullOrEmpty();

        assertThat(emailInexistente.getBody()).isNotNull();
        assertThat(senhaErrada.getBody()).isNotNull();
        assertThat(emailInexistente.getBody().get("title"))
                .isEqualTo(senhaErrada.getBody().get("title"))
                .isEqualTo("Não autorizado");
        assertThat(emailInexistente.getBody().get("detail"))
                .isEqualTo(senhaErrada.getBody().get("detail"))
                .isEqualTo("Credenciais inválidas.");
        assertThat(emailInexistente.getBody().get("status"))
                .isEqualTo(senhaErrada.getBody().get("status"));
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
        assertThat(refreshSetCookie(refresh)).containsIgnoringCase("Path=/auth");

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
    void t1_reusoDeRefreshRevogadoDeveRevogarFamiliaEInvalidarTokenRoubado() {
        ResponseEntity<Map> login = restTemplate.postForEntity(
                "/auth/login",
                json(Map.of("email", email, "senha", SENHA)),
                Map.class);
        String refreshAntigo = refreshCookieValue(login);

        HttpHeaders headersAntigo = new HttpHeaders();
        headersAntigo.add(HttpHeaders.COOKIE, "refresh_token=" + refreshAntigo);

        ResponseEntity<Map> refreshLegitimo = restTemplate.exchange(
                "/auth/refresh",
                HttpMethod.POST,
                new HttpEntity<>(null, headersAntigo),
                Map.class);
        assertThat(refreshLegitimo.getStatusCode()).isEqualTo(OK);
        String refreshNovo = refreshCookieValue(refreshLegitimo);

        ResponseEntity<Map> reuso = restTemplate.exchange(
                "/auth/refresh",
                HttpMethod.POST,
                new HttpEntity<>(null, headersAntigo),
                Map.class);
        assertThat(reuso.getStatusCode()).isEqualTo(UNAUTHORIZED);
        assertThat(reuso.getBody()).isNotNull();
        assertThat(reuso.getBody().get("detail").toString()).isEqualTo("Credenciais inválidas.");
        assertThat(reuso.getBody().get("detail").toString()).doesNotContainIgnoringCase("reutiliz");

        Integer ativos = jdbcTemplate.queryForObject(
                """
                select count(*) from refresh_token rt
                join usuario u on u.id = rt.usuario_id
                where u.email = ? and rt.revoked_at is null
                """,
                Integer.class,
                email);
        assertThat(ativos).isZero();

        HttpHeaders headersNovo = new HttpHeaders();
        headersNovo.add(HttpHeaders.COOKIE, "refresh_token=" + refreshNovo);
        ResponseEntity<Map> comTokenRoubado = restTemplate.exchange(
                "/auth/refresh",
                HttpMethod.POST,
                new HttpEntity<>(null, headersNovo),
                Map.class);
        assertThat(comTokenRoubado.getStatusCode()).isEqualTo(UNAUTHORIZED);
    }

    @Test
    void t3_aposLimpezaTokensExpiradosOuRevogadosAntigosNaoPermanecem() {
        ResponseEntity<Map> login = restTemplate.postForEntity(
                "/auth/login",
                json(Map.of("email", email, "senha", SENHA)),
                Map.class);
        String cookie = refreshCookieValue(login);

        UUID usuarioId = jdbcTemplate.queryForObject(
                "select id from usuario where email = ?", UUID.class, email);
        Instant antigo = Instant.now().minus(8, ChronoUnit.DAYS);
        jdbcTemplate.update(
                """
                insert into refresh_token (id, usuario_id, token_hash, expires_at, revoked_at, created_at)
                values (?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(), usuarioId, "a".repeat(64),
                Timestamp.from(antigo.minus(1, ChronoUnit.DAYS)), null, Timestamp.from(antigo));
        jdbcTemplate.update(
                """
                insert into refresh_token (id, usuario_id, token_hash, expires_at, revoked_at, created_at)
                values (?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(), usuarioId, "b".repeat(64),
                Timestamp.from(Instant.now().plus(1, ChronoUnit.DAYS)),
                Timestamp.from(antigo),
                Timestamp.from(antigo));

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "refresh_token=" + cookie);
        ResponseEntity<Map> refresh = restTemplate.exchange(
                "/auth/refresh",
                HttpMethod.POST,
                new HttpEntity<>(null, headers),
                Map.class);
        assertThat(refresh.getStatusCode()).isEqualTo(OK);

        Integer antigosRestantes = jdbcTemplate.queryForObject(
                """
                select count(*) from refresh_token
                where token_hash in (?, ?)
                """,
                Integer.class,
                "a".repeat(64),
                "b".repeat(64));
        assertThat(antigosRestantes).isZero();

        Integer ativosRecentes = jdbcTemplate.queryForObject(
                """
                select count(*) from refresh_token
                where usuario_id = ? and revoked_at is null
                """,
                Integer.class,
                usuarioId);
        assertThat(ativosRecentes).isEqualTo(1);
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
        assertThat(cleared).containsIgnoringCase("Path=/auth");

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

    @Test
    void deveRecusarAccessJwtAposLogoutComBearer() {
        ResponseEntity<Map> login = restTemplate.postForEntity(
                "/auth/login",
                json(Map.of("email", email, "senha", SENHA)),
                Map.class);
        String accessToken = login.getBody().get("accessToken").toString();
        String cookie = refreshCookieValue(login);

        HttpHeaders autenticado = new HttpHeaders();
        autenticado.setBearerAuth(accessToken);
        ResponseEntity<Map> meAntes = restTemplate.exchange(
                "/me",
                HttpMethod.GET,
                new HttpEntity<>(autenticado),
                Map.class);
        assertThat(meAntes.getStatusCode()).isEqualTo(OK);

        HttpHeaders logoutHeaders = new HttpHeaders();
        logoutHeaders.add(HttpHeaders.COOKIE, "refresh_token=" + cookie);
        logoutHeaders.setBearerAuth(accessToken);
        ResponseEntity<Void> logout = restTemplate.exchange(
                "/auth/logout",
                HttpMethod.POST,
                new HttpEntity<>(null, logoutHeaders),
                Void.class);
        assertThat(logout.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<Map> meDepois = restTemplate.exchange(
                "/me",
                HttpMethod.GET,
                new HttpEntity<>(autenticado),
                Map.class);
        assertThat(meDepois.getStatusCode()).isEqualTo(UNAUTHORIZED);
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
        String aud = node.get("aud").isArray()
                ? node.get("aud").get(0).asText()
                : node.get("aud").asText();
        return Map.of(
                "sub", node.get("sub").asText(),
                "iss", node.get("iss").asText(),
                "aud", aud,
                "iat", node.get("iat").asLong(),
                "exp", node.get("exp").asLong()
        );
    }
}
