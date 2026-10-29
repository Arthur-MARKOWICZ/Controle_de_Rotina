package com.controlerotina.xp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
import com.controlerotina.usuario.repository.UsuarioRepository;
import com.controlerotina.xp.entity.TipoXpEvento;
import com.controlerotina.xp.service.XpConcessaoService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class XpConcessaoConcorrenciaIT {

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

    @Autowired
    XpConcessaoService xpConcessaoService;

    private UUID usuarioId;
    private UUID tarefaId;
    private UUID execucaoId;

    @BeforeEach
    void preparar() {
        jdbcTemplate.update("delete from execucao");
        jdbcTemplate.update("delete from bloco_plano");
        jdbcTemplate.update("delete from plano");
        jdbcTemplate.update("delete from tarefa");
        jdbcTemplate.update("delete from refresh_token");
        jdbcTemplate.update("delete from capacidade_override");
        jdbcTemplate.update("delete from capacidade_semanal");
        jdbcTemplate.update("delete from xp_evento");
        jdbcTemplate.update("delete from usuario");

        String email = "xp-corrida-" + UUID.randomUUID() + "@email.com";
        assertThat(restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "XP Corrida", "email", email, "senha", SENHA)),
                Map.class).getStatusCode()).isEqualTo(CREATED);

        ResponseEntity<Map> login = restTemplate.postForEntity(
                "/auth/login",
                json(Map.of("email", email, "senha", SENHA)),
                Map.class);
        assertThat(login.getStatusCode()).isEqualTo(OK);

        usuarioId = jdbcTemplate.queryForObject(
                "select id from usuario where email = ?",
                UUID.class,
                email);

        tarefaId = UUID.randomUUID();
        tarefaRepository.save(new Tarefa(
                tarefaId, usuarioId, "Tarefa XP", "desc", Prioridade.MEDIA,
                AGORA, StatusTarefa.EM_PROGRESSO, 120, AGORA, AGORA
        ));
        UUID planoId = UUID.randomUUID();
        planoRepository.save(new Plano(
                planoId, usuarioId, DIA, 1, StatusPlano.APROVADO,
                false, null, null, AGORA, AGORA, AGORA
        ));
        UUID blocoId = UUID.randomUUID();
        blocoPlanoRepository.save(new BlocoPlano(
                blocoId, planoId, tarefaId, 60, 1, AGORA, AGORA
        ));

        execucaoId = UUID.randomUUID();
        execucaoRepository.save(new Execucao(
                execucaoId, usuarioId, blocoId, StatusExecucao.EM_ANDAMENTO,
                AGORA, null, null, AGORA, AGORA
        ));
    }

    @Test
    void t3_duasConcessoesSimultaneasMesmoBloco_umEventoXpTotalCorreto() throws Exception {
        Tarefa tarefa = tarefaRepository.findById(tarefaId).orElseThrow();
        // 60 min × MEDIA 1.5 = 90; tarefa ainda EM_PROGRESSO → só BLOCO_CONCLUIDO
        int xpEsperado = XpMotor.xpBloco(60, Prioridade.MEDIA);

        CountDownLatch liberar = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<XpConcessaoService.ResultadoConcessao> f1 = pool.submit(() -> {
                liberar.await();
                return xpConcessaoService.concederAoConcluir(usuarioId, execucaoId, 60, 60, tarefa);
            });
            Future<XpConcessaoService.ResultadoConcessao> f2 = pool.submit(() -> {
                liberar.await();
                return xpConcessaoService.concederAoConcluir(usuarioId, execucaoId, 60, 60, tarefa);
            });

            liberar.countDown();
            XpConcessaoService.ResultadoConcessao r1 = f1.get(30, TimeUnit.SECONDS);
            XpConcessaoService.ResultadoConcessao r2 = f2.get(30, TimeUnit.SECONDS);

            int ganhos = r1.ganhoNesteRequest() + r2.ganhoNesteRequest();
            assertThat(ganhos).isEqualTo(xpEsperado);
            assertThat(List.of(r1.xpTotal(), r2.xpTotal())).contains(xpEsperado);

            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from xp_evento where tipo = ? and origem_id = ?",
                    Integer.class,
                    TipoXpEvento.BLOCO_CONCLUIDO.name(),
                    execucaoId
            )).isEqualTo(1);

            assertThat(usuarioRepository.findById(usuarioId).orElseThrow().getXpTotal())
                    .isEqualTo(xpEsperado);
        } finally {
            pool.shutdownNow();
        }
    }

    private static HttpEntity<Map<String, Object>> json(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
