package com.controlerotina.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.time.Instant;
import java.time.LocalDate;
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

import com.controlerotina.capacidade.entity.CapacidadeOverride;
import com.controlerotina.capacidade.repository.CapacidadeOverrideRepository;
import com.controlerotina.planner.entity.Plano;
import com.controlerotina.planner.entity.StatusPlano;
import com.controlerotina.planner.service.PlannerGeracaoService;
import com.controlerotina.planner.service.PlanoAjusteService;
import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;
import com.controlerotina.tarefa.repository.TarefaRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class PlanoAjusteAddIT {

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

        String email = "plano-add-" + UUID.randomUUID() + "@email.com";
        assertThat(restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Dono Add", "email", email, "senha", SENHA)),
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
    void deveRetornar401AoAdicionarSemToken() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/planos/" + UUID.randomUUID() + "/blocos",
                json(Map.of("tarefaId", UUID.randomUUID().toString())),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
    }

    @Test
    void deveRetornar400QuandoTarefaIdAusente() {
        salvarOverride(2);
        Plano plano = plannerGeracaoService.gerar(usuarioId, DIA_D);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/planos/" + plano.getId() + "/blocos",
                HttpMethod.POST,
                autenticadoJson(Map.of()),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
    }

    @Test
    void deveRetornar404QuandoPlanoNaoExiste() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/planos/" + UUID.randomUUID() + "/blocos",
                HttpMethod.POST,
                autenticadoJson(Map.of("tarefaId", UUID.randomUUID().toString())),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND);
    }

    @Test
    void deveRetornar409QuandoPlanoNaoERascunhoCorrente() {
        salvarOverride(2);
        salvarTarefa("Ja no plano", Prioridade.MEDIA, StatusTarefa.PENDENTE, 60, AGORA);
        Plano v1 = plannerGeracaoService.gerar(usuarioId, DIA_D);
        Plano v2 = plannerGeracaoService.gerar(usuarioId, DIA_D);
        Tarefa extra = salvarTarefa("Extra", Prioridade.BAIXA, StatusTarefa.PENDENTE, 60, AGORA);

        ResponseEntity<Map> versaoAntiga = restTemplate.exchange(
                "/planos/" + v1.getId() + "/blocos",
                HttpMethod.POST,
                autenticadoJson(Map.of("tarefaId", extra.getId().toString())),
                Map.class);
        assertThat(versaoAntiga.getStatusCode()).isEqualTo(CONFLICT);

        jdbcTemplate.update(
                "update plano set status = ? where id = ?",
                StatusPlano.APROVADO.name(),
                v2.getId()
        );

        Tarefa outra = salvarTarefa("Outra", Prioridade.BAIXA, StatusTarefa.PENDENTE, 60, AGORA);
        ResponseEntity<Map> aprovado = restTemplate.exchange(
                "/planos/" + v2.getId() + "/blocos",
                HttpMethod.POST,
                autenticadoJson(Map.of("tarefaId", outra.getId().toString())),
                Map.class);
        assertThat(aprovado.getStatusCode()).isEqualTo(CONFLICT);
    }

    @Test
    void deveAdicionarBlocoComDuracaoTotalE201() {
        salvarOverride(4);
        Tarefa noPlano = salvarTarefa("No plano", Prioridade.ALTA, StatusTarefa.PENDENTE, 60, AGORA);
        Plano plano = plannerGeracaoService.gerar(usuarioId, DIA_D);
        Tarefa nova = salvarTarefa("Nova", Prioridade.MEDIA, StatusTarefa.PENDENTE, 120, AGORA);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/planos/" + plano.getId() + "/blocos",
                HttpMethod.POST,
                autenticadoJson(Map.of("tarefaId", nova.getId().toString())),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("temConflito")).isEqualTo(false);
        assertThat(response.getBody().get("mensagem")).isNull();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blocos = (List<Map<String, Object>>) response.getBody().get("blocos");
        assertThat(blocos).hasSize(2);
        assertThat(blocos.get(1).get("ordem")).isEqualTo(2);
        assertThat(blocos.get(1).get("duracaoMinutos")).isEqualTo(120);

        @SuppressWarnings("unchecked")
        Map<String, Object> tarefa = (Map<String, Object>) blocos.get(1).get("tarefa");
        assertThat(tarefa.get("id")).isEqualTo(nova.getId().toString());
        assertThat(tarefa.get("titulo")).isEqualTo("Nova");
        assertThat(blocos.get(0).get("tarefa")).isNotNull();
        assertThat(((Map<?, ?>) blocos.get(0).get("tarefa")).get("id")).isEqualTo(noPlano.getId().toString());
    }

    @Test
    void deveMarcarConflitoQuandoSomaUltrapassaCapacidadeMasPersiste() {
        salvarOverride(1);
        salvarTarefa("Cabe", Prioridade.ALTA, StatusTarefa.PENDENTE, 60, AGORA);
        Plano plano = plannerGeracaoService.gerar(usuarioId, DIA_D);
        Tarefa estoura = salvarTarefa("Estoura", Prioridade.MEDIA, StatusTarefa.PENDENTE, 60, AGORA);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/planos/" + plano.getId() + "/blocos",
                HttpMethod.POST,
                autenticadoJson(Map.of("tarefaId", estoura.getId().toString())),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        assertThat(response.getBody().get("temConflito")).isEqualTo(true);
        assertThat(response.getBody().get("mensagem")).isEqualTo(PlanoAjusteService.MENSAGEM_ESTOURO);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blocos = (List<Map<String, Object>>) response.getBody().get("blocos");
        assertThat(blocos).hasSize(2);
        assertThat(blocos.get(1).get("duracaoMinutos")).isEqualTo(60);
    }

    @Test
    void deveRetornar409QuandoTarefaJaEstaNoPlano() {
        salvarOverride(2);
        Tarefa tarefa = salvarTarefa("Unica", Prioridade.MEDIA, StatusTarefa.PENDENTE, 60, AGORA);
        Plano plano = plannerGeracaoService.gerar(usuarioId, DIA_D);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/planos/" + plano.getId() + "/blocos",
                HttpMethod.POST,
                autenticadoJson(Map.of("tarefaId", tarefa.getId().toString())),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(CONFLICT);
    }

    @Test
    void deveRetornar404QuandoTarefaNaoExiste() {
        salvarOverride(2);
        Plano plano = plannerGeracaoService.gerar(usuarioId, DIA_D);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/planos/" + plano.getId() + "/blocos",
                HttpMethod.POST,
                autenticadoJson(Map.of("tarefaId", UUID.randomUUID().toString())),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND);
    }

    @Test
    void deveRetornar409QuandoTarefaNaoEElegivel() {
        salvarOverride(3);
        salvarTarefa("Base", Prioridade.MEDIA, StatusTarefa.PENDENTE, 60, AGORA);
        Tarefa finalizada = salvarTarefa("Finalizada", Prioridade.ALTA, StatusTarefa.FINALIZADA, 60, AGORA);
        Plano plano = plannerGeracaoService.gerar(usuarioId, DIA_D);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/planos/" + plano.getId() + "/blocos",
                HttpMethod.POST,
                autenticadoJson(Map.of("tarefaId", finalizada.getId().toString())),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(CONFLICT);
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

    private Tarefa salvarTarefa(
            String titulo,
            Prioridade prioridade,
            StatusTarefa status,
            int duracaoMinutos,
            Instant createdAt
    ) {
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
                status,
                duracaoMinutos,
                createdAt,
                createdAt
        ));
    }

    private HttpEntity<Map<String, ?>> autenticadoJson(Map<String, ?> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(new HashMap<>(body), headers);
    }

    private static HttpEntity<Map<String, ?>> json(Map<String, ?> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(new HashMap<>(body), headers);
    }
}
