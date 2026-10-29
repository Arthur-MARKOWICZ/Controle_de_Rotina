package com.controlerotina.capacidade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.util.HashMap;
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
class CapacidadeOverrideIT {

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
        String email = "override-" + UUID.randomUUID() + "@email.com";
        assertThat(restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Dono Override", "email", email, "senha", SENHA)),
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
    void deveRetornar401AoCriarOverrideSemToken() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/capacidades/overrides",
                json(item("2026-10-08", 1)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
    }

    @Test
    void deveCriarOverrideUnitarioCom201() {
        ResponseEntity<List> response = restTemplate.exchange(
                "/capacidades/overrides",
                HttpMethod.POST,
                autenticadoJson(item("2026-10-08", 1)),
                List.class);

        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        assertThat(response.getBody()).isNotNull().hasSize(1);

        @SuppressWarnings("unchecked")
        Map<String, Object> criado = (Map<String, Object>) response.getBody().get(0);
        assertThat(criado.get("id")).isNotNull();
        assertThat(criado.get("data")).isEqualTo("2026-10-08");
        assertThat(criado.get("horas")).isEqualTo(1);
        assertThat(criado.get("ativo")).isEqualTo(true);
        assertThat(criado.get("createdAt")).isNotNull();
        assertThat(criado.get("updatedAt")).isNotNull();

        UUID id = UUID.fromString(criado.get("id").toString());
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select usuario_id, data, horas, ativo from capacidade_override where id = ?", id);
        assertThat(row.get("usuario_id")).isEqualTo(usuarioId);
        assertThat(row.get("data").toString()).isEqualTo("2026-10-08");
        assertThat(row.get("horas")).isEqualTo(1);
        assertThat(row.get("ativo")).isEqualTo(true);
    }

    @Test
    void devePermitirHorasZeroNoOverride() {
        ResponseEntity<List> response = restTemplate.exchange(
                "/capacidades/overrides",
                HttpMethod.POST,
                autenticadoJson(item("2026-10-09", 0)),
                List.class);

        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        @SuppressWarnings("unchecked")
        Map<String, Object> criado = (Map<String, Object>) response.getBody().get(0);
        assertThat(criado.get("horas")).isEqualTo(0);
    }

    @Test
    void deveCriarOverridesEmLoteCom201() {
        List<Map<String, Object>> lote = List.of(
                item("2026-10-08", 1),
                item("2026-10-09", 2),
                item("2026-10-10", 0)
        );

        ResponseEntity<List> response = restTemplate.exchange(
                "/capacidades/overrides",
                HttpMethod.POST,
                autenticadoJson(lote),
                List.class);

        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        assertThat(response.getBody()).hasSize(3);
        assertThat(response.getBody()).extracting(m -> ((Map<?, ?>) m).get("data"))
                .containsExactly("2026-10-08", "2026-10-09", "2026-10-10");
    }

    @Test
    void deveRejeitarHorasNegativasNoOverrideCom400() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/capacidades/overrides",
                HttpMethod.POST,
                autenticadoJson(item("2026-10-08", -1)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
    }

    @Test
    void deveRejeitarDataDuplicadaNoLoteCom400() {
        List<Map<String, Object>> lote = List.of(
                item("2026-10-08", 1),
                item("2026-10-08", 2)
        );

        ResponseEntity<Map> response = restTemplate.exchange(
                "/capacidades/overrides",
                HttpMethod.POST,
                autenticadoJson(lote),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from capacidade_override where usuario_id = ?",
                Integer.class,
                usuarioId)).isEqualTo(0);
    }

    @Test
    void deveRetornar409QuandoJaExisteOverrideAtivoNaMesmaData() {
        assertThat(restTemplate.exchange(
                "/capacidades/overrides",
                HttpMethod.POST,
                autenticadoJson(item("2026-10-08", 1)),
                List.class).getStatusCode()).isEqualTo(CREATED);

        ResponseEntity<Map> conflito = restTemplate.exchange(
                "/capacidades/overrides",
                HttpMethod.POST,
                autenticadoJson(item("2026-10-08", 2)),
                Map.class);

        assertThat(conflito.getStatusCode()).isEqualTo(CONFLICT);
    }

