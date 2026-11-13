package com.controlerotina.historico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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
import com.controlerotina.projeto.entity.Projeto;
import com.controlerotina.projeto.entity.StatusProjeto;
import com.controlerotina.projeto.repository.ProjetoRepository;
import com.controlerotina.tag.entity.Tag;
import com.controlerotina.tag.entity.TarefaTag;
import com.controlerotina.tag.repository.TagRepository;
import com.controlerotina.tag.repository.TarefaTagRepository;
import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;
import com.controlerotina.tarefa.repository.TarefaRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class HistoricoCalibracaoIT {

    private static final String SENHA = "Senha@123";
    /** 2026-11-13 12:00 BRT. */
    private static final Instant AGORA = Instant.parse("2026-11-13T15:00:00Z");
    private static final Instant ONTEM = AGORA.minus(Duration.ofDays(1));
    /** Primeiro instante da janela: 2026-08-16 00:00 BRT. */
    private static final Instant INICIO_JANELA = Instant.parse("2026-08-16T03:00:00Z");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @TestConfiguration
    static class RelogioConfig {
        @Bean
        @Primary
        RelogioAjustavel relogioAjustavel() {
            return new RelogioAjustavel(AGORA);
        }
    }

    /** Clock fixo que o teste pode avançar (token expirado no meio do fluxo). */
    static class RelogioAjustavel extends Clock {
        private Instant agora;

        RelogioAjustavel(Instant agora) {
            this.agora = agora;
        }

        void definir(Instant novo) {
            this.agora = novo;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(agora, zone);
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }

    @Autowired TestRestTemplate restTemplate;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired RelogioAjustavel relogio;
    @Autowired TarefaRepository tarefaRepository;
    @Autowired PlanoRepository planoRepository;
    @Autowired BlocoPlanoRepository blocoPlanoRepository;
    @Autowired ExecucaoRepository execucaoRepository;
    @Autowired HabitoRepository habitoRepository;
    @Autowired TagRepository tagRepository;
    @Autowired TarefaTagRepository tarefaTagRepository;
    @Autowired ProjetoRepository projetoRepository;

    private final AtomicInteger versaoPlano = new AtomicInteger();
    private String accessToken;
    private UUID usuarioId;

    @BeforeEach
    void limparELogar() {
        relogio.definir(AGORA);
        for (String tabela : List.of(
                "xp_evento", "execucao", "bloco_plano", "plano", "tarefa_tag", "tag", "tarefa",
                "projeto", "habito_dia_semana", "habito", "refresh_token",
                "capacidade_override", "capacidade_semanal", "usuario")) {
            jdbcTemplate.update("delete from " + tabela);
        }
        Login login = cadastrarELogar();
        accessToken = login.token();
        usuarioId = login.usuarioId();
    }

    @Test
    void deveRetornarRazaoGeralPorTagEPorProjetoQuandoHaAmostrasSuficientes() {
        UUID projeto = criarProjeto("TCC", StatusProjeto.ATIVO);
        UUID tagFaculdade = criarTag("faculdade", "#3366FF");
        UUID tarefa = criarTarefa(projeto, false);
        vincularTag(tarefa, tagFaculdade);
        seedExemploDaSpec(tarefa);

        ResponseEntity<Map> resp = consultar(accessToken);

        assertThat(resp.getStatusCode()).isEqualTo(OK);
        Map body = resp.getBody();
        assertThat(body.get("minimoAmostras")).isEqualTo(5);
        assertThat((Map) body.get("janela"))
                .containsEntry("inicio", "2026-08-16")
                .containsEntry("fim", "2026-11-13");
        Map geral = (Map) body.get("geral");
        assertThat(geral.get("amostras")).isEqualTo(5);
        assertThat(geral.get("suficiente")).isEqualTo(true);
        assertThat(((Number) geral.get("razao")).doubleValue()).isEqualTo(1.25);
        assertThat(((Number) geral.get("desvioMedioMinutos")).doubleValue()).isEqualTo(15.0);
        Map porTag = ((List<Map>) body.get("porTag")).getFirst();
        assertThat(porTag)
                .containsEntry("tagId", tagFaculdade.toString())
                .containsEntry("nome", "faculdade")
                .containsEntry("cor", "#3366FF")
                .containsEntry("amostras", 5);
        Map porProjeto = ((List<Map>) body.get("porProjeto")).getFirst();
        assertThat(porProjeto)
                .containsEntry("projetoId", projeto.toString())
                .containsEntry("nome", "TCC")
                .containsEntry("suficiente", true);
    }

    @Test
    void naoDeveVazarExecucoesDeOutroUsuario() {
        Login outro = cadastrarELogar();
        UUID tarefaDoOutro = criarTarefa(outro.usuarioId(), null, false);
        for (int i = 0; i < 6; i++) {
            seedExecucao(outro.usuarioId(), tarefaDoOutro, 60, ONTEM, ONTEM.plus(Duration.ofMinutes(60)),
                    StatusExecucao.CONCLUIDO);
        }

        Map geral = (Map) consultar(accessToken).getBody().get("geral");

        assertThat(geral.get("amostras")).isEqualTo(0);
        assertThat(geral.get("suficiente")).isEqualTo(false);
        assertThat(((Map) consultar(outro.token()).getBody().get("geral")).get("amostras")).isEqualTo(6);
    }

    @Test
    void deveIgnorarAbortadaEmAndamentoHabitoEConclusaoDireta() {
        UUID tarefa = criarTarefa(null, false);
        seedExemploDaSpec(tarefa);
        seedExecucao(usuarioId, tarefa, 60, ONTEM, ONTEM.plus(Duration.ofMinutes(50)), StatusExecucao.ABORTADO);
        seedExecucao(usuarioId, tarefa, 60, ONTEM, null, StatusExecucao.EM_ANDAMENTO);
        seedExecucao(usuarioId, tarefa, 60, ONTEM, ONTEM, StatusExecucao.CONCLUIDO); // concluir-direto
        seedHabitoConcluidoComTimer();

        Map geral = (Map) consultar(accessToken).getBody().get("geral");

        assertThat(geral.get("amostras")).isEqualTo(5);
        assertThat(((Number) geral.get("razao")).doubleValue()).isEqualTo(1.25);
    }

    @Test
    void deveRespeitarLimiteDaJanelaDe90DiasEmBrt() {
        UUID tarefa = criarTarefa(null, false);
        for (int i = 0; i < 4; i++) {
            seedExecucao(usuarioId, tarefa, 60, ONTEM, ONTEM.plus(Duration.ofMinutes(60)), StatusExecucao.CONCLUIDO);
        }
        // concluída exatamente às 00:00 BRT do primeiro dia -> entra
        seedExecucao(usuarioId, tarefa, 60, INICIO_JANELA.minus(Duration.ofMinutes(60)), INICIO_JANELA,
                StatusExecucao.CONCLUIDO);
        // 23:59 BRT do dia anterior -> fica fora
        Instant foraDaJanela = INICIO_JANELA.minus(Duration.ofMinutes(1));
        seedExecucao(usuarioId, tarefa, 60, foraDaJanela.minus(Duration.ofMinutes(60)), foraDaJanela,
                StatusExecucao.CONCLUIDO);

        Map geral = (Map) consultar(accessToken).getBody().get("geral");

        assertThat(geral.get("amostras")).isEqualTo(5);
    }

    @Test
    void deveDescartarSessaoEsquecidaAberta() {
        UUID tarefa = criarTarefa(null, false);
        seedExemploDaSpec(tarefa);
        seedExecucao(usuarioId, tarefa, 30, ONTEM, ONTEM.plus(Duration.ofHours(5)), StatusExecucao.CONCLUIDO);

        Map geral = (Map) consultar(accessToken).getBody().get("geral");

        assertThat(geral.get("amostras")).isEqualTo(5);
        assertThat(((Number) geral.get("razao")).doubleValue()).isEqualTo(1.25);
    }

    @Test
    void deveRetornarSemDadosParaUsuarioNovo() {
        ResponseEntity<Map> resp = consultar(accessToken);

        assertThat(resp.getStatusCode()).isEqualTo(OK);
        Map geral = (Map) resp.getBody().get("geral");
        assertThat(geral.get("amostras")).isEqualTo(0);
        assertThat(geral.get("suficiente")).isEqualTo(false);
        assertThat(geral.get("razao")).isNull();
        assertThat(geral.get("desvioMedioMinutos")).isNull();
        assertThat((List) resp.getBody().get("porTag")).isEmpty();
        assertThat((List) resp.getBody().get("porProjeto")).isEmpty();
    }

    @Test
    void deveOmitirTagApagadaEManterProjetoArquivadoETarefaDeletada() {
        UUID arquivado = criarProjeto("Antigo", StatusProjeto.ARQUIVADO);
        UUID tag = criarTag("efemera", "#AA0000");
        UUID tarefa = criarTarefa(arquivado, true);
        vincularTag(tarefa, tag);
        seedExemploDaSpec(tarefa);
        jdbcTemplate.update("delete from tag where id = ?", tag);

        Map body = consultar(accessToken).getBody();

        assertThat(((Map) body.get("geral")).get("amostras")).isEqualTo(5);
        assertThat((List) body.get("porTag")).isEmpty();
        assertThat((List<Map>) body.get("porProjeto"))
                .singleElement()
                .satisfies(p -> assertThat(p).containsEntry("nome", "Antigo"));
    }

    @Test
    void deveRetornar401SemToken() {
        ResponseEntity<Map> resp = restTemplate.getForEntity("/historico/calibracao", Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(UNAUTHORIZED);
        assertThat(resp.getBody().get("title")).isEqualTo("Não autorizado");
    }

    @Test
    void deveRetornar401QuandoTokenExpiraNoMeioDoFluxo() {
        assertThat(consultar(accessToken).getStatusCode()).isEqualTo(OK);

        relogio.definir(AGORA.plus(Duration.ofMinutes(16)));
        ResponseEntity<Map> resp = consultar(accessToken);

        assertThat(resp.getStatusCode()).isEqualTo(UNAUTHORIZED);
        assertThat(resp.getBody().get("title")).isEqualTo("Não autorizado");
    }

    @Test
    void deveRetornar401ComTokenMalformado() {
        ResponseEntity<Map> resp = consultar("nao-e-um-jwt");

        assertThat(resp.getStatusCode()).isEqualTo(UNAUTHORIZED);
        assertThat(resp.getBody().get("title")).isEqualTo("Não autorizado");
    }

    // --- seed -----------------------------------------------------------------

    private void seedExemploDaSpec(UUID tarefa) {
        int[][] pares = {{30, 45}, {60, 60}, {60, 90}, {30, 30}, {120, 150}};
        for (int[] p : pares) {
            seedExecucao(usuarioId, tarefa, p[0], ONTEM, ONTEM.plus(Duration.ofMinutes(p[1])),
                    StatusExecucao.CONCLUIDO);
        }
    }

    private void seedExecucao(
            UUID dono, UUID tarefaId, int planejado, Instant inicio, Instant fim, StatusExecucao status
    ) {
        UUID planoId = criarPlano(dono);
        UUID blocoId = UUID.randomUUID();
        blocoPlanoRepository.save(new BlocoPlano(blocoId, planoId, tarefaId, planejado, 1, AGORA, AGORA));
        Instant abortadoEm = status == StatusExecucao.ABORTADO ? fim : null;
        Instant concluidoEm = status == StatusExecucao.CONCLUIDO ? fim : null;
        execucaoRepository.save(new Execucao(
                UUID.randomUUID(), dono, blocoId, status, inicio, concluidoEm, abortadoEm, AGORA, AGORA));
    }

    private void seedHabitoConcluidoComTimer() {
        UUID habitoId = habitoRepository.save(new Habito(
                UUID.randomUUID(), usuarioId, "Correr", null, 30, Set.of(DiaSemana.QUINTA), null,
                StatusHabito.ATIVO, 0, 0, AGORA, AGORA)).getId();
        UUID planoId = criarPlano(usuarioId);
        UUID blocoId = UUID.randomUUID();
        blocoPlanoRepository.save(new BlocoPlano(blocoId, planoId, null, habitoId, 30, 1, AGORA, AGORA));
        execucaoRepository.save(new Execucao(
                UUID.randomUUID(), usuarioId, blocoId, StatusExecucao.CONCLUIDO,
                ONTEM, ONTEM.plus(Duration.ofMinutes(45)), null, AGORA, AGORA));
    }

    private UUID criarPlano(UUID dono) {
        UUID planoId = UUID.randomUUID();
        planoRepository.save(new Plano(
                planoId, dono, LocalDate.of(2026, 11, 12), versaoPlano.incrementAndGet(), StatusPlano.APROVADO,
                false, null, null, AGORA, AGORA, AGORA));
        return planoId;
    }

    private UUID criarTarefa(UUID projetoId, boolean deletada) {
        return criarTarefa(usuarioId, projetoId, deletada);
    }

    private UUID criarTarefa(UUID dono, UUID projetoId, boolean deletada) {
        Tarefa tarefa = new Tarefa(
                UUID.randomUUID(), dono, "T", "d", Prioridade.MEDIA,
                null, StatusTarefa.EM_PROGRESSO, 120, AGORA, AGORA);
        if (projetoId != null) {
            tarefa.definirProjetoId(projetoId, AGORA);
        }
        if (deletada) {
            tarefa.softDelete(AGORA);
        }
        return tarefaRepository.save(tarefa).getId();
    }

    private UUID criarProjeto(String nome, StatusProjeto status) {
        return projetoRepository.save(new Projeto(
                UUID.randomUUID(), usuarioId, nome, null, status, AGORA, AGORA)).getId();
    }

    private UUID criarTag(String nome, String cor) {
        return tagRepository.save(new Tag(
                UUID.randomUUID(), usuarioId, nome, nome.toLowerCase(), cor, AGORA, AGORA)).getId();
    }

    private void vincularTag(UUID tarefaId, UUID tagId) {
        tarefaTagRepository.save(new TarefaTag(tarefaId, tagId));
    }

    // --- http -----------------------------------------------------------------

    private record Login(String token, UUID usuarioId) {
    }

    private Login cadastrarELogar() {
        String email = "calib-" + UUID.randomUUID() + "@email.com";
        assertThat(restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Calib", "email", email, "senha", SENHA)),
                Map.class).getStatusCode()).isEqualTo(CREATED);
        ResponseEntity<Map> login = restTemplate.postForEntity(
                "/auth/login",
                json(Map.of("email", email, "senha", SENHA)),
                Map.class);
        assertThat(login.getStatusCode()).isEqualTo(OK);
        UUID id = jdbcTemplate.queryForObject("select id from usuario where email = ?", UUID.class, email);
        return new Login(login.getBody().get("accessToken").toString(), id);
    }

    private ResponseEntity<Map> consultar(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange("/historico/calibracao", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }

    private static HttpEntity<Map<String, Object>> json(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
