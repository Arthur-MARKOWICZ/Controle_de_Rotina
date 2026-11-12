package com.controlerotina.pendencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;

import java.time.Instant;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class EsperaIT {

    private static final String SENHA = "Senha@123";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private String accessToken;

    @BeforeEach
    void cadastrarELogar() {
        String email = "espera-" + UUID.randomUUID() + "@email.com";
        assertThat(restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Dono Espera", "email", email, "senha", SENHA)),
                Map.class).getStatusCode()).isEqualTo(CREATED);

        ResponseEntity<Map> login = restTemplate.postForEntity(
                "/auth/login",
                json(Map.of("email", email, "senha", SENHA)),
                Map.class);
        assertThat(login.getStatusCode()).isEqualTo(OK);
        accessToken = login.getBody().get("accessToken").toString();
    }

    @Test
    void deveCriarEsperaTarefaParaTarefaCom201() {
        UUID origem = criarTarefa("Origem A");
        UUID alvo = criarTarefa("Estudar");

        ResponseEntity<Map> criada = criarEsperaTarefa(origem, "TAREFA", alvo);

        assertThat(criada.getStatusCode()).isEqualTo(CREATED);
        assertEspera(criada.getBody(), "TAREFA", alvo, "Estudar");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from espera where origem_id = ? and alvo_id = ?",
                Integer.class,
                origem,
                alvo
        )).isEqualTo(1);
    }

    @Test
    void deveCriarEsperaTarefaParaProjetoCom201() {
        UUID origem = criarTarefa("Origem T");
        UUID alvo = criarProjeto("Projeto Alvo");

        ResponseEntity<Map> criada = criarEsperaTarefa(origem, "PROJETO", alvo);

        assertThat(criada.getStatusCode()).isEqualTo(CREATED);
        assertEspera(criada.getBody(), "PROJETO", alvo, "Projeto Alvo");
    }

    @Test
    void deveCriarEsperaProjetoParaTarefaCom201() {
        UUID origem = criarProjeto("Projeto Origem");
        UUID alvo = criarTarefa("Tarefa Alvo");

        ResponseEntity<Map> criada = criarEsperaProjeto(origem, "TAREFA", alvo);

        assertThat(criada.getStatusCode()).isEqualTo(CREATED);
        assertEspera(criada.getBody(), "TAREFA", alvo, "Tarefa Alvo");
    }

    @Test
    void deveCriarEsperaProjetoParaProjetoCom201() {
        UUID origem = criarProjeto("Projeto P");
        UUID alvo = criarProjeto("Projeto Q");

        ResponseEntity<Map> criada = criarEsperaProjeto(origem, "PROJETO", alvo);

        assertThat(criada.getStatusCode()).isEqualTo(CREATED);
        assertEspera(criada.getBody(), "PROJETO", alvo, "Projeto Q");
    }

    @Test
    void deveListarEsperasDaOrigem() {
        UUID origem = criarTarefa("Origem lista");
        UUID alvo = criarTarefa("Estudar");
        criarEsperaTarefa(origem, "TAREFA", alvo);

        ResponseEntity<List> lista = restTemplate.exchange(
                "/tarefas/" + origem + "/esperas",
                HttpMethod.GET,
                autenticado(),
                List.class);

        assertThat(lista.getStatusCode()).isEqualTo(OK);
        assertThat(lista.getBody()).hasSize(1);
        assertThat(((Map<?, ?>) lista.getBody().get(0)).get("alvoTitulo")).isEqualTo("Estudar");
    }

    @Test
    void deveRetornar404QuandoAlvoEDeOutroUsuario() {
        UUID origem = criarTarefa("Minha origem");
        UUID alvoAlheio = criarTarefaComToken(tokenDeNovoUsuario(), "Alheia");

        ResponseEntity<Map> response = criarEsperaTarefa(origem, "TAREFA", alvoAlheio);

        assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from espera where origem_id = ?",
                Integer.class,
                origem
        )).isEqualTo(0);
    }

    @Test
    void deveRetornar400QuandoAutoEspera() {
        UUID tarefa = criarTarefa("Auto");

        ResponseEntity<Map> response = criarEsperaTarefa(tarefa, "TAREFA", tarefa);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
        assertThat(response.getBody().get("title")).isEqualTo("Espera inválida");
    }

    @Test
    void deveRetornar409QuandoParDuplicado() {
        UUID origem = criarTarefa("Origem dup");
        UUID alvo = criarTarefa("Alvo dup");
        assertThat(criarEsperaTarefa(origem, "TAREFA", alvo).getStatusCode()).isEqualTo(CREATED);

        ResponseEntity<Map> duplicata = criarEsperaTarefa(origem, "TAREFA", alvo);

        assertThat(duplicata.getStatusCode()).isEqualTo(CONFLICT);
        assertThat(duplicata.getBody().get("title")).isEqualTo("Espera já existe");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from espera where origem_id = ? and alvo_id = ?",
                Integer.class,
                origem,
                alvo
        )).isEqualTo(1);
    }

    @Test
    void deveRemoverEsperaCom204() {
        UUID origem = criarTarefa("Origem delete");
        UUID alvo = criarTarefa("Alvo delete");
        UUID esperaId = UUID.fromString(criarEsperaTarefa(origem, "TAREFA", alvo).getBody().get("id").toString());

        ResponseEntity<Void> removida = restTemplate.exchange(
                "/tarefas/" + origem + "/esperas/" + esperaId,
                HttpMethod.DELETE,
                autenticado(),
                Void.class);

        assertThat(removida.getStatusCode()).isEqualTo(NO_CONTENT);
        ResponseEntity<List> lista = restTemplate.exchange(
                "/tarefas/" + origem + "/esperas",
                HttpMethod.GET,
                autenticado(),
                List.class);
        assertThat(lista.getBody()).isEmpty();
        assertThat(jdbcTemplate.queryForObject("select count(*) from espera where id = ?", Integer.class, esperaId))
                .isEqualTo(0);

        ResponseEntity<Map> inexistente = restTemplate.exchange(
                "/tarefas/" + origem + "/esperas/" + esperaId,
                HttpMethod.DELETE,
                autenticado(),
                Map.class);
        assertThat(inexistente.getStatusCode()).isEqualTo(NOT_FOUND);
    }

    @Test
    void deveRemoverEsperaDeProjetoCom204() {
        UUID origem = criarProjeto("Projeto delete");
        UUID alvo = criarTarefa("Alvo do projeto");
        UUID esperaId = UUID.fromString(criarEsperaProjeto(origem, "TAREFA", alvo).getBody().get("id").toString());

        ResponseEntity<Void> removida = restTemplate.exchange(
                "/projetos/" + origem + "/esperas/" + esperaId,
                HttpMethod.DELETE,
                autenticado(),
                Void.class);

        assertThat(removida.getStatusCode()).isEqualTo(NO_CONTENT);
        assertThat(jdbcTemplate.queryForObject("select count(*) from espera where id = ?", Integer.class, esperaId))
                .isEqualTo(0);
    }

    @Test
    void deveManterEsperaIrresolvidaQuandoProjetoAlvoEstaVazio() {
        UUID origem = criarTarefa("Origem vazia");
        UUID alvo = criarProjeto("Projeto vazio");
        criarEsperaTarefa(origem, "PROJETO", alvo);

        Map<?, ?> espera = primeiraEspera(origem);

        assertThat(espera.get("resolvida")).isEqualTo(false);
    }

    @Test
    void deveMarcarEsperaResolvidaQuandoTodasTarefasDoProjetoAlvoEstaoFinalizadas() {
        UUID origem = criarTarefa("Origem pronta");
        UUID alvo = criarProjeto("Projeto pronto");
        UUID passoA = criarTarefaNoProjeto("Passo A", alvo);
        UUID passoB = criarTarefaNoProjeto("Passo B", alvo);
        criarEsperaTarefa(origem, "PROJETO", alvo);
        patchStatus(passoA, "FINALIZADA");
        patchStatus(passoB, "FINALIZADA");

        Map<?, ?> espera = primeiraEspera(origem);

        assertThat(espera.get("resolvida")).isEqualTo(true);
    }

    @Test
    void deveManterEsperaIrresolvidaQuandoProjetoAlvoTemTarefaAtrasada() {
        UUID origem = criarTarefa("Origem atrasada");
        UUID alvo = criarProjeto("Projeto atrasado");
        UUID feita = criarTarefaNoProjeto("Feita", alvo);
        UUID atrasada = criarTarefaNoProjeto("Atrasada", alvo);
        criarEsperaTarefa(origem, "PROJETO", alvo);
        patchStatus(feita, "FINALIZADA");
        jdbcTemplate.update("update tarefa set status = 'ATRASADA' where id = ?", atrasada);

        Map<?, ?> espera = primeiraEspera(origem);

        assertThat(espera.get("resolvida")).isEqualTo(false);
    }

    @Test
    void deveMarcarEsperaResolvidaQuandoAlvoTarefaEstaFinalizada() {
        UUID origem = criarTarefa("Origem finalizada");
        UUID alvo = criarTarefa("Alvo finalizada");
        criarEsperaTarefa(origem, "TAREFA", alvo);
        patchStatus(alvo, "FINALIZADA");

        Map<?, ?> espera = primeiraEspera(origem);

        assertThat(espera.get("resolvida")).isEqualTo(true);
    }

    @Test
    void deveManterEsperaIrresolvidaQuandoAlvoTarefaEstaCancelada() {
        UUID origem = criarTarefa("Origem cancelada");
        UUID alvo = criarTarefa("Alvo cancelada");
        criarEsperaTarefa(origem, "TAREFA", alvo);
        patchStatus(alvo, "CANCELADA");

        Map<?, ?> espera = primeiraEspera(origem);

        assertThat(espera.get("resolvida")).isEqualTo(false);
    }

    @Test
    void deveRetornar400QuandoCicloDireto() {
        UUID a = criarTarefa("Ciclo A");
        UUID b = criarTarefa("Ciclo B");
        assertThat(criarEsperaTarefa(a, "TAREFA", b).getStatusCode()).isEqualTo(CREATED);

        ResponseEntity<Map> ciclo = criarEsperaTarefa(b, "TAREFA", a);

        assertThat(ciclo.getStatusCode()).isEqualTo(BAD_REQUEST);
        assertThat(ciclo.getBody().get("title")).isEqualTo("Espera inválida");
        assertThat(ciclo.getBody().get("detail")).isEqualTo("Esta espera fecharia um ciclo.");
        assertThat(countEsperas(b, a)).isZero();
    }

    @Test
    void deveRetornar400QuandoCicloTransitivo() {
        UUID a = criarTarefa("Trans A");
        UUID b = criarTarefa("Trans B");
        UUID c = criarTarefa("Trans C");
        assertThat(criarEsperaTarefa(a, "TAREFA", b).getStatusCode()).isEqualTo(CREATED);
        assertThat(criarEsperaTarefa(b, "TAREFA", c).getStatusCode()).isEqualTo(CREATED);

        ResponseEntity<Map> ciclo = criarEsperaTarefa(c, "TAREFA", a);

        assertThat(ciclo.getStatusCode()).isEqualTo(BAD_REQUEST);
        assertThat(ciclo.getBody().get("title")).isEqualTo("Espera inválida");
        assertThat(ciclo.getBody().get("detail")).isEqualTo("Esta espera fecharia um ciclo.");
        assertThat(countEsperas(c, a)).isZero();
    }

    @Test
    void deveApagarEsperasNoSoftDeleteENaoRecriarNoRestore() {
        UUID origem = criarTarefa("Origem limpeza");
        UUID alvo = criarTarefa("Alvo limpeza");
        UUID outraOrigem = criarTarefa("Outra origem");
        criarEsperaTarefa(origem, "TAREFA", alvo);
        criarEsperaTarefa(outraOrigem, "TAREFA", origem);

        ResponseEntity<Void> deletada = restTemplate.exchange(
                "/tarefas/" + origem,
                HttpMethod.DELETE,
                autenticado(),
                Void.class);
        assertThat(deletada.getStatusCode()).isEqualTo(NO_CONTENT);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from espera where origem_id = ? or alvo_id = ?",
                Integer.class,
                origem,
                origem
        )).isZero();
        assertThat(listarEsperas(outraOrigem)).isEmpty();

        ResponseEntity<Map> restaurada = restTemplate.exchange(
                "/tarefas/" + origem + "/restore",
                HttpMethod.POST,
                autenticado(),
                Map.class);
        assertThat(restaurada.getStatusCode()).isEqualTo(OK);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from espera where origem_id = ? or alvo_id = ?",
                Integer.class,
                origem,
                origem
        )).isZero();
        assertThat(listarEsperas(outraOrigem)).isEmpty();
    }

    @Test
    void deveApagarEsperasAoArquivarProjeto() {
        UUID origem = criarTarefa("Origem arquivo");
        UUID projetoAlvo = criarProjeto("Projeto alvo arquivo");
        UUID projetoOrigem = criarProjeto("Projeto origem arquivo");
        UUID tarefaAlvo = criarTarefa("Tarefa alvo arquivo");
        criarEsperaTarefa(origem, "PROJETO", projetoAlvo);
        criarEsperaProjeto(projetoOrigem, "TAREFA", tarefaAlvo);

        ResponseEntity<Map> arquivadoAlvo = restTemplate.exchange(
                "/projetos/" + projetoAlvo + "/arquivar",
                HttpMethod.POST,
                autenticado(),
                Map.class);
        assertThat(arquivadoAlvo.getStatusCode()).isEqualTo(OK);
        assertThat(listarEsperas(origem)).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from espera where origem_id = ? or alvo_id = ?",
                Integer.class,
                projetoAlvo,
                projetoAlvo
        )).isZero();

        ResponseEntity<Map> arquivadoOrigem = restTemplate.exchange(
                "/projetos/" + projetoOrigem + "/arquivar",
                HttpMethod.POST,
                autenticado(),
                Map.class);
        assertThat(arquivadoOrigem.getStatusCode()).isEqualTo(OK);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from espera where origem_id = ? or alvo_id = ?",
                Integer.class,
                projetoOrigem,
                projetoOrigem
        )).isZero();
    }

    private static void assertEspera(Map<?, ?> body, String alvoTipo, UUID alvoId, String alvoTitulo) {
        assertThat(body).isNotNull();
        assertThat(body.get("id")).isNotNull();
        assertThat(body.get("alvoTipo")).isEqualTo(alvoTipo);
        assertThat(body.get("alvoId")).isEqualTo(alvoId.toString());
        assertThat(body.get("alvoTitulo")).isEqualTo(alvoTitulo);
        assertThat(body.get("resolvida")).isEqualTo(false);
    }

    private ResponseEntity<Map> criarEsperaTarefa(UUID origemId, String alvoTipo, UUID alvoId) {
        return restTemplate.exchange(
                "/tarefas/" + origemId + "/esperas",
                HttpMethod.POST,
                autenticadoJson(Map.of("alvoTipo", alvoTipo, "alvoId", alvoId.toString())),
                Map.class);
    }

    private ResponseEntity<Map> criarEsperaProjeto(UUID origemId, String alvoTipo, UUID alvoId) {
        return restTemplate.exchange(
                "/projetos/" + origemId + "/esperas",
                HttpMethod.POST,
                autenticadoJson(Map.of("alvoTipo", alvoTipo, "alvoId", alvoId.toString())),
                Map.class);
    }

    private Map<?, ?> primeiraEspera(UUID origemId) {
        List<?> lista = listarEsperas(origemId);
        assertThat(lista).hasSize(1);
        return (Map<?, ?>) lista.get(0);
    }

    private List<?> listarEsperas(UUID origemId) {
        ResponseEntity<List> lista = restTemplate.exchange(
                "/tarefas/" + origemId + "/esperas",
                HttpMethod.GET,
                autenticado(),
                List.class);
        assertThat(lista.getStatusCode()).isEqualTo(OK);
        return lista.getBody();
    }

    private Integer countEsperas(UUID origemId, UUID alvoId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from espera where origem_id = ? and alvo_id = ?",
                Integer.class,
                origemId,
                alvoId
        );
    }

    private void patchStatus(UUID tarefaId, String status) {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/tarefas/" + tarefaId,
                HttpMethod.PATCH,
                autenticadoJson(Map.of("status", status)),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(OK);
    }

    private UUID criarTarefaNoProjeto(String titulo, UUID projetoId) {
        Map<String, Object> body = new HashMap<>();
        body.put("titulo", titulo);
        body.put("descricao", "desc");
        body.put("prioridade", "MEDIA");
        body.put("prazo", Instant.now().plus(2, ChronoUnit.DAYS).toString());
        body.put("duracaoMinutos", 30);
        body.put("projetoId", projetoId.toString());
        ResponseEntity<Map> response = restTemplate.exchange(
                "/tarefas",
                HttpMethod.POST,
                autenticadoJson(body),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        return UUID.fromString(response.getBody().get("id").toString());
    }

    private UUID criarTarefa(String titulo) {
        return criarTarefaComToken(accessToken, titulo);
    }

    private UUID criarTarefaComToken(String token, String titulo) {
        Map<String, Object> body = new HashMap<>();
        body.put("titulo", titulo);
        body.put("descricao", "desc");
        body.put("prioridade", "MEDIA");
        body.put("prazo", Instant.now().plus(2, ChronoUnit.DAYS).toString());
        body.put("duracaoMinutos", 30);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        ResponseEntity<Map> response = restTemplate.exchange(
                "/tarefas",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
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

    private String tokenDeNovoUsuario() {
        String email = "outro-espera-" + UUID.randomUUID() + "@email.com";
        restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Outro", "email", email, "senha", SENHA)),
                Map.class);
        return restTemplate.postForEntity(
                "/auth/login",
                json(Map.of("email", email, "senha", SENHA)),
                Map.class).getBody().get("accessToken").toString();
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
        return new HttpEntity<>(body, headers);
    }

    private static HttpEntity<Map<String, ?>> json(Map<String, ?> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
