package com.controlerotina.historico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

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

import com.controlerotina.execucao.entity.Execucao;
import com.controlerotina.execucao.entity.StatusExecucao;
import com.controlerotina.execucao.repository.ExecucaoRepository;
import com.controlerotina.planner.entity.BlocoPlano;
import com.controlerotina.planner.entity.Plano;
import com.controlerotina.planner.entity.StatusPlano;
import com.controlerotina.planner.repository.BlocoPlanoRepository;
import com.controlerotina.planner.repository.PlanoRepository;
import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;
import com.controlerotina.tarefa.repository.TarefaRepository;
import com.controlerotina.xp.entity.TipoXpEvento;
import com.controlerotina.xp.entity.XpEvento;
import com.controlerotina.xp.repository.XpEventoRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class HistoricoSemanaIT {

    private static final String SENHA = "Senha@123";
    private static final Instant TERCA = Instant.parse("2026-10-27T15:00:00Z");
    private static final Instant QUARTA = Instant.parse("2026-10-28T15:00:00Z");
    private static final LocalDate SEGUNDA = LocalDate.of(2026, 10, 26);

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

    @Autowired
    ExecucaoRepository execucaoRepository;

    @Autowired
    XpEventoRepository xpEventoRepository;

    private String accessToken;
    private UUID usuarioId;

    @BeforeEach
    void limparELogar() {
        jdbcTemplate.update("delete from xp_evento");
        jdbcTemplate.update("delete from execucao");
        jdbcTemplate.update("delete from bloco_plano");
        jdbcTemplate.update("delete from plano");
        jdbcTemplate.update("delete from tarefa");
        jdbcTemplate.update("delete from refresh_token");
        jdbcTemplate.update("delete from capacidade_override");
        jdbcTemplate.update("delete from capacidade_semanal");
        jdbcTemplate.update("delete from usuario");

        String email = "hist-sem-" + UUID.randomUUID() + "@email.com";
        assertThat(restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Hist", "email", email, "senha", SENHA)),
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
    void t5_semanaAgregaVariosDiasERejeitaNaoSegunda() {
        seedDia(LocalDate.of(2026, 10, 27), TERCA, 60, 90);
        seedDia(LocalDate.of(2026, 10, 28), QUARTA, 30, 45);

        ResponseEntity<Map> semana = restTemplate.exchange(
                "/historico/semana?semanaInicio=" + SEGUNDA,
                HttpMethod.GET,
                autenticado(),
                Map.class);

        assertThat(semana.getStatusCode()).isEqualTo(OK);
        assertThat(semana.getBody().get("blocosConcluidos")).isEqualTo(2);
        assertThat(semana.getBody().get("minutosTrabalhados")).isEqualTo(90);
        assertThat(semana.getBody().get("xpGanho")).isEqualTo(135);
        Map periodo = (Map) semana.getBody().get("periodo");
        assertThat(periodo.get("escopo")).isEqualTo("SEMANA");
        assertThat(periodo.get("inicio")).isEqualTo(SEGUNDA.toString());
        assertThat(periodo.get("fim")).isEqualTo("2026-11-01");

        ResponseEntity<Map> invalido = restTemplate.exchange(
                "/historico/semana?semanaInicio=2026-10-27",
                HttpMethod.GET,
                autenticado(),
                Map.class);
        assertThat(invalido.getStatusCode()).isEqualTo(BAD_REQUEST);
    }

    @Test
    void t6_semanaSemToken401() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/historico/semana?semanaInicio=" + SEGUNDA,
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
    }

    private void seedDia(LocalDate dataPlano, Instant concluidoEm, int minutos, int xp) {
        UUID tarefaId = UUID.randomUUID();
        tarefaRepository.save(new Tarefa(
                tarefaId, usuarioId, "T", "d", Prioridade.MEDIA,
                concluidoEm, StatusTarefa.EM_PROGRESSO, 2, concluidoEm, concluidoEm
        ));
        UUID planoId = UUID.randomUUID();
        planoRepository.save(new Plano(
                planoId, usuarioId, dataPlano, 1, StatusPlano.APROVADO,
                false, null, concluidoEm, concluidoEm, concluidoEm
        ));
        UUID blocoId = UUID.randomUUID();
        blocoPlanoRepository.save(new BlocoPlano(
                blocoId, planoId, tarefaId, minutos, 1, concluidoEm, concluidoEm
        ));
        UUID execucaoId = UUID.randomUUID();
        execucaoRepository.save(new Execucao(
                execucaoId, usuarioId, blocoId, StatusExecucao.CONCLUIDO,
                concluidoEm, concluidoEm, null, concluidoEm, concluidoEm
        ));
        xpEventoRepository.save(new XpEvento(
                UUID.randomUUID(), usuarioId, TipoXpEvento.BLOCO_CONCLUIDO, execucaoId, xp, concluidoEm
        ));
    }

    private HttpEntity<Void> autenticado() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return new HttpEntity<>(headers);
    }

    private static HttpEntity<Map<String, Object>> json(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
