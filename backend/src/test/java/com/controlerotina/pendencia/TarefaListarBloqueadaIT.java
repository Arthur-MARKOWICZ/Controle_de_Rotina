package com.controlerotina.pendencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Filtro {@code GET /tarefas?bloqueada=}. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class TarefaListarBloqueadaIT {

    private static final String SENHA = "Senha@123";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    TestRestTemplate restTemplate;

    private String accessToken;

    @BeforeEach
    void cadastrarELogar() {
        accessToken = novoUsuario();
    }

    @Test
    void deveRetornarSoTarefasComEsperaPropriaQuandoBloqueadaTrue() {
        UUID origem = criarTarefa("Origem");
        UUID alvo = criarTarefa("Alvo");
        UUID livre = criarTarefa("Livre");
        criarEspera("/tarefas/" + origem, alvo);

        List<Map<String, Object>> conteudo = listar("bloqueada=true&size=50");

        assertThat(ids(conteudo)).containsExactly(origem.toString());
        assertThat(conteudo.get(0).get("bloqueada")).isEqualTo(true);
        assertThat((List<?>) conteudo.get(0).get("esperas")).hasSize(1);
        assertThat(ids(listar("bloqueada=false&size=50")))
                .contains(alvo.toString(), livre.toString())
                .doesNotContain(origem.toString());
    }

    @Test
    void deveIncluirBloqueioHerdadoDeProjetoEDePai() {
        UUID projeto = criarProjeto("Projeto que espera");
        UUID doProjeto = criarTarefa("Do projeto", projeto, null);
        UUID pai = criarTarefa("Pai que espera");
        UUID filha = criarTarefa("Filha", null, pai);
        UUID alvo = criarTarefa("Alvo comum");
        criarEspera("/projetos/" + projeto, alvo);
        criarEspera("/tarefas/" + pai, alvo);

        assertThat(ids(listar("bloqueada=true&size=50")))
                .contains(doProjeto.toString(), filha.toString(), pai.toString())
                .doesNotContain(alvo.toString());
    }

    @Test
    void naoDeveIncluirTarefaCujaEsperaJaResolveu() {
        UUID origem = criarTarefa("Origem resolvida");
        UUID alvo = criarTarefa("Alvo finalizado");
        criarEspera("/tarefas/" + origem, alvo);
        assertThat(patch(alvo, Map.of("status", "FINALIZADA")).getStatusCode()).isEqualTo(OK);

        assertThat(ids(listar("bloqueada=true&size=50"))).doesNotContain(origem.toString());
    }

    @Test
    void naoDeveVazarTarefasBloqueadasDeOutroUsuario() {
        UUID origem = criarTarefa("Minha bloqueada");
        criarEspera("/tarefas/" + origem, criarTarefa("Meu alvo"));
        String meuToken = accessToken;

        accessToken = novoUsuario();
        List<Map<String, Object>> doOutro = listar("bloqueada=true&size=50");

        assertThat(doOutro).isEmpty();
        accessToken = meuToken;
        assertThat(ids(listar("bloqueada=true&size=50"))).containsExactly(origem.toString());
    }

    @Test
    void devePaginarSobreOConjuntoFiltrado() {
        UUID alvo = criarTarefa("Alvo da paginação");
        for (int i = 0; i < 25; i++) {
            criarEspera("/tarefas/" + criarTarefa("Bloqueada " + i), alvo);
        }
        for (int i = 0; i < 10; i++) {
            criarTarefa("Livre " + i);
        }

        ResponseEntity<Map> response = get("/tarefas?bloqueada=true&size=20", accessToken);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat((List<?>) response.getBody().get("content")).hasSize(20);
        assertThat(((Number) totalElements(response)).intValue()).isEqualTo(25);
    }

    @Test
    void deveRetornar400QuandoBloqueadaInvalido() {
        ResponseEntity<Map> response = get("/tarefas?bloqueada=talvez", accessToken);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
        assertThat(response.getBody().get("title")).isEqualTo("Parâmetro inválido");
    }

    @Test
    void deveRetornar401SemToken() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/tarefas?bloqueada=true", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
        assertThat(response.getBody().get("title")).isEqualTo("Não autorizado");
    }

    @Test
    void deveRetornar401ComTokenMalformado() {
        ResponseEntity<Map> response = get("/tarefas?bloqueada=true", "nao-e-um-jwt");

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
        assertThat(response.getBody().get("title")).isEqualTo("Não autorizado");
    }

    // ---------- helpers ----------

    private static Object totalElements(ResponseEntity<Map> response) {
        Object total = response.getBody().get("totalElements");
        if (total == null && response.getBody().get("page") instanceof Map<?, ?> page) {
            total = page.get("totalElements");
        }
        return total;
    }

    private static List<String> ids(List<Map<String, Object>> conteudo) {
        return conteudo.stream().map(item -> item.get("id").toString()).toList();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listar(String query) {
        ResponseEntity<Map> response = get("/tarefas?" + query, accessToken);
        assertThat(response.getStatusCode()).isEqualTo(OK);
        return (List<Map<String, Object>>) response.getBody().get("content");
    }

    private ResponseEntity<Map> get(String url, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }

    private ResponseEntity<Map> patch(UUID tarefaId, Map<String, Object> body) {
        return restTemplate.exchange(
                "/tarefas/" + tarefaId, HttpMethod.PATCH, autenticadoJson(body), Map.class);
    }

    private String novoUsuario() {
        String email = "lista-bloqueada-" + UUID.randomUUID() + "@email.com";
        assertThat(restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Dono Lista", "email", email, "senha", SENHA)),
                Map.class).getStatusCode()).isEqualTo(CREATED);
        ResponseEntity<Map> login = restTemplate.postForEntity(
                "/auth/login", json(Map.of("email", email, "senha", SENHA)), Map.class);
        assertThat(login.getStatusCode()).isEqualTo(OK);
        return login.getBody().get("accessToken").toString();
    }

    private void criarEspera(String origemPath, UUID alvoTarefaId) {
        ResponseEntity<Map> response = restTemplate.exchange(
                origemPath + "/esperas",
                HttpMethod.POST,
                autenticadoJson(Map.of("alvoTipo", "TAREFA", "alvoId", alvoTarefaId.toString())),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(CREATED);
    }

    private UUID criarTarefa(String titulo) {
        return criarTarefa(titulo, null, null);
    }

    private UUID criarTarefa(String titulo, UUID projetoId, UUID parentId) {
        Map<String, Object> body = new HashMap<>();
        body.put("titulo", titulo);
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
                "/tarefas", HttpMethod.POST, autenticadoJson(body), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        return UUID.fromString(response.getBody().get("id").toString());
    }

    private UUID criarProjeto(String nome) {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/projetos", HttpMethod.POST, autenticadoJson(Map.of("nome", nome)), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        return UUID.fromString(response.getBody().get("id").toString());
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
