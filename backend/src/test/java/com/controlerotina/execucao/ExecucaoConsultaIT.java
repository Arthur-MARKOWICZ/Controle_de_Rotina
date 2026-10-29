package com.controlerotina.execucao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.time.Instant;
import java.time.LocalDate;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class ExecucaoConsultaIT {

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

    @Autowired
    ExecucaoRepository execucaoRepository;

    private String accessToken;
    private UUID usuarioId;

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

        String email = "exec-consulta-" + UUID.randomUUID() + "@email.com";
        assertThat(restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Consulta", "email", email, "senha", SENHA)),
                Map.class).getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.CREATED);

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
    void t6_listarItensDoAprovadoCorrenteVazioSemAprovadoE401SemToken() {
        ResponseEntity<Map> semToken = restTemplate.getForEntity("/execucoes?data=" + DIA, Map.class);
        assertThat(semToken.getStatusCode()).isEqualTo(UNAUTHORIZED);

        ResponseEntity<Map> semPlano = restTemplate.exchange(
                "/execucoes?data=" + DIA,
                HttpMethod.GET,
                autenticado(),
                Map.class);
        assertThat(semPlano.getStatusCode()).isEqualTo(OK);
        assertThat(semPlano.getBody().get("planoAprovado")).isNull();
        assertThat((List<?>) semPlano.getBody().get("itens")).isEmpty();

        UUID tarefaId = salvarTarefa("Tarefa lista", 60);
        UUID planoId = UUID.randomUUID();
        planoRepository.save(new Plano(
                planoId, usuarioId, DIA, 1, StatusPlano.APROVADO,
                false, null, null, AGORA, AGORA, AGORA
        ));
        UUID bloco1 = UUID.randomUUID();
        UUID bloco2 = UUID.randomUUID();
        UUID tarefa2 = salvarTarefa("Segunda", 60);
        blocoPlanoRepository.save(new BlocoPlano(bloco1, planoId, tarefaId, 60, 1, AGORA, AGORA));
        blocoPlanoRepository.save(new BlocoPlano(bloco2, planoId, tarefa2, 30, 2, AGORA, AGORA));
        execucaoRepository.save(new Execucao(
                UUID.randomUUID(), usuarioId, bloco1, StatusExecucao.EM_ANDAMENTO,
                AGORA, null, null, AGORA, AGORA
        ));

        ResponseEntity<Map> comPlano = restTemplate.exchange(
                "/execucoes?data=" + DIA,
                HttpMethod.GET,
                autenticado(),
                Map.class);

        assertThat(comPlano.getStatusCode()).isEqualTo(OK);
        Map planoAprovado = (Map) comPlano.getBody().get("planoAprovado");
        assertThat(planoAprovado.get("id")).isEqualTo(planoId.toString());
        assertThat(planoAprovado.get("data")).isEqualTo(DIA.toString());
        assertThat(planoAprovado.get("versao")).isEqualTo(1);

        List<Map> itens = (List<Map>) comPlano.getBody().get("itens");
        assertThat(itens).hasSize(2);
        assertThat(((Map) itens.get(0).get("bloco")).get("id")).isEqualTo(bloco1.toString());
        assertThat(((Map) itens.get(0).get("execucao")).get("status")).isEqualTo("EM_ANDAMENTO");
        assertThat(((Map) itens.get(1).get("bloco")).get("id")).isEqualTo(bloco2.toString());
        assertThat(((Map) itens.get(1).get("execucao")).get("status")).isEqualTo("NAO_INICIADO");
        assertThat(((Map) itens.get(0).get("tarefaResumo")).get("id")).isEqualTo(tarefaId.toString());
    }

    private UUID salvarTarefa(String titulo, int duracaoMinutos) {
        UUID id = UUID.randomUUID();
        tarefaRepository.save(new Tarefa(
                id, usuarioId, titulo, "descricao", Prioridade.MEDIA,
                AGORA, StatusTarefa.PENDENTE, duracaoMinutos, AGORA, AGORA
        ));
        return id;
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
