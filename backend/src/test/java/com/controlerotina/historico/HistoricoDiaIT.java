package com.controlerotina.historico;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.controlerotina.usuario.entity.Usuario;
import com.controlerotina.usuario.repository.UsuarioRepository;
import com.controlerotina.xp.entity.TipoXpEvento;
import com.controlerotina.xp.entity.XpEvento;
import com.controlerotina.xp.repository.XpEventoRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class HistoricoDiaIT {

    private static final String SENHA = "Senha@123";
    private static final Instant AGORA = Instant.parse("2026-10-27T15:00:00Z");
    private static final LocalDate DIA = LocalDate.of(2026, 10, 27);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    UsuarioRepository usuarioRepository;

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

        String email = "hist-dia-" + UUID.randomUUID() + "@email.com";
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
    void t4_diaComMetricasEDiaVazioComZeros() {
        seedAtividade(usuarioId, DIA, 60, 90, true);

        ResponseEntity<Map> comDados = restTemplate.exchange(
                "/historico/dia?data=" + DIA,
                HttpMethod.GET,
                autenticado(),
                Map.class);

        assertThat(comDados.getStatusCode()).isEqualTo(OK);
        assertThat(comDados.getBody().get("tarefasFinalizadas")).isEqualTo(1);
        assertThat(comDados.getBody().get("blocosConcluidos")).isEqualTo(1);
        assertThat(comDados.getBody().get("minutosTrabalhados")).isEqualTo(60);
        assertThat(comDados.getBody().get("xpGanho")).isEqualTo(99);
        Map periodo = (Map) comDados.getBody().get("periodo");
        assertThat(periodo.get("escopo")).isEqualTo("DIA");
        assertThat(periodo.get("inicio")).isEqualTo(DIA.toString());

        ResponseEntity<Map> vazio = restTemplate.exchange(
                "/historico/dia?data=2026-10-20",
                HttpMethod.GET,
                autenticado(),
                Map.class);
        assertThat(vazio.getStatusCode()).isEqualTo(OK);
        assertThat(vazio.getBody().get("tarefasFinalizadas")).isEqualTo(0);
        assertThat(vazio.getBody().get("blocosConcluidos")).isEqualTo(0);
        assertThat(vazio.getBody().get("minutosTrabalhados")).isEqualTo(0);
        assertThat(vazio.getBody().get("xpGanho")).isEqualTo(0);
    }

    @Test
    void t6_semTokenRetorna401() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/historico/dia?data=" + DIA, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
    }

    @Test
    void t7_usuarioNaoVeDadosDeOutro() {
        seedAtividade(usuarioId, DIA, 60, 90, true);

        UUID outro = UUID.randomUUID();
        usuarioRepository.save(new Usuario(
                outro, "outro-hist-" + outro + "@email.com", "$2a$10$hash", "Outro", AGORA, AGORA
        ));
        seedAtividade(outro, DIA, 120, 200, true);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/historico/dia?data=" + DIA,
                HttpMethod.GET,
                autenticado(),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody().get("blocosConcluidos")).isEqualTo(1);
        assertThat(response.getBody().get("minutosTrabalhados")).isEqualTo(60);
        assertThat(response.getBody().get("xpGanho")).isEqualTo(99);
        assertThat(response.getBody().get("tarefasFinalizadas")).isEqualTo(1);
    }

    private void seedAtividade(
            UUID dono,
            LocalDate data,
            int duracaoMinutos,
            int xpBloco,
            boolean comBonus
    ) {
        UUID tarefaId = UUID.randomUUID();
        tarefaRepository.save(new Tarefa(
                tarefaId, dono, "T", "d", Prioridade.MEDIA,
                AGORA, StatusTarefa.FINALIZADA, 1, AGORA, AGORA
        ));
        UUID planoId = UUID.randomUUID();
        planoRepository.save(new Plano(
                planoId, dono, data, 1, StatusPlano.APROVADO,
                false, null, AGORA, AGORA, AGORA
        ));
        UUID blocoId = UUID.randomUUID();
        blocoPlanoRepository.save(new BlocoPlano(
                blocoId, planoId, tarefaId, duracaoMinutos, 1, AGORA, AGORA
        ));
        UUID execucaoId = UUID.randomUUID();
        execucaoRepository.save(new Execucao(
                execucaoId, dono, blocoId, StatusExecucao.CONCLUIDO,
                AGORA, AGORA, null, AGORA, AGORA
        ));
        xpEventoRepository.save(new XpEvento(
                UUID.randomUUID(), dono, TipoXpEvento.BLOCO_CONCLUIDO, execucaoId, xpBloco, AGORA
        ));
        if (comBonus) {
            xpEventoRepository.save(new XpEvento(
                    UUID.randomUUID(), dono, TipoXpEvento.TAREFA_FINALIZADA, tarefaId, 9, AGORA
            ));
        }
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
