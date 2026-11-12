package com.controlerotina.pendencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
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

import com.controlerotina.capacidade.entity.CapacidadeOverride;
import com.controlerotina.capacidade.repository.CapacidadeOverrideRepository;
import com.controlerotina.planner.entity.Plano;
import com.controlerotina.planner.repository.BlocoPlanoRepository;
import com.controlerotina.planner.service.PlannerGeracaoService;

/**
 * Tarefa bloqueada por espera fora do ajuste, do finalizar e da sessão,
 * e campos {@code esperas} / {@code bloqueada} nas respostas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class EsperaBloqueioIT {

    private static final String SENHA = "Senha@123";
    private static final ZoneId BRT = ZoneId.of("America/Sao_Paulo");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    CapacidadeOverrideRepository capacidadeOverrideRepository;

    @Autowired
    PlannerGeracaoService plannerGeracaoService;

    @Autowired
    BlocoPlanoRepository blocoPlanoRepository;

    private String accessToken;
    private UUID usuarioId;
    private LocalDate hoje;

    @BeforeEach
    void cadastrarELogar() {
        hoje = LocalDate.now(BRT);
        String email = "bloqueio-" + UUID.randomUUID() + "@email.com";
        assertThat(restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Dono Bloqueio", "email", email, "senha", SENHA)),
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

    // ---------- 3.2 ajuste do rascunho ----------

    @Test
    void naoDeveAdicionarBlocoDeTarefaBloqueadaAoRascunho() {
        salvarOverride(3);
        UUID bloqueada = criarTarefa("Bloqueada ajuste");
        UUID alvo = criarTarefa("Alvo ajuste");
        criarEsperaTarefa(bloqueada, "TAREFA", alvo);
        Plano plano = plannerGeracaoService.gerar(usuarioId, hoje);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/planos/" + plano.getId() + "/blocos",
                HttpMethod.POST,
                autenticadoJson(Map.of("tarefaId", bloqueada.toString())),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(CONFLICT);
        assertThat(response.getBody().get("title")).isEqualTo("Tarefa bloqueada");
        assertThat(blocoPlanoRepository.existsByPlanoIdAndTarefaId(plano.getId(), bloqueada)).isFalse();
    }

    // ---------- 3.3 finalizar ----------

    @Test
    void naoDeveFinalizarTarefaBloqueada() {
        UUID bloqueada = criarTarefa("Bloqueada finalizar");
        UUID alvo = criarTarefa("Alvo finalizar");
        criarEsperaTarefa(bloqueada, "TAREFA", alvo);

        ResponseEntity<Map> response = patchStatus(bloqueada, "FINALIZADA");

        assertThat(response.getStatusCode()).isEqualTo(CONFLICT);
        assertThat(response.getBody().get("title")).isEqualTo("Tarefa bloqueada");
        assertThat(statusDe(bloqueada)).isEqualTo("PENDENTE");
    }

    @Test
    void deveFinalizarTarefaQuandoEsperaResolve() {
        UUID origem = criarTarefa("Origem destravada");
        UUID alvo = criarTarefa("Alvo destravado");
        criarEsperaTarefa(origem, "TAREFA", alvo);
        assertThat(patchStatus(alvo, "FINALIZADA").getStatusCode()).isEqualTo(OK);

        assertThat(patchStatus(origem, "FINALIZADA").getStatusCode()).isEqualTo(OK);
        assertThat(statusDe(origem)).isEqualTo("FINALIZADA");
    }

    // ---------- 3.4 sessão ----------

    @Test
    void naoDeveIniciarSessaoEmTarefaComEsperaPropria() {
        UUID bloqueada = criarTarefa("Bloqueada sessão");
        UUID alvo = criarTarefa("Alvo sessão");
        criarEsperaTarefa(bloqueada, "TAREFA", alvo);

        ResponseEntity<Map> response = iniciar(bloqueada);

        assertThat(response.getStatusCode()).isEqualTo(CONFLICT);
        assertThat(response.getBody().get("title")).isEqualTo("Tarefa bloqueada");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from bloco_plano where tarefa_id = ?",
                Integer.class,
                bloqueada
        )).isZero();
    }

    @Test
    void naoDeveIniciarSessaoEmFolhaDePaiBloqueado() {
        UUID pai = criarTarefa("Pai bloqueado sessão");
        UUID folha = criarSubtarefa("Folha herdeira", pai);
        UUID alvo = criarTarefa("Alvo do pai sessão");
        criarEsperaTarefa(pai, "TAREFA", alvo);

        ResponseEntity<Map> response = iniciar(folha);

        assertThat(response.getStatusCode()).isEqualTo(CONFLICT);
        assertThat(response.getBody().get("title")).isEqualTo("Tarefa bloqueada");
    }

    @Test
    void naoDeveConcluirDiretoEmTarefaDeProjetoBloqueado() {
        UUID projeto = criarProjeto("Projeto bloqueado sessão");
        UUID tarefa = criarTarefaNoProjeto("Tarefa do projeto", projeto);
        UUID alvo = criarTarefa("Alvo do projeto sessão");
        criarEsperaProjeto(projeto, "TAREFA", alvo);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/tarefas/" + tarefa + "/concluir-direto",
                HttpMethod.POST,
                autenticadoJson(Map.of("duracaoMinutos", 30, "data", hoje.toString())),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(CONFLICT);
        assertThat(response.getBody().get("title")).isEqualTo("Tarefa bloqueada");
        assertThat(statusDe(tarefa)).isEqualTo("PENDENTE");
    }

    @Test
    void deveIniciarSessaoEmFolhaDesbloqueada() {
        UUID livre = criarTarefa("Folha livre");

        ResponseEntity<Map> response = iniciar(livre);

        assertThat(response.getStatusCode()).isIn(CREATED, OK);
        assertThat(response.getBody().get("status")).isEqualTo("EM_ANDAMENTO");
    }

    // ---------- 4.1 enrich das respostas ----------

    @Test
    void deveExporEsperasEBloqueadaNoGetDaTarefa() {
        UUID origem = criarTarefa("Origem enrich");
        UUID alvo = criarTarefa("Estudar");
        criarEsperaTarefa(origem, "TAREFA", alvo);

        Map<?, ?> tarefa = buscarTarefa(origem);

        assertThat(tarefa.get("bloqueada")).isEqualTo(true);
        List<?> esperas = (List<?>) tarefa.get("esperas");
        assertThat(esperas).hasSize(1);
        Map<?, ?> espera = (Map<?, ?>) esperas.get(0);
        assertThat(espera.get("alvoId")).isEqualTo(alvo.toString());
        assertThat(espera.get("alvoTitulo")).isEqualTo("Estudar");
        assertThat(espera.get("resolvida")).isEqualTo(false);

        assertThat(patchStatus(alvo, "FINALIZADA").getStatusCode()).isEqualTo(OK);
        Map<?, ?> depois = buscarTarefa(origem);
        assertThat(depois.get("bloqueada")).isEqualTo(false);
        assertThat(((Map<?, ?>) ((List<?>) depois.get("esperas")).get(0)).get("resolvida")).isEqualTo(true);
    }

    @Test
    void deveExporBloqueadaNaListagemDeTarefas() {
        UUID origem = criarTarefa("Origem lista");
        UUID alvo = criarTarefa("Alvo lista");
        UUID livre = criarTarefa("Livre lista");
        criarEsperaTarefa(origem, "TAREFA", alvo);

        ResponseEntity<Map> lista = restTemplate.exchange(
                "/tarefas?size=50",
                HttpMethod.GET,
                autenticado(),
                Map.class);
        assertThat(lista.getStatusCode()).isEqualTo(OK);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> conteudo = (List<Map<String, Object>>) lista.getBody().get("content");
        assertThat(bloqueadaNaLista(conteudo, origem)).isTrue();
        assertThat(bloqueadaNaLista(conteudo, livre)).isFalse();
        assertThat(esperasNaLista(conteudo, origem)).hasSize(1);
    }

    @Test
    void deveExporEsperasEBloqueadaNoGetDoProjeto() {
        UUID projeto = criarProjeto("Projeto enrich");
        UUID semEspera = criarProjeto("Projeto livre");
        UUID alvo = criarTarefa("Alvo do projeto enrich");
        criarEsperaProjeto(projeto, "TAREFA", alvo);

        Map<?, ?> comEspera = buscarProjeto(projeto);
        assertThat(comEspera.get("bloqueada")).isEqualTo(true);
        assertThat((List<?>) comEspera.get("esperas")).hasSize(1);
        assertThat(((Map<?, ?>) ((List<?>) comEspera.get("esperas")).get(0)).get("alvoTitulo"))
                .isEqualTo("Alvo do projeto enrich");

        Map<?, ?> livre = buscarProjeto(semEspera);
        assertThat(livre.get("bloqueada")).isEqualTo(false);
        assertThat((List<?>) livre.get("esperas")).isEmpty();
    }

    // ---------- helpers ----------

    private static boolean bloqueadaNaLista(List<Map<String, Object>> conteudo, UUID tarefaId) {
        return conteudo.stream()
                .filter(item -> tarefaId.toString().equals(item.get("id")))
                .findFirst()
                .map(item -> Boolean.TRUE.equals(item.get("bloqueada")))
                .orElseThrow(() -> new AssertionError("tarefa não veio na listagem: " + tarefaId));
    }

    private static List<?> esperasNaLista(List<Map<String, Object>> conteudo, UUID tarefaId) {
        return conteudo.stream()
                .filter(item -> tarefaId.toString().equals(item.get("id")))
                .findFirst()
                .map(item -> (List<?>) item.get("esperas"))
                .orElseThrow(() -> new AssertionError("tarefa não veio na listagem: " + tarefaId));
    }

    private ResponseEntity<Map> iniciar(UUID tarefaId) {
        return restTemplate.exchange(
                "/tarefas/" + tarefaId + "/iniciar",
                HttpMethod.POST,
                autenticadoJson(Map.of("duracaoMinutos", 30, "data", hoje.toString())),
                Map.class);
    }

    private Map<?, ?> buscarTarefa(UUID tarefaId) {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/tarefas/" + tarefaId,
                HttpMethod.GET,
                autenticado(),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(OK);
        return response.getBody();
    }

    private Map<?, ?> buscarProjeto(UUID projetoId) {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/projetos/" + projetoId,
                HttpMethod.GET,
                autenticado(),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(OK);
        return response.getBody();
    }

    private String statusDe(UUID tarefaId) {
        return jdbcTemplate.queryForObject(
                "select status from tarefa where id = ?",
                String.class,
                tarefaId);
    }

    private ResponseEntity<Map> patchStatus(UUID tarefaId, String status) {
        return restTemplate.exchange(
                "/tarefas/" + tarefaId,
                HttpMethod.PATCH,
                autenticadoJson(Map.of("status", status)),
                Map.class);
    }

    private void criarEsperaTarefa(UUID origemId, String alvoTipo, UUID alvoId) {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/tarefas/" + origemId + "/esperas",
                HttpMethod.POST,
                autenticadoJson(Map.of("alvoTipo", alvoTipo, "alvoId", alvoId.toString())),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(CREATED);
    }

    private void criarEsperaProjeto(UUID origemId, String alvoTipo, UUID alvoId) {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/projetos/" + origemId + "/esperas",
                HttpMethod.POST,
                autenticadoJson(Map.of("alvoTipo", alvoTipo, "alvoId", alvoId.toString())),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(CREATED);
    }

    private void salvarOverride(int horas) {
        Instant agora = Instant.now();
        capacidadeOverrideRepository.save(new CapacidadeOverride(
                UUID.randomUUID(),
                usuarioId,
                hoje,
                horas,
                true,
                agora,
                agora
        ));
    }

    private UUID criarTarefa(String titulo) {
        return criarTarefa(titulo, null, null);
    }

    private UUID criarTarefaNoProjeto(String titulo, UUID projetoId) {
        return criarTarefa(titulo, projetoId, null);
    }

    private UUID criarSubtarefa(String titulo, UUID parentId) {
        return criarTarefa(titulo, null, parentId);
    }

    private UUID criarTarefa(String titulo, UUID projetoId, UUID parentId) {
        Map<String, Object> body = new HashMap<>();
        body.put("titulo", titulo);
        body.put("descricao", "desc");
        body.put("prioridade", "MEDIA");
        body.put("prazo", Instant.now().plus(3, ChronoUnit.DAYS).toString());
        body.put("duracaoMinutos", 60);
        if (projetoId != null) {
            body.put("projetoId", projetoId.toString());
        }
        if (parentId != null) {
            body.put("parentId", parentId.toString());
        }
        ResponseEntity<Map> response = restTemplate.exchange(
                "/tarefas",
                HttpMethod.POST,
                autenticadoJson(body),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        return UUID.fromString(response.getBody().get("id").toString());
    }

    private UUID criarProjeto(String nome) {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/projetos",
                HttpMethod.POST,
                autenticadoJson(Map.of("nome", nome)),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        return UUID.fromString(response.getBody().get("id").toString());
    }

    private HttpEntity<Void> autenticado() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return new HttpEntity<>(headers);
    }

    private HttpEntity<Map<String, Object>> autenticadoJson(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        return new HttpEntity<>(new HashMap<>(body), headers);
    }

    private static HttpEntity<Map<String, ?>> json(Map<String, ?> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(new HashMap<>(body), headers);
    }
}
