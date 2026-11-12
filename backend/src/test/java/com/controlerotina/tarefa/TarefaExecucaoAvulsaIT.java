package com.controlerotina.tarefa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

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

import com.controlerotina.planner.entity.BlocoPlano;
import com.controlerotina.planner.entity.Plano;
import com.controlerotina.planner.entity.StatusPlano;
import com.controlerotina.planner.repository.BlocoPlanoRepository;
import com.controlerotina.planner.repository.PlanoRepository;
import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;
import com.controlerotina.tarefa.repository.TarefaRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class TarefaExecucaoAvulsaIT {

    private static final String SENHA = "Senha@123";
    private static final LocalDate TERCA = LocalDate.of(2026, 10, 20);
    private static final Instant AGORA = Instant.parse("2026-10-20T12:00:00Z");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    TarefaRepository tarefaRepository;

    @Autowired
    PlanoRepository planoRepository;

    @Autowired
    BlocoPlanoRepository blocoPlanoRepository;

    private String accessToken;
    private UUID usuarioId;

    @BeforeEach
    void cadastrarELogar() {
        jdbcTemplate.update("delete from execucao");
        jdbcTemplate.update("delete from bloco_plano");
        jdbcTemplate.update("delete from plano");
        jdbcTemplate.update("delete from tarefa");

        String email = "tarefa-avulsa-" + UUID.randomUUID() + "@email.com";
        assertThat(restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Dono", "email", email, "senha", SENHA)),
                Map.class).getStatusCode()).isEqualTo(CREATED);

        ResponseEntity<Map> login = restTemplate.postForEntity(
                "/auth/login",
                json(Map.of("email", email, "senha", SENHA)),
                Map.class);
        accessToken = login.getBody().get("accessToken").toString();
        usuarioId = jdbcTemplate.queryForObject(
                "select id from usuario where email = ?", UUID.class, email);
    }

    @Test
    void deveIniciarSessaoAvulsaECriarPlanoTarefaAvulsa() {
        UUID tarefaId = criarTarefa(120);

        ResponseEntity<Map> primeiro = restTemplate.exchange(
                "/tarefas/" + tarefaId + "/iniciar",
                HttpMethod.POST,
                authed(Map.of("duracaoMinutos", 30, "data", TERCA.toString())),
                Map.class);
        assertThat(primeiro.getStatusCode()).isIn(CREATED, OK);
        assertThat(primeiro.getBody().get("status")).isEqualTo("EM_ANDAMENTO");

        assertThat(planoRepository.findByUsuarioIdAndDataAndStatus(
                usuarioId, TERCA, StatusPlano.TAREFA_AVULSA)).isPresent();
        assertThat(planoRepository.findFirstByUsuarioIdAndDataAndStatusOrderByVersaoDesc(
                usuarioId, TERCA, StatusPlano.APROVADO)).isEmpty();

        ResponseEntity<Map> segundo = restTemplate.exchange(
                "/tarefas/" + tarefaId + "/iniciar",
                HttpMethod.POST,
                authed(Map.of("duracaoMinutos", 30, "data", TERCA.toString())),
                Map.class);
        assertThat(segundo.getStatusCode()).isEqualTo(CONFLICT);
    }

    @Test
    void deveConcluirDiretoAvulsoComXpEAtualizarRestante() {
        UUID tarefaId = criarTarefa(90);

        ResponseEntity<Map> primeiro = restTemplate.exchange(
                "/tarefas/" + tarefaId + "/concluir-direto",
                HttpMethod.POST,
                authed(Map.of("duracaoMinutos", 30, "data", TERCA.toString())),
                Map.class);
        assertThat(primeiro.getStatusCode()).isEqualTo(OK);
        assertThat(primeiro.getBody().get("status")).isEqualTo("CONCLUIDO");
        assertThat(primeiro.getBody().get("xp")).isNotNull();

        ResponseEntity<Map> tarefa = restTemplate.exchange(
                "/tarefas/" + tarefaId,
                HttpMethod.GET,
                authed(null),
                Map.class);
        assertThat(tarefa.getBody().get("restanteMinutos")).isEqualTo(60);
        assertThat(tarefa.getBody().get("status")).isEqualTo("EM_PROGRESSO");
    }

    @Test
    void deveReusarBlocoAbertoDoAprovadoSemDuracao() {
        UUID tarefaId = criarTarefa(120);
        Plano aprovado = planoRepository.save(new Plano(
                UUID.randomUUID(), usuarioId, TERCA, 1, StatusPlano.APROVADO,
                false, null, null, AGORA, AGORA, AGORA));
        BlocoPlano bloco = blocoPlanoRepository.save(new BlocoPlano(
                UUID.randomUUID(), aprovado.getId(), tarefaId, 40, 1, AGORA, AGORA));

        ResponseEntity<Map> inicio = restTemplate.exchange(
                "/tarefas/" + tarefaId + "/iniciar",
                HttpMethod.POST,
                authed(Map.of("data", TERCA.toString())),
                Map.class);
        assertThat(inicio.getStatusCode()).isIn(CREATED, OK);
        assertThat(inicio.getBody().get("blocoPlanoId")).isEqualTo(bloco.getId().toString());

        assertThat(planoRepository.findByUsuarioIdAndDataAndStatus(
                usuarioId, TERCA, StatusPlano.TAREFA_AVULSA)).isEmpty();
    }

    @Test
    void deveRejeitarDuracaoAcimaDoRestanteCom400() {
        UUID tarefaId = criarTarefa(30);

        ResponseEntity<Map> resp = restTemplate.exchange(
                "/tarefas/" + tarefaId + "/iniciar",
                HttpMethod.POST,
                authed(Map.of("duracaoMinutos", 60, "data", TERCA.toString())),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(BAD_REQUEST);
    }

    @Test
    void deveRejeitarSemRestanteCom409() {
        UUID tarefaId = criarTarefa(30);

        ResponseEntity<Map> primeiro = restTemplate.exchange(
                "/tarefas/" + tarefaId + "/concluir-direto",
                HttpMethod.POST,
                authed(Map.of("duracaoMinutos", 30, "data", TERCA.toString())),
                Map.class);
        assertThat(primeiro.getStatusCode()).isEqualTo(OK);
        assertThat(primeiro.getBody().get("status")).isEqualTo("CONCLUIDO");

        ResponseEntity<Map> segundo = restTemplate.exchange(
                "/tarefas/" + tarefaId + "/iniciar",
                HttpMethod.POST,
                authed(Map.of("duracaoMinutos", 15, "data", TERCA.toString())),
                Map.class);
        assertThat(segundo.getStatusCode()).isEqualTo(CONFLICT);
    }

    @Test
    void deveRejeitarIniciarNoPaiComFilhos() {
        UUID paiId = criarTarefa(120);
        criarFilho(paiId, 60);

        ResponseEntity<Map> resp = restTemplate.exchange(
                "/tarefas/" + paiId + "/iniciar",
                HttpMethod.POST,
                authed(Map.of("duracaoMinutos", 30, "data", TERCA.toString())),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(CONFLICT);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from bloco_plano where tarefa_id = ?",
                Integer.class,
                paiId)).isZero();
    }

    @Test
    void deveRejeitarConcluirDiretoNoPaiComFilhos() {
        UUID paiId = criarTarefa(120);
        UUID filhoId = criarFilho(paiId, 60);

        ResponseEntity<Map> resp = restTemplate.exchange(
                "/tarefas/" + paiId + "/concluir-direto",
                HttpMethod.POST,
                authed(Map.of("duracaoMinutos", 30, "data", TERCA.toString())),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(CONFLICT);
        assertThat(jdbcTemplate.queryForObject(
                "select status from tarefa where id = ?",
                String.class,
                filhoId)).isEqualTo("PENDENTE");
        assertThat(jdbcTemplate.queryForObject(
                "select status from tarefa where id = ?",
                String.class,
                paiId)).isEqualTo("PENDENTE");
    }

    @Test
    void deveIniciarSessaoNaSubtarefa() {
        UUID paiId = criarTarefa(120);
        UUID filhoId = criarFilho(paiId, 60);

        ResponseEntity<Map> resp = restTemplate.exchange(
                "/tarefas/" + filhoId + "/iniciar",
                HttpMethod.POST,
                authed(Map.of("duracaoMinutos", 30, "data", TERCA.toString())),
                Map.class);

        assertThat(resp.getStatusCode()).isIn(CREATED, OK);
        assertThat(resp.getBody().get("status")).isEqualTo("EM_ANDAMENTO");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from bloco_plano where tarefa_id = ?",
                Integer.class,
                filhoId)).isEqualTo(1);
    }

    private UUID criarTarefa(int duracaoMinutos) {
        return tarefaRepository.save(new Tarefa(
                UUID.randomUUID(),
                usuarioId,
                "Estudar",
                "descricao",
                Prioridade.MEDIA,
                AGORA.plusSeconds(86_400),
                StatusTarefa.PENDENTE,
                duracaoMinutos,
                AGORA,
                AGORA
        )).getId();
    }

    private UUID criarFilho(UUID parentId, int duracaoMinutos) {
        Tarefa filho = new Tarefa(
                UUID.randomUUID(),
                usuarioId,
                "Passo",
                "descricao",
                Prioridade.MEDIA,
                AGORA.plusSeconds(86_400),
                StatusTarefa.PENDENTE,
                duracaoMinutos,
                AGORA,
                AGORA
        );
        filho.definirParentId(parentId);
        return tarefaRepository.save(filho).getId();
    }

    private HttpEntity<?> authed(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (body == null) {
            return new HttpEntity<>(headers);
        }
        return new HttpEntity<>(body, headers);
    }

    private static HttpEntity<Map<String, Object>> json(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(new HashMap<>(body), headers);
    }
}
