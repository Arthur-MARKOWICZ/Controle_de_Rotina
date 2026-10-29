package com.controlerotina.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.time.Instant;
import java.time.LocalDate;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.controlerotina.capacidade.entity.CapacidadeOverride;
import com.controlerotina.capacidade.repository.CapacidadeOverrideRepository;
import com.controlerotina.planner.entity.Plano;
import com.controlerotina.planner.service.PlannerGeracaoService;
import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;
import com.controlerotina.tarefa.repository.TarefaRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class PlanoAprovacaoIT {

    private static final String SENHA = "Senha@123";
    private static final LocalDate DIA_D = LocalDate.of(2026, 10, 20);
    private static final Instant AGORA = Instant.parse("2026-10-20T12:00:00Z");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    CapacidadeOverrideRepository capacidadeOverrideRepository;

    @Autowired
    TarefaRepository tarefaRepository;

    @Autowired
    PlannerGeracaoService plannerGeracaoService;

    private String accessToken;
    private UUID usuarioId;

    @BeforeEach
    void cadastrarELogar() {
        jdbcTemplate.update("delete from bloco_plano");
        jdbcTemplate.update("delete from plano");
        jdbcTemplate.update("delete from tarefa");
        jdbcTemplate.update("delete from refresh_token");
        jdbcTemplate.update("delete from capacidade_override");
        jdbcTemplate.update("delete from capacidade_semanal");
        jdbcTemplate.update("delete from xp_evento");
        jdbcTemplate.update("delete from usuario");

        String email = "plano-aprovar-" + UUID.randomUUID() + "@email.com";
        assertThat(restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Dono Aprovar", "email", email, "senha", SENHA)),
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
    void deveRetornar401AoAprovarSemToken() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/planos/" + UUID.randomUUID() + "/aprovar",
                null,
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
    }

    @Test
    void deveRetornar404QuandoPlanoNaoExiste() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/planos/" + UUID.randomUUID() + "/aprovar",
                HttpMethod.POST,
                autenticado(),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND);
    }

    @Test
    void deveAprovarRascunhoCorrenteCom200() {
        salvarOverride(2);
        salvarTarefa("Tarefa", Prioridade.MEDIA, 60, AGORA);
        Plano plano = plannerGeracaoService.gerar(usuarioId, DIA_D);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/planos/" + plano.getId() + "/aprovar",
                HttpMethod.POST,
                autenticado(),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("id")).isEqualTo(plano.getId().toString());
        assertThat(response.getBody().get("status")).isEqualTo("APROVADO");
        assertThat(response.getBody().get("versao")).isEqualTo(1);
    }

    @Test
    void deveRetornar409AoAprovarNovamente() {
        salvarOverride(2);
        salvarTarefa("Tarefa", Prioridade.MEDIA, 60, AGORA);
        Plano plano = plannerGeracaoService.gerar(usuarioId, DIA_D);

        assertThat(restTemplate.exchange(
                "/planos/" + plano.getId() + "/aprovar",
                HttpMethod.POST,
                autenticado(),
                Map.class).getStatusCode()).isEqualTo(OK);

        ResponseEntity<Map> segunda = restTemplate.exchange(
                "/planos/" + plano.getId() + "/aprovar",
                HttpMethod.POST,
                autenticado(),
                Map.class);

        assertThat(segunda.getStatusCode()).isEqualTo(CONFLICT);
    }

    @Test
    void deveRetornar409AoAprovarVersaoQueNaoECorrente() {
        salvarOverride(2);
        salvarTarefa("Tarefa", Prioridade.MEDIA, 60, AGORA);
        Plano v1 = plannerGeracaoService.gerar(usuarioId, DIA_D);
        plannerGeracaoService.gerar(usuarioId, DIA_D);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/planos/" + v1.getId() + "/aprovar",
                HttpMethod.POST,
                autenticado(),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(CONFLICT);
    }

    @Test
    void deveBloquearNovaGeracaoAposAprovacao() {
        salvarOverride(2);
        salvarTarefa("Tarefa", Prioridade.MEDIA, 60, AGORA);
        Plano plano = plannerGeracaoService.gerar(usuarioId, DIA_D);

        assertThat(restTemplate.exchange(
                "/planos/" + plano.getId() + "/aprovar",
                HttpMethod.POST,
                autenticado(),
                Map.class).getStatusCode()).isEqualTo(OK);

        ResponseEntity<Map> gerar = restTemplate.exchange(
                "/planos/gerar?data=" + DIA_D,
                HttpMethod.POST,
                autenticado(),
                Map.class);

        assertThat(gerar.getStatusCode()).isEqualTo(CONFLICT);
    }

    private void salvarOverride(int horas) {
        capacidadeOverrideRepository.save(new CapacidadeOverride(
                UUID.randomUUID(),
                usuarioId,
                DIA_D,
                horas,
                true,
                AGORA,
                AGORA
        ));
    }

    private Tarefa salvarTarefa(String titulo, Prioridade prioridade, int duracaoMinutos, Instant createdAt) {
        Instant prazo = DIA_D.plusDays(5).atTime(12, 0)
                .atZone(java.time.ZoneId.of("America/Sao_Paulo"))
                .toInstant();
        return tarefaRepository.save(new Tarefa(
                UUID.randomUUID(),
                usuarioId,
                titulo,
                "descricao",
                prioridade,
                prazo,
                StatusTarefa.PENDENTE,
                duracaoMinutos,
                createdAt,
                createdAt
        ));
    }

    private HttpEntity<Void> autenticado() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return new HttpEntity<>(headers);
    }

    private static HttpEntity<Map<String, ?>> json(Map<String, ?> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(new HashMap<>(body), headers);
    }
}
