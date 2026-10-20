package com.controlerotina.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.NO_CONTENT;
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
import com.controlerotina.planner.entity.BlocoPlano;
import com.controlerotina.planner.entity.Plano;
import com.controlerotina.planner.entity.StatusPlano;
import com.controlerotina.planner.repository.BlocoPlanoRepository;
import com.controlerotina.planner.service.PlannerGeracaoService;
import com.controlerotina.planner.service.PlanoAjusteService;
import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;
import com.controlerotina.tarefa.repository.TarefaRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class PlanoAjusteRemoveIT {

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
    BlocoPlanoRepository blocoPlanoRepository;

    @Autowired
    PlannerGeracaoService plannerGeracaoService;

    @Autowired
    PlanoAjusteService planoAjusteService;

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
        jdbcTemplate.update("delete from usuario");

        String email = "plano-remove-" + UUID.randomUUID() + "@email.com";
        assertThat(restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Dono Remove", "email", email, "senha", SENHA)),
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
    void deveRetornar401AoRemoverSemToken() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/planos/" + UUID.randomUUID() + "/blocos/" + UUID.randomUUID(),
                HttpMethod.DELETE,
                null,
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
    }

    @Test
    void deveRetornar404QuandoPlanoNaoExiste() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/planos/" + UUID.randomUUID() + "/blocos/" + UUID.randomUUID(),
                HttpMethod.DELETE,
                autenticado(),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND);
    }

    @Test
    void deveRetornar404QuandoBlocoNaoExiste() {
        salvarOverride(2);
        salvarTarefa("Base", Prioridade.MEDIA, 1, AGORA);
        Plano plano = plannerGeracaoService.gerar(usuarioId, DIA_D);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/planos/" + plano.getId() + "/blocos/" + UUID.randomUUID(),
                HttpMethod.DELETE,
                autenticado(),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND);
    }

    @Test
    void deveRetornar409QuandoPlanoNaoERascunhoCorrente() {
        salvarOverride(2);
        salvarTarefa("Base", Prioridade.MEDIA, 1, AGORA);
        Plano v1 = plannerGeracaoService.gerar(usuarioId, DIA_D);
        Plano v2 = plannerGeracaoService.gerar(usuarioId, DIA_D);
        UUID blocoV1 = blocoPlanoRepository.findByPlanoIdOrderByOrdemAsc(v1.getId()).getFirst().getId();
        UUID blocoV2 = blocoPlanoRepository.findByPlanoIdOrderByOrdemAsc(v2.getId()).getFirst().getId();

        ResponseEntity<Map> versaoAntiga = restTemplate.exchange(
                "/planos/" + v1.getId() + "/blocos/" + blocoV1,
                HttpMethod.DELETE,
                autenticado(),
                Map.class);
        assertThat(versaoAntiga.getStatusCode()).isEqualTo(CONFLICT);

        jdbcTemplate.update(
                "update plano set status = ? where id = ?",
                StatusPlano.APROVADO.name(),
                v2.getId()
        );

        ResponseEntity<Map> aprovado = restTemplate.exchange(
                "/planos/" + v2.getId() + "/blocos/" + blocoV2,
                HttpMethod.DELETE,
                autenticado(),
                Map.class);
        assertThat(aprovado.getStatusCode()).isEqualTo(CONFLICT);
    }

    @Test
    void deveRemoverBlocoCom204() {
        salvarOverride(3);
        salvarTarefa("Alta", Prioridade.ALTA, 1, AGORA);
        salvarTarefa("Baixa", Prioridade.BAIXA, 1, AGORA);
        Plano plano = plannerGeracaoService.gerar(usuarioId, DIA_D);
        List<BlocoPlano> blocosAntes = blocoPlanoRepository.findByPlanoIdOrderByOrdemAsc(plano.getId());
        assertThat(blocosAntes).hasSize(2);
        UUID blocoRemovido = blocosAntes.get(1).getId();

        ResponseEntity<Void> response = restTemplate.exchange(
                "/planos/" + plano.getId() + "/blocos/" + blocoRemovido,
                HttpMethod.DELETE,
                autenticado(),
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(NO_CONTENT);
        assertThat(blocoPlanoRepository.findByPlanoIdOrderByOrdemAsc(plano.getId())).hasSize(1);
        assertThat(blocoPlanoRepository.findById(blocoRemovido)).isEmpty();
    }

    @Test
    void deveLimparConflitoQuandoSomaVoltaACabernaCapacidade() {
        salvarOverride(1);
        salvarTarefa("Cabe", Prioridade.ALTA, 1, AGORA);
        Plano plano = plannerGeracaoService.gerar(usuarioId, DIA_D);
        Tarefa estoura = salvarTarefa("Estoura", Prioridade.MEDIA, 1, AGORA);
        planoAjusteService.adicionarBloco(usuarioId, plano.getId(), estoura.getId());

        List<BlocoPlano> blocos = blocoPlanoRepository.findByPlanoIdOrderByOrdemAsc(plano.getId());
        assertThat(blocos).hasSize(2);
        UUID blocoEstouro = blocos.get(1).getId();

        ResponseEntity<Void> response = restTemplate.exchange(
                "/planos/" + plano.getId() + "/blocos/" + blocoEstouro,
                HttpMethod.DELETE,
                autenticado(),
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(NO_CONTENT);

        ResponseEntity<Map> consulta = restTemplate.exchange(
                "/planos/" + plano.getId(),
                HttpMethod.GET,
                autenticado(),
                Map.class);
        assertThat(consulta.getStatusCode()).isEqualTo(OK);
        assertThat(consulta.getBody().get("temConflito")).isEqualTo(false);
        assertThat(consulta.getBody().get("mensagem")).isNull();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blocosRestantes =
                (List<Map<String, Object>>) consulta.getBody().get("blocos");
        assertThat(blocosRestantes).hasSize(1);
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

    private Tarefa salvarTarefa(String titulo, Prioridade prioridade, int duracaoHoras, Instant createdAt) {
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
                duracaoHoras,
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
