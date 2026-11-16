package com.controlerotina.execucao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
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

import com.controlerotina.capacidade.entity.DiaSemana;
import com.controlerotina.execucao.entity.Execucao;
import com.controlerotina.execucao.entity.StatusExecucao;
import com.controlerotina.execucao.repository.ExecucaoRepository;
import com.controlerotina.habito.entity.Habito;
import com.controlerotina.habito.entity.StatusHabito;
import com.controlerotina.habito.repository.HabitoRepository;
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
class ExecucaoSessaoAtivaIT {

    private static final String SENHA = "Senha@123";
    private static final LocalDate DIA = LocalDate.of(2026, 10, 27);
    private static final Instant AGORA = Instant.parse("2026-10-27T12:00:00Z");
    private static final Instant NOVE_HORAS = Instant.parse("2026-10-27T12:00:00Z");
    private static final Instant DEZ_HORAS = Instant.parse("2026-10-27T13:00:00Z");

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
    HabitoRepository habitoRepository;

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

    @BeforeEach
    void cadastrarELogar() {
        jdbcTemplate.update("delete from execucao");
        jdbcTemplate.update("delete from bloco_plano");
        jdbcTemplate.update("delete from plano");
        jdbcTemplate.update("delete from tarefa");
        jdbcTemplate.update("delete from habito_dia_semana");
        jdbcTemplate.update("delete from habito");
        jdbcTemplate.update("delete from refresh_token");
        jdbcTemplate.update("delete from xp_evento");
        jdbcTemplate.update("delete from usuario");

        String email = "sessao-ativa-" + UUID.randomUUID() + "@email.com";
        assertThat(restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Sessao", "email", email, "senha", SENHA)),
                Map.class).getStatusCode()).isEqualTo(CREATED);

