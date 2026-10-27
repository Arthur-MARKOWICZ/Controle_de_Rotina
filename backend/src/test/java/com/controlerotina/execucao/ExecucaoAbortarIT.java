package com.controlerotina.execucao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import java.time.Instant;
import java.time.LocalDate;
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
class ExecucaoAbortarIT {

    private static final String SENHA = "Senha@123";
    private static final Instant AGORA = Instant.parse("2026-10-27T12:00:00Z");
    private static final LocalDate DIA = LocalDate.of(2026, 10, 27);

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
    private UUID blocoId;

    @BeforeEach
    void cadastrarELogar() {
        jdbcTemplate.update("delete from execucao");
        jdbcTemplate.update("delete from bloco_plano");
        jdbcTemplate.update("delete from plano");
        jdbcTemplate.update("delete from tarefa");
        jdbcTemplate.update("delete from refresh_token");
        jdbcTemplate.update("delete from capacidade_override");
        jdbcTemplate.update("delete from capacidade_semanal");
        jdbcTemplate.update("delete from xp_evento");
        jdbcTemplate.update("delete from usuario");

        String email = "exec-abortar-" + UUID.randomUUID() + "@email.com";
        assertThat(restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Abortar", "email", email, "senha", SENHA)),
                Map.class).getStatusCode()).isEqualTo(CREATED);

        ResponseEntity<Map> login = restTemplate.postForEntity(
                "/auth/login",
                json(Map.of("email", email, "senha", SENHA)),
                Map.class);
        assertThat(login.getStatusCode()).isEqualTo(OK);
        accessToken = login.getBody().get("accessToken").toString();
        UUID usuarioId = jdbcTemplate.queryForObject(
                "select id from usuario where email = ?",
                UUID.class,
                email);

        UUID tarefaId = UUID.randomUUID();
        tarefaRepository.save(new Tarefa(
                tarefaId, usuarioId, "Tarefa abortar", "desc", Prioridade.MEDIA,
                AGORA, StatusTarefa.PENDENTE, 1, AGORA, AGORA
        ));
        UUID planoId = UUID.randomUUID();
        planoRepository.save(new Plano(
                planoId, usuarioId, DIA, 1, StatusPlano.APROVADO,
                false, null, AGORA, AGORA, AGORA
        ));
        blocoId = UUID.randomUUID();
        blocoPlanoRepository.save(new BlocoPlano(
                blocoId, planoId, tarefaId, 60, 1, AGORA, AGORA
        ));
    }

    @Test
    void t9_abortar200E409EmStatusInvalido() {
        ResponseEntity<Map> iniciada = restTemplate.exchange(
                "/execucoes/iniciar",
                HttpMethod.POST,
                autenticadoJson(Map.of("blocoId", blocoId.toString())),
                Map.class);
        assertThat(iniciada.getStatusCode()).isEqualTo(CREATED);
        UUID execucaoId = UUID.fromString(iniciada.getBody().get("id").toString());

        ResponseEntity<Map> abortada = restTemplate.exchange(
                "/execucoes/" + execucaoId + "/abortar",
                HttpMethod.POST,
                autenticado(),
                Map.class);
        assertThat(abortada.getStatusCode()).isEqualTo(OK);
        assertThat(abortada.getBody().get("status")).isEqualTo("ABORTADO");

        ResponseEntity<Map> abortarDeNovo = restTemplate.exchange(
                "/execucoes/" + execucaoId + "/abortar",
                HttpMethod.POST,
                autenticado(),
                Map.class);
        assertThat(abortarDeNovo.getStatusCode()).isEqualTo(CONFLICT);

        ResponseEntity<Map> reinicio = restTemplate.exchange(
                "/execucoes/iniciar",
                HttpMethod.POST,
                autenticadoJson(Map.of("blocoId", blocoId.toString())),
                Map.class);
        assertThat(reinicio.getStatusCode()).isEqualTo(OK);

        assertThat(restTemplate.exchange(
                "/execucoes/" + execucaoId + "/concluir",
                HttpMethod.POST,
                autenticado(),
                Map.class).getStatusCode()).isEqualTo(OK);

        ResponseEntity<Map> concluirDeNovo = restTemplate.exchange(
                "/execucoes/" + execucaoId + "/concluir",
                HttpMethod.POST,
                autenticado(),
                Map.class);
        assertThat(concluirDeNovo.getStatusCode()).isEqualTo(CONFLICT);

        ResponseEntity<Map> abortarConcluida = restTemplate.exchange(
                "/execucoes/" + execucaoId + "/abortar",
                HttpMethod.POST,
                autenticado(),
                Map.class);
        assertThat(abortarConcluida.getStatusCode()).isEqualTo(CONFLICT);
    }

    private HttpEntity<Void> autenticado() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return new HttpEntity<>(headers);
    }

    private HttpEntity<Map<String, Object>> autenticadoJson(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private static HttpEntity<Map<String, Object>> json(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
