package com.controlerotina.objetivo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.controlerotina.historico.PeriodoBRT;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class ObjetivoEvolucaoIT {

    private static final String SENHA = "Senha@123";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private final AtomicInteger versaoPlano = new AtomicInteger();
    private String accessToken;
    private UUID usuarioId;
    private LocalDate hoje;

    @BeforeEach
    void cadastrarELogar() {
        String email = "evolucao-" + UUID.randomUUID() + "@email.com";
        accessToken = cadastrarELogar(email);
        usuarioId = jdbcTemplate.queryForObject("select id from usuario where email = ?", UUID.class, email);
        hoje = LocalDate.now(PeriodoBRT.ZONE);
    }

    @Test
    void deveDevolverDoisDiasDeTrabalhoComAcumuladoIgualAoProgresso() {
        UUID objetivo = criarObjetivo("Meta");
        UUID tarefa = criarTarefa("Estudar", 120, List.of(objetivo));
        executarTarefa(tarefa, hoje.minusDays(5), 30, instanteBrt(hoje.minusDays(5)));
        executarTarefa(tarefa, hoje.minusDays(3), 45, instanteBrt(hoje.minusDays(3)));

        ResponseEntity<Map> response = get("/objetivos/" + objetivo + "/evolucao");

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(antes(response)).isZero();
        assertThat(pontos(response)).hasSize(2);
        assertPonto(pontos(response).get(0), hoje.minusDays(5), 30, 30);
        assertPonto(pontos(response).get(1), hoje.minusDays(3), 45, 75);
        assertThat(ultimoAcumulado(response)).isEqualTo(minutosConcluidosDoProgresso(objetivo)).isEqualTo(75);
    }

    @Test
    void deveColocarOSaldoNoDiaDaFinalizacaoPorPatch() {
        UUID objetivo = criarObjetivo("Meta");
        UUID tarefa = criarTarefa("Escrever", 60, List.of(objetivo));
        executarTarefa(tarefa, hoje.minusDays(2), 30, instanteBrt(hoje.minusDays(2)));

        finalizar(tarefa);

        ResponseEntity<Map> response = get("/objetivos/" + objetivo + "/evolucao");
        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(pontos(response)).hasSize(2);
        assertPonto(pontos(response).get(0), hoje.minusDays(2), 30, 30);
        assertPonto(pontos(response).get(1), hoje, 30, 60);
        assertThat(ultimoAcumulado(response)).isEqualTo(minutosConcluidosDoProgresso(objetivo)).isEqualTo(60);
    }

    @Test
    void reabrirATarefaDeveRemovelaDaCurvaEDoProgresso() {
        UUID objetivo = criarObjetivo("Meta");
        UUID tarefa = criarTarefa("Reabrir", 60, List.of(objetivo));
        finalizar(tarefa);
        assertThat(pontos(get("/objetivos/" + objetivo + "/evolucao"))).hasSize(1);

        patchTarefa(tarefa, Map.of("status", "PENDENTE"));

        ResponseEntity<Map> response = get("/objetivos/" + objetivo + "/evolucao");
        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(antes(response)).isZero();
        assertThat(pontos(response)).isEmpty();
        assertThat(minutosConcluidosDoProgresso(objetivo)).isZero();
    }

    @Test
    void tarefaFinalizadaSemDataDeveIrParaAntesDoHistoricoSemPontos() {
        UUID objetivo = criarObjetivo("Meta");
        UUID tarefa = criarTarefa("Antiga", 60, List.of(objetivo));
        finalizar(tarefa);
        jdbcTemplate.update("update tarefa set finalizada_em = null where id = ?", tarefa);

        ResponseEntity<Map> response = get("/objetivos/" + objetivo + "/evolucao");

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(antes(response)).isEqualTo(60);
        assertThat(pontos(response)).isEmpty();
        assertThat(antes(response)).isEqualTo(minutosConcluidosDoProgresso(objetivo));
    }

    @Test
    void execucaoDeSubtarefaDeveContarNaRaizNoDiaDaExecucao() {
        UUID objetivo = criarObjetivo("Meta");
        UUID raiz = criarTarefa("Raiz", 60, List.of(objetivo));
        UUID filho = criarFilho(raiz, "Filho", 30);
        executarTarefa(filho, hoje.minusDays(4), 30, instanteBrt(hoje.minusDays(4)));

        ResponseEntity<Map> response = get("/objetivos/" + objetivo + "/evolucao");

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(pontos(response)).hasSize(1);
        assertPonto(pontos(response).get(0), hoje.minusDays(4), 30, 30);
        assertThat(ultimoAcumulado(response)).isEqualTo(minutosConcluidosDoProgresso(objetivo));
    }

    @Test
    void execucaoDasDuasEMeiaUtcDeveCairNoDiaAnteriorEmBrt() {
        UUID objetivo = criarObjetivo("Meta");
        UUID tarefa = criarTarefa("Virada", 60, List.of(objetivo));
        executarTarefa(tarefa, LocalDate.of(2026, 11, 30), 30, Instant.parse("2026-12-01T02:30:00Z"));

        ResponseEntity<Map> response = get("/objetivos/" + objetivo + "/evolucao");

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(pontos(response)).hasSize(1);
        assertPonto(pontos(response).get(0), LocalDate.of(2026, 11, 30), 30, 30);
    }

    @Test
    void deveSomarExecucaoDeHabitoDentroDaJanelaDoVinculo() {
        UUID objetivo = criarObjetivo("Meta");
        UUID habito = criarHabitoDiario("Alongar", List.of(objetivo));
        LocalDate ontem = hoje.minusDays(1);
        jdbcTemplate.update(
                "update objetivo_habito set created_at = ? where habito_id = ? and objetivo_id = ?",
                Timestamp.from(instanteBrt(ontem)), habito, objetivo);
        executarHabito(habito, ontem, 30);

        ResponseEntity<Map> response = get("/objetivos/" + objetivo + "/evolucao");

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(pontos(response)).hasSize(1);
        assertPonto(pontos(response).get(0), ontem, 30, 30);
        assertThat(ultimoAcumulado(response)).isEqualTo(minutosConcluidosDoProgresso(objetivo));
    }

    @Test
    void habitoPausadoDeveSairDaCurvaEVoltarAoReativar() {
        UUID objetivo = criarObjetivo("Meta");
        UUID habito = criarHabitoDiario("Alongar", List.of(objetivo));
        LocalDate ontem = hoje.minusDays(1);
        jdbcTemplate.update(
                "update objetivo_habito set created_at = ? where habito_id = ? and objetivo_id = ?",
                Timestamp.from(instanteBrt(ontem)), habito, objetivo);
        executarHabito(habito, ontem, 30);

        post("/habitos/" + habito + "/pausar", null);
        assertThat(pontos(get("/objetivos/" + objetivo + "/evolucao"))).isEmpty();

        post("/habitos/" + habito + "/reativar", null);
        assertThat(pontos(get("/objetivos/" + objetivo + "/evolucao"))).hasSize(1);
    }

    @Test
    void tarefaDesvinculadaDeveSairDaCurva() {
        UUID objetivo = criarObjetivo("Meta");
        UUID tarefa = criarTarefa("Sai da curva", 60, List.of(objetivo));
        executarTarefa(tarefa, hoje.minusDays(2), 30, instanteBrt(hoje.minusDays(2)));
        assertThat(pontos(get("/objetivos/" + objetivo + "/evolucao"))).hasSize(1);

        patchTarefa(tarefa, Map.of("objetivoIds", List.of()));

        assertThat(pontos(get("/objetivos/" + objetivo + "/evolucao"))).isEmpty();
    }

    @Test
    void tarefaCanceladaNaoDeveAparecerNaCurva() {
        UUID objetivo = criarObjetivo("Meta");
        UUID tarefa = criarTarefa("Cancelada", 60, List.of(objetivo));
        executarTarefa(tarefa, hoje.minusDays(2), 30, instanteBrt(hoje.minusDays(2)));

        patchTarefa(tarefa, Map.of("status", "CANCELADA"));

        ResponseEntity<Map> response = get("/objetivos/" + objetivo + "/evolucao");
        assertThat(pontos(response)).isEmpty();
        assertThat(antes(response)).isZero();
    }

    @Test
    void objetivoArquivadoDeveContinuarRespondendoComOsPontos() {
        UUID objetivo = criarObjetivo("Meta");
        UUID tarefa = criarTarefa("Feita", 60, List.of(objetivo));
        executarTarefa(tarefa, hoje.minusDays(2), 30, instanteBrt(hoje.minusDays(2)));
        post("/objetivos/" + objetivo + "/arquivar", null);

        ResponseEntity<Map> response = get("/objetivos/" + objetivo + "/evolucao");

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(pontos(response)).hasSize(1);
    }

    @Test
    void objetivoSemItensDeveDevolverListaVazia() {
        UUID objetivo = criarObjetivo("Vazio");

        ResponseEntity<Map> response = get("/objetivos/" + objetivo + "/evolucao");

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(antes(response)).isZero();
        assertThat(pontos(response)).isEmpty();
    }

    @Test
    void deveRetornar404ParaObjetivoDeOutroUsuarioSemRevelarOsPontos() {
        String tokenOutro = cadastrarELogar("outro-evolucao-" + UUID.randomUUID() + "@email.com");
        ResponseEntity<Map> criado = restTemplate.exchange("/objetivos", HttpMethod.POST,
                autenticadoJson(tokenOutro, Map.of("titulo", "Do outro")), Map.class);
        UUID doOutro = id(criado);

        ResponseEntity<Map> response = get("/objetivos/" + doOutro + "/evolucao");

        assertProblema(response, NOT_FOUND, "Objetivo não encontrado");
        assertThat(response.getBody().get("pontos")).isNull();
    }

    @Test
    void deveRetornar404ParaObjetivoInexistente() {
        ResponseEntity<Map> response = get("/objetivos/" + UUID.randomUUID() + "/evolucao");

        assertProblema(response, NOT_FOUND, "Objetivo não encontrado");
    }

    @Test
    void deveRetornar401SemToken() {
        UUID objetivo = criarObjetivo("Meta");

        ResponseEntity<Map> response = restTemplate.getForEntity("/objetivos/" + objetivo + "/evolucao", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
    }

    // --- helpers -------------------------------------------------------------------------------

    private String cadastrarELogar(String email) {
        assertThat(restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Dono Evolucao", "email", email, "senha", SENHA)),
                Map.class).getStatusCode()).isEqualTo(CREATED);
        ResponseEntity<Map> login = restTemplate.postForEntity(
                "/auth/login", json(Map.of("email", email, "senha", SENHA)), Map.class);
        assertThat(login.getStatusCode()).isEqualTo(OK);
        return login.getBody().get("accessToken").toString();
    }

    private UUID criarObjetivo(String titulo) {
        ResponseEntity<Map> response = post("/objetivos", Map.of("titulo", titulo));
        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        return id(response);
    }

    private UUID criarTarefa(String titulo, int duracaoMinutos, List<UUID> objetivoIds) {
        Map<String, Object> body = new HashMap<>();
        body.put("titulo", titulo);
        body.put("duracaoMinutos", duracaoMinutos);
        body.put("objetivoIds", objetivoIds.stream().map(UUID::toString).toList());
        ResponseEntity<Map> response = post("/tarefas", body);
        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        return id(response);
    }

    private UUID criarFilho(UUID paiId, String titulo, int duracaoMinutos) {
        ResponseEntity<Map> response = post("/tarefas", Map.of(
                "titulo", titulo, "duracaoMinutos", duracaoMinutos, "parentId", paiId.toString()));
        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        return id(response);
    }

    private UUID criarHabitoDiario(String titulo, List<UUID> objetivoIds) {
        ResponseEntity<Map> response = post("/habitos", Map.of(
                "titulo", titulo,
                "duracaoMinutos", 30,
                "diasSemana", List.of("SEGUNDA", "TERCA", "QUARTA", "QUINTA", "SEXTA", "SABADO", "DOMINGO"),
                "objetivoIds", objetivoIds.stream().map(UUID::toString).toList()));
        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        return id(response);
    }

    private void finalizar(UUID tarefaId) {
        patchTarefa(tarefaId, Map.of("status", "FINALIZADA"));
    }

    private void patchTarefa(UUID tarefaId, Map<String, Object> body) {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/tarefas/" + tarefaId, HttpMethod.PATCH, autenticadoJson(accessToken, body), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(OK);
    }

    /** Uma execução CONCLUIDO de tarefa: o minuto canônico é a duração do bloco. */
    private void executarTarefa(UUID tarefaId, LocalDate diaDoPlano, int minutosBloco, Instant concluidoEm) {
        UUID blocoId = semearPlanoEBloco(diaDoPlano, minutosBloco, tarefaId, null);
        semearExecucao(blocoId, concluidoEm, null);
    }

    private void executarHabito(UUID habitoId, LocalDate diaDoPlano, int minutos) {
        UUID blocoId = semearPlanoEBloco(diaDoPlano, minutos, null, habitoId);
        semearExecucao(blocoId, instanteBrt(diaDoPlano), minutos);
    }

    private UUID semearPlanoEBloco(LocalDate dia, int minutosBloco, UUID tarefaId, UUID habitoId) {
        UUID planoId = UUID.randomUUID();
        UUID blocoId = UUID.randomUUID();
        Timestamp agora = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                insert into plano (id, usuario_id, data, versao, status, tem_conflito, itens_nao_alocados,
                                   gerado_em, created_at, updated_at)
                values (?, ?, ?, ?, 'TAREFA_AVULSA', false, '[]'::jsonb, ?, ?, ?)
                """, planoId, usuarioId, Date.valueOf(dia), versaoPlano.incrementAndGet(), agora, agora, agora);
        jdbcTemplate.update("""
                insert into bloco_plano (id, plano_id, tarefa_id, habito_id, duracao_minutos, ordem, created_at, updated_at)
                values (?, ?, ?, ?, ?, 1, ?, ?)
                """, blocoId, planoId, tarefaId, habitoId, minutosBloco, agora, agora);
        return blocoId;
    }

    private void semearExecucao(UUID blocoId, Instant concluidoEm, Integer minutosInformados) {
        Timestamp agora = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                insert into execucao (id, usuario_id, bloco_plano_id, status, iniciado_em, concluido_em,
                                      minutos_informados, created_at, updated_at)
                values (?, ?, ?, 'CONCLUIDO', ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), usuarioId, blocoId, Timestamp.from(concluidoEm), Timestamp.from(concluidoEm),
                minutosInformados, agora, agora);
    }

    private int minutosConcluidosDoProgresso(UUID objetivoId) {
        Map<String, Object> progresso = progressoDe(get("/objetivos/" + objetivoId));
        return progresso == null ? 0 : ((Number) progresso.get("minutosConcluidos")).intValue();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> progressoDe(ResponseEntity<Map> detalhe) {
        return (Map<String, Object>) detalhe.getBody().get("progresso");
    }

    private static int antes(ResponseEntity<Map> response) {
        return ((Number) response.getBody().get("antesDoHistorico")).intValue();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> pontos(ResponseEntity<Map> response) {
        return (List<Map<String, Object>>) response.getBody().get("pontos");
    }

    private static int ultimoAcumulado(ResponseEntity<Map> response) {
        List<Map<String, Object>> pontos = pontos(response);
        return pontos.isEmpty()
                ? antes(response)
                : ((Number) pontos.get(pontos.size() - 1).get("minutosAcumulados")).intValue();
    }

    private static void assertPonto(Map<String, Object> ponto, LocalDate data, int noDia, int acumulado) {
        assertThat(ponto.get("data")).isEqualTo(data.toString());
        assertThat(((Number) ponto.get("minutosNoDia")).intValue()).isEqualTo(noDia);
        assertThat(((Number) ponto.get("minutosAcumulados")).intValue()).isEqualTo(acumulado);
    }

    private static Instant instanteBrt(LocalDate dia) {
        return LocalDateTime.of(dia.getYear(), dia.getMonth(), dia.getDayOfMonth(), 12, 0)
                .atZone(PeriodoBRT.ZONE)
                .toInstant();
    }

    private static UUID id(ResponseEntity<Map> response) {
        return UUID.fromString(response.getBody().get("id").toString());
    }

    private ResponseEntity<Map> post(String path, Object body) {
        return restTemplate.exchange(path, HttpMethod.POST, autenticadoJson(accessToken, body), Map.class);
    }

    private ResponseEntity<Map> get(String path) {
        return restTemplate.exchange(path, HttpMethod.GET, autenticadoJson(accessToken, null), Map.class);
    }

    private static void assertProblema(ResponseEntity<Map> response, HttpStatus status, String title) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("title")).isEqualTo(title);
    }

    private static HttpEntity<?> autenticadoJson(String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    private static HttpEntity<?> json(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