        ResponseEntity<Map> login = restTemplate.postForEntity(
                "/auth/login",
                json(Map.of("email", email, "senha", SENHA)),
                Map.class);
        assertThat(login.getStatusCode()).isEqualTo(OK);
        accessToken = login.getBody().get("accessToken").toString();
        usuarioId = jdbcTemplate.queryForObject(
                "select id from usuario where email = ?", UUID.class, email);
    }

    @Test
    void deveRetornarHabitoAvulsoEmAndamento() {
        UUID habitoId = salvarHabito(usuarioId, "Ler");
        UUID planoId = salvarPlano(usuarioId, DIA, 1, StatusPlano.HABITO_AVULSO);
        UUID blocoId = salvarBlocoHabito(planoId, habitoId);
        UUID execucaoId = salvarExecucao(usuarioId, blocoId, StatusExecucao.EM_ANDAMENTO, NOVE_HORAS);

        ResponseEntity<Map> response = buscarAtiva();

        assertThat(response.getStatusCode()).isEqualTo(OK);
        Map execucao = (Map) response.getBody().get("execucao");
        assertThat(execucao.get("id")).isEqualTo(execucaoId.toString());
        assertThat(execucao.get("status")).isEqualTo("EM_ANDAMENTO");
        assertThat(execucao.get("iniciadoEm")).isNotNull();
        assertThat(((Map) response.getBody().get("habitoResumo")).get("id")).isEqualTo(habitoId.toString());
        assertThat(response.getBody().get("tarefaResumo")).isNull();
        assertThat(((Map) response.getBody().get("bloco")).get("id")).isEqualTo(blocoId.toString());
    }

    @Test
    void deveRetornarBlocoDoPlanoAprovadoEmAndamento() {
        UUID tarefaId = salvarTarefa(usuarioId, "Relatório");
        UUID planoId = salvarPlano(usuarioId, DIA, 1, StatusPlano.APROVADO);
        UUID blocoId = salvarBlocoTarefa(planoId, tarefaId);
        salvarExecucao(usuarioId, blocoId, StatusExecucao.EM_ANDAMENTO, NOVE_HORAS);

        ResponseEntity<Map> response = buscarAtiva();

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(((Map) response.getBody().get("tarefaResumo")).get("id")).isEqualTo(tarefaId.toString());
        assertThat(response.getBody().get("habitoResumo")).isNull();
    }

    @Test
    void deveRetornarTarefaAvulsaEmAndamento() {
        UUID tarefaId = salvarTarefa(usuarioId, "Estudar");
        UUID planoId = salvarPlano(usuarioId, DIA, 1, StatusPlano.TAREFA_AVULSA);
        UUID blocoId = salvarBlocoTarefa(planoId, tarefaId);
        salvarExecucao(usuarioId, blocoId, StatusExecucao.EM_ANDAMENTO, NOVE_HORAS);

        ResponseEntity<Map> response = buscarAtiva();

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(((Map) response.getBody().get("tarefaResumo")).get("id")).isEqualTo(tarefaId.toString());
    }

    @Test
    void deveRetornar204SemSessaoEmAndamentoSemAlterarBanco() {
        UUID habitoId = salvarHabito(usuarioId, "Ler");
        UUID planoId = salvarPlano(usuarioId, DIA, 1, StatusPlano.HABITO_AVULSO);
        salvarExecucao(usuarioId, salvarBlocoHabito(planoId, habitoId), StatusExecucao.CONCLUIDO, NOVE_HORAS);
        UUID outroHabito = salvarHabito(usuarioId, "Meditar");
        salvarExecucao(usuarioId, salvarBlocoHabito(planoId, outroHabito), StatusExecucao.ABORTADO, NOVE_HORAS);

        ResponseEntity<Map> response = buscarAtiva();

        assertThat(response.getStatusCode()).isEqualTo(NO_CONTENT);
        assertThat(response.getBody()).isNull();
        assertThat(jdbcTemplate.queryForObject("select count(*) from execucao", Integer.class)).isEqualTo(2);
    }

    @Test
    void deveRetornarAMaisRecenteQuandoHaDuasEmAndamento() {
        UUID planoId = salvarPlano(usuarioId, DIA, 1, StatusPlano.HABITO_AVULSO);
        UUID antiga = salvarExecucao(usuarioId,
                salvarBlocoHabito(planoId, salvarHabito(usuarioId, "Ler")),
                StatusExecucao.EM_ANDAMENTO, NOVE_HORAS);
        UUID recente = salvarExecucao(usuarioId,
                salvarBlocoHabito(planoId, salvarHabito(usuarioId, "Meditar")),
                StatusExecucao.EM_ANDAMENTO, DEZ_HORAS);

        ResponseEntity<Map> response = buscarAtiva();

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(((Map) response.getBody().get("execucao")).get("id")).isEqualTo(recente.toString());
        assertThat(execucaoRepository.findById(antiga).orElseThrow().getStatus())
                .isEqualTo(StatusExecucao.EM_ANDAMENTO);
    }

    @Test
    void deveRetornarSessaoIniciadaNoDiaAnteriorAposMeiaNoiteBrt() {
        LocalDate ontem = DIA.minusDays(1);
        // 23:50 BRT de ontem = 02:50Z de hoje.
        Instant vinteTresECinquentaBrt = Instant.parse("2026-10-27T02:50:00Z");
        UUID planoId = salvarPlano(usuarioId, ontem, 1, StatusPlano.HABITO_AVULSO);
        UUID execucaoId = salvarExecucao(usuarioId,
                salvarBlocoHabito(planoId, salvarHabito(usuarioId, "Ler")),
                StatusExecucao.EM_ANDAMENTO, vinteTresECinquentaBrt);

        ResponseEntity<Map> response = buscarAtiva();

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(((Map) response.getBody().get("execucao")).get("id")).isEqualTo(execucaoId.toString());
    }

    @Test
    void deveIgnorarSessaoEmPlanoAprovadoSubstituidoSemAlterarEstado() {
        UUID tarefaId = salvarTarefa(usuarioId, "Relatório");
        UUID v1 = salvarPlano(usuarioId, DIA, 1, StatusPlano.APROVADO);
        UUID execucaoId = salvarExecucao(usuarioId, salvarBlocoTarefa(v1, tarefaId),
                StatusExecucao.EM_ANDAMENTO, NOVE_HORAS);
        salvarPlano(usuarioId, DIA, 2, StatusPlano.RASCUNHO);

        ResponseEntity<Map> response = buscarAtiva();

        assertThat(response.getStatusCode()).isEqualTo(NO_CONTENT);
        assertThat(execucaoRepository.findById(execucaoId).orElseThrow().getStatus())
                .isEqualTo(StatusExecucao.EM_ANDAMENTO);
    }

    @Test
    void deveRetornarAvulsaQuandoAMaisRecenteEstaEmPlanoSubstituido() {
        UUID avulso = salvarPlano(usuarioId, DIA, 1, StatusPlano.HABITO_AVULSO);
        UUID execucaoAvulsa = salvarExecucao(usuarioId,
                salvarBlocoHabito(avulso, salvarHabito(usuarioId, "Ler")),
                StatusExecucao.EM_ANDAMENTO, NOVE_HORAS);
        UUID v1 = salvarPlano(usuarioId, DIA, 2, StatusPlano.APROVADO);
        salvarExecucao(usuarioId, salvarBlocoTarefa(v1, salvarTarefa(usuarioId, "Relatório")),
                StatusExecucao.EM_ANDAMENTO, DEZ_HORAS);
        salvarPlano(usuarioId, DIA, 3, StatusPlano.RASCUNHO);

        ResponseEntity<Map> response = buscarAtiva();

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(((Map) response.getBody().get("execucao")).get("id")).isEqualTo(execucaoAvulsa.toString());
    }

    @Test
    void naoDeveRetornarSessaoDeOutroUsuario() {
        UUID outro = UUID.randomUUID();
        usuarioRepository.save(new Usuario(
                outro, "outro-ativa-" + outro + "@email.com", "$2a$10$hash", "Outro", AGORA, AGORA));
        UUID planoOutro = salvarPlano(outro, DIA, 1, StatusPlano.HABITO_AVULSO);
        UUID execucaoOutro = salvarExecucao(outro,
                salvarBlocoHabito(planoOutro, salvarHabito(outro, "Alheio")),
                StatusExecucao.EM_ANDAMENTO, NOVE_HORAS);

        ResponseEntity<Map> response = buscarAtiva();

        assertThat(response.getStatusCode()).isEqualTo(NO_CONTENT);
        assertThat(execucaoRepository.findById(execucaoOutro).orElseThrow().getStatus())
                .isEqualTo(StatusExecucao.EM_ANDAMENTO);
    }

    @Test
    void deveRetornar401SemToken() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/execucoes/ativa", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
        assertThat(response.getBody().get("title")).isEqualTo("Não autorizado");
    }

    @Test
    void deveRetornar401ComTokenInvalido() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("token-invalido");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/execucoes/ativa", HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
        assertThat(response.getBody().get("title")).isEqualTo("Não autorizado");
    }

    private ResponseEntity<Map> buscarAtiva() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return restTemplate.exchange("/execucoes/ativa", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }

    private UUID salvarTarefa(UUID dono, String titulo) {
        UUID id = UUID.randomUUID();
        tarefaRepository.save(new Tarefa(
                id, dono, titulo, "descricao", Prioridade.MEDIA,
                AGORA, StatusTarefa.EM_PROGRESSO, 60, AGORA, AGORA));
        return id;
    }

    private UUID salvarHabito(UUID dono, String titulo) {
        return habitoRepository.save(new Habito(
                UUID.randomUUID(), dono, titulo, null, 30,
                Set.of(DiaSemana.values()), null, StatusHabito.ATIVO, 0, 0, AGORA, AGORA)).getId();
    }

    private UUID salvarPlano(UUID dono, LocalDate data, int versao, StatusPlano status) {
        UUID id = UUID.randomUUID();
        planoRepository.save(new Plano(id, dono, data, versao, status, false, null, null, AGORA, AGORA, AGORA));
        return id;
    }

    private UUID salvarBlocoTarefa(UUID planoId, UUID tarefaId) {
        UUID id = UUID.randomUUID();
        blocoPlanoRepository.save(new BlocoPlano(id, planoId, tarefaId, 60, 1, AGORA, AGORA));
        return id;
    }

    private UUID salvarBlocoHabito(UUID planoId, UUID habitoId) {
        UUID id = UUID.randomUUID();
        blocoPlanoRepository.save(new BlocoPlano(id, planoId, null, habitoId, 30, 1, AGORA, AGORA));
        return id;
    }

    private UUID salvarExecucao(UUID dono, UUID blocoId, StatusExecucao status, Instant iniciadoEm) {
        UUID id = UUID.randomUUID();
        Instant concluido = status == StatusExecucao.CONCLUIDO ? iniciadoEm.plusSeconds(1800) : null;
        Instant abortado = status == StatusExecucao.ABORTADO ? iniciadoEm.plusSeconds(600) : null;
        execucaoRepository.save(new Execucao(
                id, dono, blocoId, status, iniciadoEm, concluido, abortado, AGORA, AGORA));
        return id;
    }

    private static HttpEntity<Map<String, Object>> json(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