    @Test
    void devePermitirCriarMesmaDataQuandoOverrideAnteriorEstaInativo() {
        UUID inativoId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                insert into capacidade_override (id, usuario_id, data, horas, ativo, created_at, updated_at)
                values (?, ?, '2026-10-08', 3, false, now(), now())
                """,
                inativoId,
                usuarioId);

        ResponseEntity<List> response = restTemplate.exchange(
                "/capacidades/overrides",
                HttpMethod.POST,
                autenticadoJson(item("2026-10-08", 5)),
                List.class);

        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from capacidade_override where usuario_id = ? and data = '2026-10-08' and ativo = true",
                Integer.class,
                usuarioId)).isEqualTo(1);
    }

    @Test
    void deveListarSomenteOverridesDoUsuarioAutenticado() {
        criarOverrideAutenticado("2026-10-08", 1);

        String outroToken = tokenDeNovoUsuario();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(outroToken);
        restTemplate.exchange(
                "/capacidades/overrides",
                HttpMethod.POST,
                new HttpEntity<>(item("2026-10-08", 8), headers),
                List.class);

        ResponseEntity<List> response = restTemplate.exchange(
                "/capacidades/overrides",
                HttpMethod.GET,
                autenticado(),
                List.class);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(((Map<?, ?>) response.getBody().get(0)).get("data")).isEqualTo("2026-10-08");
        assertThat(((Map<?, ?>) response.getBody().get(0)).get("horas")).isEqualTo(1);
    }

    @Test
    void deveExcluirInativosDaListagemPadraoEPermitirFiltrosDeOverride() {
        UUID ativaId = criarOverrideAutenticado("2026-10-08", 1);
        UUID inativaId = criarOverrideAutenticado("2026-10-09", 2);
        jdbcTemplate.update("update capacidade_override set ativo = false where id = ?", inativaId);

        ResponseEntity<List> padrao = restTemplate.exchange(
                "/capacidades/overrides",
                HttpMethod.GET,
                autenticado(),
                List.class);
        assertThat(padrao.getBody()).extracting(m -> ((Map<?, ?>) m).get("data"))
                .containsExactly("2026-10-08");

        ResponseEntity<List> incluir = restTemplate.exchange(
                "/capacidades/overrides?incluirDeletadas=true",
                HttpMethod.GET,
                autenticado(),
                List.class);
        assertThat(incluir.getBody()).extracting(m -> ((Map<?, ?>) m).get("data"))
                .containsExactly("2026-10-08", "2026-10-09");

        ResponseEntity<List> somente = restTemplate.exchange(
                "/capacidades/overrides?somenteDeletadas=true",
                HttpMethod.GET,
                autenticado(),
                List.class);
        assertThat(somente.getBody()).hasSize(1);
        Map<?, ?> inativa = (Map<?, ?>) somente.getBody().get(0);
        assertThat(inativa.get("data")).isEqualTo("2026-10-09");
        assertThat(inativa.get("ativo")).isEqualTo(false);
        assertThat(inativa.get("id").toString()).isEqualTo(inativaId.toString());
        assertThat(ativaId).isNotNull();
    }

    @Test
    void deveFiltrarOverridesPorIntervaloDeDatas() {
        criarOverrideAutenticado("2026-10-07", 1);
        criarOverrideAutenticado("2026-10-08", 2);
        criarOverrideAutenticado("2026-10-10", 3);

        ResponseEntity<List> response = restTemplate.exchange(
                "/capacidades/overrides?dataDe=2026-10-08&dataAte=2026-10-09",
                HttpMethod.GET,
                autenticado(),
                List.class);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).extracting(m -> ((Map<?, ?>) m).get("data"))
                .containsExactly("2026-10-08");
    }

    @Test
    void deveBuscarOverridePorIdInclusiveInativo() {
        UUID ativaId = criarOverrideAutenticado("2026-10-08", 1);
        UUID inativaId = criarOverrideAutenticado("2026-10-09", 2);
        jdbcTemplate.update("update capacidade_override set ativo = false where id = ?", inativaId);

        ResponseEntity<Map> ativa = restTemplate.exchange(
                "/capacidades/overrides/" + ativaId,
                HttpMethod.GET,
                autenticado(),
                Map.class);
        assertThat(ativa.getStatusCode()).isEqualTo(OK);
        assertThat(ativa.getBody().get("data")).isEqualTo("2026-10-08");
        assertThat(ativa.getBody().get("ativo")).isEqualTo(true);

        ResponseEntity<Map> inativa = restTemplate.exchange(
                "/capacidades/overrides/" + inativaId,
                HttpMethod.GET,
                autenticado(),
                Map.class);
        assertThat(inativa.getStatusCode()).isEqualTo(OK);
        assertThat(inativa.getBody().get("ativo")).isEqualTo(false);
    }

    @Test
    void deveRetornar404AoBuscarOverrideDeOutroUsuarioOuInexistente() {
        UUID id = criarOverrideAutenticado("2026-10-08", 1);

        assertThat(restTemplate.exchange(
                "/capacidades/overrides/" + id,
                HttpMethod.GET,
                autenticadoCom(tokenDeNovoUsuario()),
                Map.class).getStatusCode()).isEqualTo(NOT_FOUND);

        assertThat(restTemplate.exchange(
                "/capacidades/overrides/" + UUID.randomUUID(),
                HttpMethod.GET,
                autenticado(),
                Map.class).getStatusCode()).isEqualTo(NOT_FOUND);
    }

    @Test
    void deveAtualizarOverrideParcialmenteComPatch() {
        UUID id = criarOverrideAutenticado("2026-10-08", 1);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/capacidades/overrides/" + id,
                HttpMethod.PATCH,
                autenticadoJson(Map.of("horas", 4, "data", "2026-10-11")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody().get("horas")).isEqualTo(4);
        assertThat(response.getBody().get("data")).isEqualTo("2026-10-11");
        assertThat(response.getBody().get("ativo")).isEqualTo(true);
    }

    @Test
    void deveRetornar409AoMudarDataParaUmaJaAtiva() {
        UUID id = criarOverrideAutenticado("2026-10-08", 1);
        criarOverrideAutenticado("2026-10-09", 2);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/capacidades/overrides/" + id,
                HttpMethod.PATCH,
                autenticadoJson(Map.of("data", "2026-10-09")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(CONFLICT);
    }

    @Test
    void devePermitirPatchEmOverrideInativoSemRestaurar() {
        UUID id = criarOverrideAutenticado("2026-10-08", 1);
        jdbcTemplate.update("update capacidade_override set ativo = false where id = ?", id);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/capacidades/overrides/" + id,
                HttpMethod.PATCH,
                autenticadoJson(Map.of("horas", 8)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody().get("horas")).isEqualTo(8);
        assertThat(response.getBody().get("ativo")).isEqualTo(false);
    }

    @Test
    void deveFazerSoftDeleteDeOverrideCom204() {
        UUID id = criarOverrideAutenticado("2026-10-08", 1);

        ResponseEntity<Void> deleted = restTemplate.exchange(
                "/capacidades/overrides/" + id,
                HttpMethod.DELETE,
                autenticado(),
                Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(NO_CONTENT);

        ResponseEntity<Map> detalhe = restTemplate.exchange(
                "/capacidades/overrides/" + id,
                HttpMethod.GET,
                autenticado(),
                Map.class);
        assertThat(detalhe.getBody().get("ativo")).isEqualTo(false);
    }

    @Test
    void deveRetornar404AoDeletarOverrideJaInativoOuDeOutroUsuario() {
        UUID id = criarOverrideAutenticado("2026-10-08", 1);
        assertThat(restTemplate.exchange(
                "/capacidades/overrides/" + id,
                HttpMethod.DELETE,
                autenticado(),
                Void.class).getStatusCode()).isEqualTo(NO_CONTENT);

        assertThat(restTemplate.exchange(
                "/capacidades/overrides/" + id,
                HttpMethod.DELETE,
                autenticado(),
                Void.class).getStatusCode()).isEqualTo(NOT_FOUND);

        UUID outra = criarOverrideAutenticado("2026-10-09", 2);
        assertThat(restTemplate.exchange(
                "/capacidades/overrides/" + outra,
                HttpMethod.DELETE,
                autenticadoCom(tokenDeNovoUsuario()),
                Void.class).getStatusCode()).isEqualTo(NOT_FOUND);
    }

    @Test
    void deveRestaurarOverrideInativo() {
        UUID id = criarOverrideAutenticado("2026-10-08", 1);
        assertThat(restTemplate.exchange(
                "/capacidades/overrides/" + id,
                HttpMethod.DELETE,
                autenticado(),
                Void.class).getStatusCode()).isEqualTo(NO_CONTENT);

        ResponseEntity<Map> restaurada = restTemplate.exchange(
                "/capacidades/overrides/" + id + "/restore",
                HttpMethod.POST,
                autenticado(),
                Map.class);

        assertThat(restaurada.getStatusCode()).isEqualTo(OK);
        assertThat(restaurada.getBody().get("ativo")).isEqualTo(true);

        assertThat(restTemplate.exchange(
                "/capacidades/overrides/" + id + "/restore",
                HttpMethod.POST,
                autenticado(),
                Map.class).getStatusCode()).isEqualTo(NOT_FOUND);
    }

    @Test
    void deveRetornar409AoRestaurarOverrideQuandoJaExisteAtivoNaMesmaData() {
        UUID inativaId = criarOverrideAutenticado("2026-10-08", 1);
        assertThat(restTemplate.exchange(
                "/capacidades/overrides/" + inativaId,
                HttpMethod.DELETE,
                autenticado(),
                Void.class).getStatusCode()).isEqualTo(NO_CONTENT);

        criarOverrideAutenticado("2026-10-08", 5);

        ResponseEntity<Map> conflito = restTemplate.exchange(
                "/capacidades/overrides/" + inativaId + "/restore",
                HttpMethod.POST,
                autenticado(),
                Map.class);

        assertThat(conflito.getStatusCode()).isEqualTo(CONFLICT);
        assertThat(jdbcTemplate.queryForObject(
                "select ativo from capacidade_override where id = ?",
                Boolean.class,
                inativaId)).isFalse();
    }

    @Test
    void deveRetornar401AoConsultarResolvidaSemToken() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/capacidades/resolvida?dataDe=2026-10-08&dataAte=2026-10-08",
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
    }

    @Test
    void deveRejeitarResolvidaSemDatasObrigatorias() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/capacidades/resolvida",
                HttpMethod.GET,
                autenticado(),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
    }

    @Test
    void deveRejeitarIntervaloInvalidoOuMaiorQue31Dias() {
        assertThat(restTemplate.exchange(
                "/capacidades/resolvida?dataDe=2026-10-10&dataAte=2026-10-08",
                HttpMethod.GET,
                autenticado(),
                Map.class).getStatusCode()).isEqualTo(BAD_REQUEST);

        assertThat(restTemplate.exchange(
                "/capacidades/resolvida?dataDe=2026-10-01&dataAte=2026-11-01",
                HttpMethod.GET,
                autenticado(),
                Map.class).getStatusCode()).isEqualTo(BAD_REQUEST);
    }

    @Test
    void deveResolverCapacidadeComOverrideSemanalEVazio() {
        // 2026-10-08 = quinta; 2026-10-09 = sexta; 2026-10-10 = sábado
        criarSemanalAutenticada("QUINTA", 4);
        criarSemanalAutenticada("SEXTA", 6);
        criarOverrideAutenticado("2026-10-08", 1);

        ResponseEntity<List> response = restTemplate.exchange(
                "/capacidades/resolvida?dataDe=2026-10-08&dataAte=2026-10-10",
                HttpMethod.GET,
                autenticado(),
                List.class);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).hasSize(3);

        Map<?, ?> dia08 = (Map<?, ?>) response.getBody().get(0);
        assertThat(dia08.get("data")).isEqualTo("2026-10-08");
        assertThat(dia08.get("horas")).isEqualTo(1);
        assertThat(dia08.get("origem")).isEqualTo("OVERRIDE");

        Map<?, ?> dia09 = (Map<?, ?>) response.getBody().get(1);
        assertThat(dia09.get("data")).isEqualTo("2026-10-09");
        assertThat(dia09.get("horas")).isEqualTo(6);
        assertThat(dia09.get("origem")).isEqualTo("SEMANAL");

        Map<?, ?> dia10 = (Map<?, ?>) response.getBody().get(2);
        assertThat(dia10.get("data")).isEqualTo("2026-10-10");
        assertThat(dia10.get("horas")).isNull();
        assertThat(dia10.get("origem")).isEqualTo("VAZIO");
    }

    @Test
    void deveIgnorarRegistrosInativosNaResolucao() {
        criarSemanalAutenticada("QUINTA", 4);
        UUID semanalInativa = criarSemanalAutenticada("SEXTA", 6);
        jdbcTemplate.update("update capacidade_semanal set ativo = false where id = ?", semanalInativa);

        UUID overrideInativo = criarOverrideAutenticado("2026-10-08", 1);
        jdbcTemplate.update("update capacidade_override set ativo = false where id = ?", overrideInativo);

        ResponseEntity<List> response = restTemplate.exchange(
                "/capacidades/resolvida?dataDe=2026-10-08&dataAte=2026-10-09",
                HttpMethod.GET,
                autenticado(),
                List.class);

        assertThat(response.getBody()).hasSize(2);
        assertThat(((Map<?, ?>) response.getBody().get(0)).get("origem")).isEqualTo("SEMANAL");
        assertThat(((Map<?, ?>) response.getBody().get(0)).get("horas")).isEqualTo(4);
        assertThat(((Map<?, ?>) response.getBody().get(1)).get("origem")).isEqualTo("VAZIO");
        assertThat(((Map<?, ?>) response.getBody().get(1)).get("horas")).isNull();
    }

    @Test
    void deveResolverSomenteCapacidadeDoUsuarioAutenticado() {
        criarSemanalAutenticada("QUINTA", 4);

        String outroToken = tokenDeNovoUsuario();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(outroToken);
        restTemplate.exchange(
                "/capacidades/semanal",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("diaSemana", "QUINTA", "horas", 9), headers),
                List.class);
        restTemplate.exchange(
                "/capacidades/overrides",
                HttpMethod.POST,
                new HttpEntity<>(item("2026-10-08", 2), headers),
                List.class);

        ResponseEntity<List> response = restTemplate.exchange(
                "/capacidades/resolvida?dataDe=2026-10-08&dataAte=2026-10-08",
                HttpMethod.GET,
                autenticado(),
                List.class);

        assertThat(response.getBody()).hasSize(1);
        assertThat(((Map<?, ?>) response.getBody().get(0)).get("origem")).isEqualTo("SEMANAL");
        assertThat(((Map<?, ?>) response.getBody().get(0)).get("horas")).isEqualTo(4);
    }

    private UUID criarOverrideAutenticado(String data, int horas) {
        ResponseEntity<List> response = restTemplate.exchange(
                "/capacidades/overrides",
                HttpMethod.POST,
                autenticadoJson(item(data, horas)),
                List.class);
        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        return UUID.fromString(((Map<?, ?>) response.getBody().get(0)).get("id").toString());
    }

    private UUID criarSemanalAutenticada(String diaSemana, int horas) {
        ResponseEntity<List> response = restTemplate.exchange(
                "/capacidades/semanal",
                HttpMethod.POST,
                autenticadoJson(Map.of("diaSemana", diaSemana, "horas", horas)),
                List.class);
        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        return UUID.fromString(((Map<?, ?>) response.getBody().get(0)).get("id").toString());
    }

    private String tokenDeNovoUsuario() {
        String email = "outro-ovr-" + UUID.randomUUID() + "@email.com";
        restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Outro", "email", email, "senha", SENHA)),
                Map.class);
        return restTemplate.postForEntity(
                "/auth/login",
                json(Map.of("email", email, "senha", SENHA)),
                Map.class).getBody().get("accessToken").toString();
    }

    private Map<String, Object> item(String data, int horas) {
        Map<String, Object> body = new HashMap<>();
        body.put("data", data);
        body.put("horas", horas);
        return body;
    }

    private HttpEntity<Void> autenticado() {
        return autenticadoCom(accessToken);
    }

    private HttpEntity<Void> autenticadoCom(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    private HttpEntity<?> autenticadoJson(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        return new HttpEntity<>(body, headers);
    }

    private static HttpEntity<Map<String, ?>> json(Map<String, ?> body) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
