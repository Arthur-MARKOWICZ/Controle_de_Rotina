package com.controlerotina.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

/** Formato estável de paginação: {@code { content, page: { size, number, totalElements, totalPages } }}. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class PaginacaoContratoIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    TestRestTemplate rest;

    private String token;

    @BeforeEach
    void setUp() {
        token = ContratoTestSupport.tokenNovoUsuario(rest);
    }

    @Test
    void deveDevolverMetadadosNoObjetoPageEmPaginaIntermediaria() {
        for (int i = 0; i < 45; i++) {
            post("/tarefas", Map.of("titulo", "T" + i, "duracaoMinutos", 30));
        }

        ResponseEntity<Map> response = get("/tarefas?page=1&size=20");

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat((List<?>) response.getBody().get("content")).hasSize(20);
        assertThat(response.getBody().get("page"))
                .isEqualTo(Map.of("size", 20, "number", 1, "totalElements", 45, "totalPages", 3));
    }

    @Test
    void deveDevolverContentVazioETotaisZeroQuandoNaoHaItens() {
        ResponseEntity<Map> response = get("/habitos");

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat((List<?>) response.getBody().get("content")).isEmpty();
        Map<?, ?> page = (Map<?, ?>) response.getBody().get("page");
        assertThat(page.get("totalElements")).isEqualTo(0);
        assertThat(page.get("totalPages")).isEqualTo(0);
    }

    @Test
    void deveManterTotalQuandoPaginaPassaDoFim() {
        for (int i = 0; i < 5; i++) {
            post("/projetos", Map.of("nome", "P" + i));
        }

        ResponseEntity<Map> response = get("/projetos?page=3&size=20");

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat((List<?>) response.getBody().get("content")).isEmpty();
        Map<?, ?> page = (Map<?, ?>) response.getBody().get("page");
        assertThat(page.get("totalElements")).isEqualTo(5);
        assertThat(page.get("number")).isEqualTo(3);
    }

    @Test
    void deveIncluirChavePrazoComValorNullQuandoTarefaNaoTemPrazo() {
        post("/tarefas", Map.of("titulo", "Sem prazo", "duracaoMinutos", 30));

        @SuppressWarnings("unchecked")
        Map<String, Object> tarefa = (Map<String, Object>) ((List<?>) get("/tarefas").getBody().get("content")).get(0);

        assertThat(tarefa).containsKey("prazo");
        assertThat(tarefa.get("prazo")).isNull();
        assertThat(tarefa).containsKeys("projetoId", "projetoNome", "deletedAt", "parentId", "parentTitulo");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/tarefas", "/habitos", "/projetos", "/decidir"})
    void naoDeveExporCamposDePaginacaoNaRaiz(String path) {
        ResponseEntity<Map> response = get(path);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody().keySet()).containsExactlyInAnyOrder("content", "page");
    }

    @Test
    void naoDeveVazarItensDeOutroUsuarioNaContagem() {
        post("/decidir", Map.of("titulo", "Do outro"));
        token = ContratoTestSupport.tokenNovoUsuario(rest);

        ResponseEntity<Map> response = get("/decidir");

        assertThat((List<?>) response.getBody().get("content")).isEmpty();
        assertThat(((Map<?, ?>) response.getBody().get("page")).get("totalElements")).isEqualTo(0);
    }

    @Test
    void deveRetornar401SemTokenSemCorpoDePagina() {
        token = null;

        ResponseEntity<Map> response = get("/tarefas");

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
        assertThat(response.getBody().get("title")).isEqualTo("Não autorizado");
        assertThat(response.getBody()).doesNotContainKey("page");
    }

    @Test
    void deveCairNaPrimeiraPaginaQuandoPageNaoNumerico() {
        post("/tarefas", Map.of("titulo", "Unica", "duracaoMinutos", 30));

        ResponseEntity<Map> response = get("/tarefas?page=abc");

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(((Map<?, ?>) response.getBody().get("page")).get("number")).isEqualTo(0);
        assertThat((List<?>) response.getBody().get("content")).hasSize(1);
    }

    @Test
    void deveRetornar400ComTitleQuandoFiltroInvalidoSemCorpoDePagina() {
        ResponseEntity<Map> response = get("/tarefas?status=NAO_EXISTE");

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().get("title")).isEqualTo("Parâmetro inválido");
        assertThat(response.getBody()).doesNotContainKey("page");
    }

    @Test
    void deveRetornar401ComTokenInvalido() {
        token = "nao-e-um-jwt";

        ResponseEntity<Map> response = get("/projetos");

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
        assertThat(response.getBody().get("title")).isEqualTo("Não autorizado");
    }

    private ResponseEntity<Map> get(String path) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers()), Map.class);
    }

    private void post(String path, Map<String, ?> body) {
        ResponseEntity<Map> response = rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers()), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(CREATED);
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }
}
