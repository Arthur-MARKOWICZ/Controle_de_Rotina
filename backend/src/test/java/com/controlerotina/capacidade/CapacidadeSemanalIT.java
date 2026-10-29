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
class CapacidadeSemanalIT {

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
        String email = "capacidade-" + UUID.randomUUID() + "@email.com";
        assertThat(restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Dono Capacidade", "email", email, "senha", SENHA)),
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
                "/capacidades/semanal",
                json(item("SEGUNDA", 4)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
    }

    @Test
    void deveCriarCapacidadeSemanalUnitariaCom201() {
        ResponseEntity<List> response = restTemplate.exchange(
                "/capacidades/semanal",
                HttpMethod.POST,
                autenticadoJson(item("SEGUNDA", 4)),
                List.class);

        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        assertThat(response.getBody()).isNotNull().hasSize(1);

        @SuppressWarnings("unchecked")
        Map<String, Object> criado = (Map<String, Object>) response.getBody().get(0);
        assertThat(criado.get("id")).isNotNull();
        assertThat(criado.get("diaSemana")).isEqualTo("SEGUNDA");
        assertThat(criado.get("horas")).isEqualTo(4);
        assertThat(criado.get("ativo")).isEqualTo(true);
        assertThat(criado.get("createdAt")).isNotNull();
        assertThat(criado.get("updatedAt")).isNotNull();

        UUID id = UUID.fromString(criado.get("id").toString());
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select usuario_id, dia_semana, horas, ativo from capacidade_semanal where id = ?", id);
        assertThat(row.get("usuario_id")).isEqualTo(usuarioId);
        assertThat(row.get("dia_semana")).isEqualTo("SEGUNDA");
        assertThat(row.get("horas")).isEqualTo(4);
        assertThat(row.get("ativo")).isEqualTo(true);
    }

    @Test
    void devePermitirHorasZero() {
        ResponseEntity<List> response = restTemplate.exchange(
                "/capacidades/semanal",
                HttpMethod.POST,
                autenticadoJson(item("DOMINGO", 0)),
                List.class);

        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        @SuppressWarnings("unchecked")
        Map<String, Object> criado = (Map<String, Object>) response.getBody().get(0);
        assertThat(criado.get("horas")).isEqualTo(0);
    }

    @Test
    void deveCriarCapacidadeSemanalEmLoteCom201() {
        List<Map<String, Object>> lote = List.of(
                item("SEGUNDA", 4),
                item("TERCA", 2),
                item("QUARTA", 0)
        );

        ResponseEntity<List> response = restTemplate.exchange(
                "/capacidades/semanal",
                HttpMethod.POST,
                autenticadoJson(lote),
                List.class);

        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        assertThat(response.getBody()).hasSize(3);
        assertThat(response.getBody()).extracting(m -> ((Map<?, ?>) m).get("diaSemana"))
                .containsExactly("SEGUNDA", "TERCA", "QUARTA");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from capacidade_semanal where usuario_id = ? and ativo = true",
                Integer.class,
                usuarioId)).isEqualTo(3);
    }

    @Test
    void deveRejeitarHorasNegativasCom400() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/capacidades/semanal",
                HttpMethod.POST,
                autenticadoJson(item("SEGUNDA", -1)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
    }

    @Test
    void deveRejeitarDiaSemanaInvalidoCom400() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/capacidades/semanal",
                HttpMethod.POST,
                autenticadoJson(item("FERIADO", 2)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
    }

    @Test
    void deveRejeitarDiaSemanaDuplicadoNoLoteCom400() {
        List<Map<String, Object>> lote = List.of(
                item("SEGUNDA", 4),
                item("SEGUNDA", 2)
        );

        ResponseEntity<Map> response = restTemplate.exchange(
                "/capacidades/semanal",
                HttpMethod.POST,
                autenticadoJson(lote),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from capacidade_semanal where usuario_id = ?",
                Integer.class,
                usuarioId)).isEqualTo(0);
    }

    @Test
    void deveRetornar409QuandoJaExisteAtivoNoMesmoDiaSemana() {
        assertThat(restTemplate.exchange(
                "/capacidades/semanal",
                HttpMethod.POST,
                autenticadoJson(item("SEGUNDA", 4)),
                List.class).getStatusCode()).isEqualTo(CREATED);

        ResponseEntity<Map> conflito = restTemplate.exchange(
                "/capacidades/semanal",
                HttpMethod.POST,
                autenticadoJson(item("SEGUNDA", 2)),
                Map.class);

        assertThat(conflito.getStatusCode()).isEqualTo(CONFLICT);
    }

    @Test
    void devePermitirCriarMesmoDiaQuandoRegistroAnteriorEstaInativo() {
        UUID inativoId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                insert into capacidade_semanal (id, usuario_id, dia_semana, horas, ativo, created_at, updated_at)
                values (?, ?, 'SEGUNDA', 3, false, now(), now())
                """,
                inativoId,
                usuarioId);

        ResponseEntity<List> response = restTemplate.exchange(
                "/capacidades/semanal",
                HttpMethod.POST,
                autenticadoJson(item("SEGUNDA", 5)),
                List.class);

        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from capacidade_semanal where usuario_id = ? and dia_semana = 'SEGUNDA' and ativo = true",
                Integer.class,
                usuarioId)).isEqualTo(1);
    }

    @Test
    void deveRetornar401AoListarSemToken() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/capacidades/semanal", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
    }

    @Test
    void deveListarSomenteCapacidadesDoUsuarioAutenticado() {
        criarSemanalAutenticada("SEGUNDA", 4);

        String outroToken = tokenDeNovoUsuario();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(outroToken);
        restTemplate.exchange(
                "/capacidades/semanal",
                HttpMethod.POST,
                new HttpEntity<>(item("SEGUNDA", 8), headers),
                List.class);

        ResponseEntity<List> response = restTemplate.exchange(
                "/capacidades/semanal",
                HttpMethod.GET,
                autenticado(),
                List.class);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(((Map<?, ?>) response.getBody().get(0)).get("diaSemana")).isEqualTo("SEGUNDA");
        assertThat(((Map<?, ?>) response.getBody().get(0)).get("horas")).isEqualTo(4);
    }

    @Test
    void deveOrdenarPorOrdemDoEnumSegundaADomingo() {
        criarSemanalAutenticada("DOMINGO", 1);
        criarSemanalAutenticada("SEGUNDA", 2);
        criarSemanalAutenticada("QUARTA", 3);

        ResponseEntity<List> response = restTemplate.exchange(
                "/capacidades/semanal",
                HttpMethod.GET,
                autenticado(),
                List.class);

        assertThat(response.getBody()).extracting(m -> ((Map<?, ?>) m).get("diaSemana"))
                .containsExactly("SEGUNDA", "QUARTA", "DOMINGO");
    }

    @Test
    void deveExcluirInativosDaListagemPadraoEPermitirFiltros() {
        UUID ativaId = criarSemanalAutenticada("SEGUNDA", 4);
        UUID inativaId = criarSemanalAutenticada("TERCA", 2);
        jdbcTemplate.update("update capacidade_semanal set ativo = false where id = ?", inativaId);

        ResponseEntity<List> padrao = restTemplate.exchange(
                "/capacidades/semanal",
                HttpMethod.GET,
                autenticado(),
                List.class);
        assertThat(padrao.getBody()).extracting(m -> ((Map<?, ?>) m).get("diaSemana"))
                .containsExactly("SEGUNDA");

        ResponseEntity<List> incluir = restTemplate.exchange(
                "/capacidades/semanal?incluirDeletadas=true",
                HttpMethod.GET,
                autenticado(),
                List.class);
        assertThat(incluir.getBody()).extracting(m -> ((Map<?, ?>) m).get("diaSemana"))
                .containsExactly("SEGUNDA", "TERCA");

        ResponseEntity<List> somente = restTemplate.exchange(
                "/capacidades/semanal?somenteDeletadas=true",
                HttpMethod.GET,
                autenticado(),
                List.class);
        assertThat(somente.getBody()).hasSize(1);
        Map<?, ?> inativa = (Map<?, ?>) somente.getBody().get(0);
        assertThat(inativa.get("diaSemana")).isEqualTo("TERCA");
        assertThat(inativa.get("ativo")).isEqualTo(false);
        assertThat(inativa.get("id").toString()).isEqualTo(inativaId.toString());
        assertThat(ativaId).isNotNull();
    }

    @Test
    void deveBuscarCapacidadePorIdInclusiveInativa() {
        UUID ativaId = criarSemanalAutenticada("SEGUNDA", 4);
        UUID inativaId = criarSemanalAutenticada("TERCA", 2);
        jdbcTemplate.update("update capacidade_semanal set ativo = false where id = ?", inativaId);

        ResponseEntity<Map> ativa = restTemplate.exchange(
                "/capacidades/semanal/" + ativaId,
                HttpMethod.GET,
                autenticado(),
                Map.class);
        assertThat(ativa.getStatusCode()).isEqualTo(OK);
        assertThat(ativa.getBody().get("diaSemana")).isEqualTo("SEGUNDA");
        assertThat(ativa.getBody().get("horas")).isEqualTo(4);
        assertThat(ativa.getBody().get("ativo")).isEqualTo(true);

        ResponseEntity<Map> inativa = restTemplate.exchange(
                "/capacidades/semanal/" + inativaId,
                HttpMethod.GET,
                autenticado(),
                Map.class);
        assertThat(inativa.getStatusCode()).isEqualTo(OK);
        assertThat(inativa.getBody().get("diaSemana")).isEqualTo("TERCA");
        assertThat(inativa.getBody().get("ativo")).isEqualTo(false);
    }

    @Test
    void deveRetornar404AoBuscarCapacidadeDeOutroUsuarioOuInexistente() {
        UUID id = criarSemanalAutenticada("SEGUNDA", 4);

        ResponseEntity<Map> alheia = restTemplate.exchange(
                "/capacidades/semanal/" + id,
                HttpMethod.GET,
                autenticadoCom(tokenDeNovoUsuario()),
                Map.class);
        assertThat(alheia.getStatusCode()).isEqualTo(NOT_FOUND);

        ResponseEntity<Map> inexistente = restTemplate.exchange(
                "/capacidades/semanal/" + UUID.randomUUID(),
                HttpMethod.GET,
                autenticado(),
                Map.class);
        assertThat(inexistente.getStatusCode()).isEqualTo(NOT_FOUND);
    }

    @Test
    void deveAtualizarParcialmenteComPatch() {
        UUID id = criarSemanalAutenticada("SEGUNDA", 4);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/capacidades/semanal/" + id,
                HttpMethod.PATCH,
                autenticadoJson(Map.of("horas", 6, "diaSemana", "QUARTA")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody().get("horas")).isEqualTo(6);
        assertThat(response.getBody().get("diaSemana")).isEqualTo("QUARTA");
        assertThat(response.getBody().get("ativo")).isEqualTo(true);
    }

    @Test
    void deveRetornar409AoMudarDiaSemanaParaUmJaAtivo() {
        UUID id = criarSemanalAutenticada("SEGUNDA", 4);
        criarSemanalAutenticada("TERCA", 2);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/capacidades/semanal/" + id,
                HttpMethod.PATCH,
                autenticadoJson(Map.of("diaSemana", "TERCA")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(CONFLICT);
    }

    @Test
    void devePermitirPatchEmInativoSemRestaurar() {
        UUID id = criarSemanalAutenticada("SEGUNDA", 4);
        jdbcTemplate.update("update capacidade_semanal set ativo = false where id = ?", id);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/capacidades/semanal/" + id,
                HttpMethod.PATCH,
                autenticadoJson(Map.of("horas", 8)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody().get("horas")).isEqualTo(8);
        assertThat(response.getBody().get("ativo")).isEqualTo(false);
    }

    @Test
    void deveRejeitarHorasNegativasNoPatchCom400() {
        UUID id = criarSemanalAutenticada("SEGUNDA", 4);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/capacidades/semanal/" + id,
                HttpMethod.PATCH,
                autenticadoJson(Map.of("horas", -1)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
    }

    @Test
    void deveFazerSoftDeleteCom204() {
        UUID id = criarSemanalAutenticada("SEGUNDA", 4);

        ResponseEntity<Void> deleted = restTemplate.exchange(
                "/capacidades/semanal/" + id,
                HttpMethod.DELETE,
                autenticado(),
                Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(NO_CONTENT);

        ResponseEntity<Map> detalhe = restTemplate.exchange(
                "/capacidades/semanal/" + id,
                HttpMethod.GET,
                autenticado(),
                Map.class);
        assertThat(detalhe.getStatusCode()).isEqualTo(OK);
        assertThat(detalhe.getBody().get("ativo")).isEqualTo(false);
    }

    @Test
    void deveRetornar404AoDeletarJaInativoOuDeOutroUsuario() {
        UUID id = criarSemanalAutenticada("SEGUNDA", 4);
        assertThat(restTemplate.exchange(
                "/capacidades/semanal/" + id,
                HttpMethod.DELETE,
                autenticado(),
                Void.class).getStatusCode()).isEqualTo(NO_CONTENT);

        assertThat(restTemplate.exchange(
                "/capacidades/semanal/" + id,
                HttpMethod.DELETE,
                autenticado(),
                Void.class).getStatusCode()).isEqualTo(NOT_FOUND);

        UUID outra = criarSemanalAutenticada("TERCA", 2);
        assertThat(restTemplate.exchange(
                "/capacidades/semanal/" + outra,
                HttpMethod.DELETE,
                autenticadoCom(tokenDeNovoUsuario()),
                Void.class).getStatusCode()).isEqualTo(NOT_FOUND);
    }

    @Test
    void deveRestaurarCapacidadeInativa() {
        UUID id = criarSemanalAutenticada("SEGUNDA", 4);
        assertThat(restTemplate.exchange(
                "/capacidades/semanal/" + id,
                HttpMethod.DELETE,
                autenticado(),
                Void.class).getStatusCode()).isEqualTo(NO_CONTENT);

        ResponseEntity<Map> restaurada = restTemplate.exchange(
                "/capacidades/semanal/" + id + "/restore",
                HttpMethod.POST,
                autenticado(),
                Map.class);

        assertThat(restaurada.getStatusCode()).isEqualTo(OK);
        assertThat(restaurada.getBody().get("ativo")).isEqualTo(true);

        assertThat(restTemplate.exchange(
                "/capacidades/semanal/" + id + "/restore",
                HttpMethod.POST,
                autenticado(),
                Map.class).getStatusCode()).isEqualTo(NOT_FOUND);
    }

    @Test
    void deveRetornar409AoRestaurarQuandoJaExisteAtivoNoMesmoDia() {
        UUID inativaId = criarSemanalAutenticada("SEGUNDA", 3);
        assertThat(restTemplate.exchange(
                "/capacidades/semanal/" + inativaId,
                HttpMethod.DELETE,
                autenticado(),
                Void.class).getStatusCode()).isEqualTo(NO_CONTENT);

        criarSemanalAutenticada("SEGUNDA", 5);

        ResponseEntity<Map> conflito = restTemplate.exchange(
                "/capacidades/semanal/" + inativaId + "/restore",
                HttpMethod.POST,
                autenticado(),
                Map.class);

        assertThat(conflito.getStatusCode()).isEqualTo(CONFLICT);
        assertThat(jdbcTemplate.queryForObject(
                "select ativo from capacidade_semanal where id = ?",
                Boolean.class,
                inativaId)).isFalse();
    }

    private UUID criarSemanalAutenticada(String diaSemana, int horas) {
        ResponseEntity<List> response = restTemplate.exchange(
                "/capacidades/semanal",
                HttpMethod.POST,
                autenticadoJson(item(diaSemana, horas)),
                List.class);
        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        return UUID.fromString(((Map<?, ?>) response.getBody().get(0)).get("id").toString());
    }

    private String tokenDeNovoUsuario() {
        String email = "outro-" + UUID.randomUUID() + "@email.com";
        restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Outro", "email", email, "senha", SENHA)),
                Map.class);
        return restTemplate.postForEntity(
                "/auth/login",
                json(Map.of("email", email, "senha", SENHA)),
                Map.class).getBody().get("accessToken").toString();
    }

    private Map<String, Object> item(String diaSemana, int horas) {
        Map<String, Object> body = new HashMap<>();
        body.put("diaSemana", diaSemana);
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
