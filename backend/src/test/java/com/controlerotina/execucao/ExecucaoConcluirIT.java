package com.controlerotina.execucao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NOT_FOUND;
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
import com.controlerotina.usuario.entity.Usuario;
import com.controlerotina.usuario.repository.UsuarioRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class ExecucaoConcluirIT {

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

    @Autowired
    UsuarioRepository usuarioRepository;

    private String accessToken;
    private UUID usuarioId;
    private UUID tarefaId;
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

        String email = "exec-concluir-" + UUID.randomUUID() + "@email.com";
        assertThat(restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Concluir", "email", email, "senha", SENHA)),
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

        tarefaId = UUID.randomUUID();
        tarefaRepository.save(new Tarefa(
                tarefaId, usuarioId, "Tarefa concluir", "desc", Prioridade.MEDIA,
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
    void t8_concluir200ComStatusTarefaE404DeOutroUsuario() {
        ResponseEntity<Map> iniciada = restTemplate.exchange(
                "/execucoes/iniciar",
                HttpMethod.POST,
                autenticadoJson(Map.of("blocoId", blocoId.toString())),
                Map.class);
        assertThat(iniciada.getStatusCode()).isEqualTo(CREATED);
        UUID execucaoId = UUID.fromString(iniciada.getBody().get("id").toString());

        ResponseEntity<Map> concluida = restTemplate.exchange(
                "/execucoes/" + execucaoId + "/concluir",
                HttpMethod.POST,
                autenticado(),
                Map.class);

        assertThat(concluida.getStatusCode()).isEqualTo(OK);
        assertThat(concluida.getBody().get("status")).isEqualTo("CONCLUIDO");
        assertThat(tarefaRepository.findById(tarefaId).orElseThrow().getStatus())
                .isEqualTo(StatusTarefa.FINALIZADA);

        UUID outroUsuario = UUID.randomUUID();
        usuarioRepository.save(new Usuario(
                outroUsuario,
                "outro-conc-" + outroUsuario + "@email.com",
                "$2a$10$hash",
                "Outro",
                AGORA,
                AGORA
        ));
        UUID tarefaOutro = UUID.randomUUID();
        tarefaRepository.save(new Tarefa(
                tarefaOutro, outroUsuario, "Outra", "desc", Prioridade.BAIXA,
                AGORA, StatusTarefa.PENDENTE, 1, AGORA, AGORA
        ));
        UUID planoOutro = UUID.randomUUID();
        planoRepository.save(new Plano(
                planoOutro, outroUsuario, DIA, 1, StatusPlano.APROVADO,
                false, null, AGORA, AGORA, AGORA
        ));
        UUID blocoOutro = UUID.randomUUID();
        blocoPlanoRepository.save(new BlocoPlano(
                blocoOutro, planoOutro, tarefaOutro, 30, 1, AGORA, AGORA
        ));
        UUID execucaoOutro = UUID.randomUUID();
        execucaoRepository.save(new Execucao(
                execucaoOutro, outroUsuario, blocoOutro, StatusExecucao.EM_ANDAMENTO,
                AGORA, null, null, AGORA, AGORA
        ));

        ResponseEntity<Map> deOutro = restTemplate.exchange(
                "/execucoes/" + execucaoOutro + "/concluir",
                HttpMethod.POST,
                autenticado(),
                Map.class);
        assertThat(deOutro.getStatusCode()).isEqualTo(NOT_FOUND);
    }

    @Test
    @SuppressWarnings("unchecked")
    void t2_concluir200ComXpGanhoEventosEProgresso() {
        ResponseEntity<Map> iniciada = restTemplate.exchange(
                "/execucoes/iniciar",
                HttpMethod.POST,
                autenticadoJson(Map.of("blocoId", blocoId.toString())),
                Map.class);
        assertThat(iniciada.getStatusCode()).isEqualTo(CREATED);
        UUID execucaoId = UUID.fromString(iniciada.getBody().get("id").toString());

        ResponseEntity<Map> concluida = restTemplate.exchange(
                "/execucoes/" + execucaoId + "/concluir",
                HttpMethod.POST,
                autenticado(),
                Map.class);

        assertThat(concluida.getStatusCode()).isEqualTo(OK);
        assertThat(concluida.getBody()).isNotNull();

        Map<String, Object> xp = (Map<String, Object>) concluida.getBody().get("xp");
        assertThat(xp).isNotNull();
        // 60 min × MEDIA 1.5 = 90; bônus tarefa 1h × 60 × 1.5 × 0.1 = 9 → ganho 99
        assertThat(((Number) xp.get("ganhoNesteRequest")).intValue()).isEqualTo(99);

        var eventos = (java.util.List<Map<String, Object>>) xp.get("eventos");
        assertThat(eventos).hasSize(2);
        assertThat(eventos).anySatisfy(e -> {
            assertThat(e.get("tipo")).isEqualTo("BLOCO_CONCLUIDO");
            assertThat(((Number) e.get("quantidade")).intValue()).isEqualTo(90);
        });
        assertThat(eventos).anySatisfy(e -> {
            assertThat(e.get("tipo")).isEqualTo("TAREFA_FINALIZADA");
            assertThat(((Number) e.get("quantidade")).intValue()).isEqualTo(9);
        });

        Map<String, Object> progresso = (Map<String, Object>) xp.get("progresso");
        assertThat(progresso).isNotNull();
        assertThat(((Number) progresso.get("xpTotal")).intValue()).isEqualTo(99);
        assertThat(((Number) progresso.get("nivel")).intValue()).isEqualTo(4);
        assertThat(((Number) progresso.get("xpNoNivel")).intValue()).isEqualTo(39);
        assertThat(((Number) progresso.get("xpParaProximoNivel")).intValue()).isEqualTo(40);
    }

    @Test
    void t4_concluirSemTokenRetorna401() {
        UUID execucaoId = UUID.randomUUID();
        execucaoRepository.save(new Execucao(
                execucaoId, usuarioId, blocoId, StatusExecucao.EM_ANDAMENTO,
                AGORA, null, null, AGORA, AGORA
        ));

        ResponseEntity<Map> response = restTemplate.exchange(
                "/execucoes/" + execucaoId + "/concluir",
                HttpMethod.POST,
                new HttpEntity<>(new HttpHeaders()),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
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
