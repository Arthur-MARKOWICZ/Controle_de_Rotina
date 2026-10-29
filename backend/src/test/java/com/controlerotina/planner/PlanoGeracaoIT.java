package com.controlerotina.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
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
import com.controlerotina.planner.repository.PlanoRepository;
import com.controlerotina.planner.service.PlannerGeracaoService;
import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;
import com.controlerotina.tarefa.repository.TarefaRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class PlanoGeracaoIT {

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
    PlanoRepository planoRepository;

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

        String email = "plano-gerar-" + UUID.randomUUID() + "@email.com";
        assertThat(restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Dono Gerar", "email", email, "senha", SENHA)),
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
    void deveRetornar401AoGerarSemToken() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/planos/gerar?data=" + DIA_D,
                null,
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
    }

    @Test
    void deveRetornar400QuandoDataAusente() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/planos/gerar",
                HttpMethod.POST,
                autenticado(),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
    }

    @Test
    void deveGerarPlanoCom201EBlocos() {
        salvarOverride(3);
        Tarefa baixa = salvarTarefa("Baixa", Prioridade.BAIXA, 60, AGORA);
        Tarefa alta = salvarTarefa("Alta", Prioridade.ALTA, 60, AGORA.plusSeconds(5));

        ResponseEntity<Map> response = restTemplate.exchange(
                "/planos/gerar?data=" + DIA_D,
                HttpMethod.POST,
                autenticado(),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("data")).isEqualTo(DIA_D.toString());
        assertThat(response.getBody().get("versao")).isEqualTo(1);
        assertThat(response.getBody().get("status")).isEqualTo("RASCUNHO");
        assertThat(response.getBody().get("temConflito")).isEqualTo(false);
        assertThat(response.getBody().get("id")).isNotNull();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blocos = (List<Map<String, Object>>) response.getBody().get("blocos");
        assertThat(blocos).hasSize(2);
        assertThat(blocos.get(0).get("ordem")).isEqualTo(1);
        assertThat(blocos.get(0).get("duracaoMinutos")).isEqualTo(60);

        @SuppressWarnings("unchecked")
        Map<String, Object> tarefa0 = (Map<String, Object>) blocos.get(0).get("tarefa");
        assertThat(tarefa0.get("id")).isEqualTo(alta.getId().toString());
        assertThat(tarefa0.get("titulo")).isEqualTo("Alta");

        @SuppressWarnings("unchecked")
        Map<String, Object> tarefa1 = (Map<String, Object>) blocos.get(1).get("tarefa");
        assertThat(tarefa1.get("id")).isEqualTo(baixa.getId().toString());
    }

    @Test
    void deveIncrementarVersaoQuandoJaExisteRascunho() {
        salvarOverride(2);
        salvarTarefa("Tarefa", Prioridade.MEDIA, 60, AGORA);
        plannerGeracaoService.gerar(usuarioId, DIA_D);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/planos/gerar?data=" + DIA_D,
                HttpMethod.POST,
                autenticado(),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        assertThat(response.getBody().get("versao")).isEqualTo(2);
        assertThat(response.getBody().get("status")).isEqualTo("RASCUNHO");
    }

    @Test
    void deveRetornar409QuandoJaExistePlanoAprovado() {
        salvarOverride(2);
        salvarTarefa("Tarefa", Prioridade.MEDIA, 60, AGORA);
        Plano rascunho = plannerGeracaoService.gerar(usuarioId, DIA_D);
        jdbcTemplate.update(
                "update plano set status = ? where id = ?",
                StatusPlano.APROVADO.name(),
                rascunho.getId()
        );

        ResponseEntity<Map> response = restTemplate.exchange(
                "/planos/gerar?data=" + DIA_D,
                HttpMethod.POST,
                autenticado(),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(CONFLICT);
        assertThat(planoRepository.findMaxVersaoByUsuarioIdAndData(usuarioId, DIA_D)).isEqualTo(1);
    }

    @Test
    void t4_duasGeracoesSimultaneas_versoesDistintasOu409Nunca500() throws Exception {
        salvarOverride(3);
        salvarTarefa("Tarefa corrida", Prioridade.MEDIA, 60, AGORA);

        CountDownLatch liberar = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<ResponseEntity<Map>> f1 = pool.submit(() -> {
                liberar.await();
                return restTemplate.exchange(
                        "/planos/gerar?data=" + DIA_D,
                        HttpMethod.POST,
                        autenticado(),
                        Map.class);
            });
            Future<ResponseEntity<Map>> f2 = pool.submit(() -> {
                liberar.await();
                return restTemplate.exchange(
                        "/planos/gerar?data=" + DIA_D,
                        HttpMethod.POST,
                        autenticado(),
                        Map.class);
            });

            liberar.countDown();
            ResponseEntity<Map> r1 = f1.get(30, TimeUnit.SECONDS);
            ResponseEntity<Map> r2 = f2.get(30, TimeUnit.SECONDS);

            List<HttpStatusCode> statuses = List.of(r1.getStatusCode(), r2.getStatusCode());
            assertThat(statuses).doesNotContain(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(statuses).allMatch(s -> s.equals(CREATED) || s.equals(CONFLICT));
            assertThat(statuses).contains(CREATED);

            Integer quantidade = jdbcTemplate.queryForObject(
                    "select count(*) from plano where usuario_id = ? and data = ?",
                    Integer.class,
                    usuarioId,
                    DIA_D
            );
            assertThat(quantidade).isBetween(1, 2);

            List<Integer> versoes = jdbcTemplate.queryForList(
                    "select versao from plano where usuario_id = ? and data = ? order by versao",
                    Integer.class,
                    usuarioId,
                    DIA_D
            );
            Set<Integer> unicas = new HashSet<>(versoes);
            assertThat(unicas).hasSize(versoes.size());
        } finally {
            pool.shutdownNow();
        }
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
