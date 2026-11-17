package com.controlerotina.objetivo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
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
class ObjetivoIT {

    private static final String SENHA = "Senha@123";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private String accessToken;
    private UUID usuarioId;
    private LocalDate hoje;

    @BeforeEach
    void cadastrarELogar() {
        String email = "objetivo-" + UUID.randomUUID() + "@email.com";
        accessToken = cadastrarELogar(email);
        usuarioId = jdbcTemplate.queryForObject("select id from usuario where email = ?", UUID.class, email);
        hoje = LocalDate.now(PeriodoBRT.ZONE);
    }

    @Test
    void deveRetornar401AoCriarSemToken() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/objetivos", json(Map.of("titulo", "Perder 5 kg")), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
    }

    @Test
    void deveCriarObjetivoSoComTituloCom201() {
        ResponseEntity<Map> response = post("/objetivos", Map.of("titulo", "  Perder 5 kg  "));

        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        Map<String, Object> body = response.getBody();
        assertThat(body.get("titulo")).isEqualTo("Perder 5 kg");
        assertThat(body.get("status")).isEqualTo("ATIVO");
        assertThat(body.get("descricao")).isNull();
        assertThat(body.get("prazo")).isNull();
        assertThat(body.get("concluidoEm")).isNull();
        assertThat(body.get("arquivadoEm")).isNull();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select usuario_id, titulo, status from objetivo where id = ?", id(response));
        assertThat(row.get("usuario_id")).isEqualTo(usuarioId);
        assertThat(row.get("titulo")).isEqualTo("Perder 5 kg");
        assertThat(row.get("status")).isEqualTo("ATIVO");
    }

    @Test
    void deveAceitarPrazoDeHoje() {
        ResponseEntity<Map> response = post("/objetivos", Map.of("titulo", "Hoje", "prazo", hoje.toString()));

        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        assertThat(response.getBody().get("prazo")).isEqualTo(hoje.toString());
    }

    @Test
    void deveRetornar400QuandoTituloEmBrancoSemPersistir() {
        ResponseEntity<Map> response = post("/objetivos", Map.of("titulo", "   "));

        assertProblema(response, BAD_REQUEST, "Dados inválidos");
        assertThat(propriedadesComErro(response)).contains("titulo");
        assertThat(totalObjetivos()).isZero();
    }

    @Test
    void deveRetornar400QuandoTituloOuDescricaoExcedemLimite() {
        ResponseEntity<Map> titulo = post("/objetivos", Map.of("titulo", "a".repeat(81)));
        ResponseEntity<Map> descricao = post("/objetivos", Map.of("titulo", "Ok", "descricao", "b".repeat(501)));

        assertProblema(titulo, BAD_REQUEST, "Dados inválidos");
        assertThat(propriedadesComErro(titulo)).contains("titulo");
        assertProblema(descricao, BAD_REQUEST, "Dados inválidos");
        assertThat(propriedadesComErro(descricao)).contains("descricao");
        assertThat(totalObjetivos()).isZero();
    }

    @Test
    void deveRetornar400QuandoPrazoDeOntemSemPersistir() {
        ResponseEntity<Map> response = post("/objetivos",
                Map.of("titulo", "Ontem", "prazo", hoje.minusDays(1).toString()));

        assertProblema(response, BAD_REQUEST, "Prazo inválido");
        assertThat(totalObjetivos()).isZero();
    }

    @Test
    void deveRetornar400QuandoPrazoEmFormatoInvalidoSemPersistir() {
        ResponseEntity<Map> response = post("/objetivos", Map.of("titulo", "Formato", "prazo", "31/12/2026"));

        assertProblema(response, BAD_REQUEST, "Requisição inválida");
        assertThat(totalObjetivos()).isZero();
    }

    @Test
    void deveRetornar404ParaTodasAsOperacoesEmObjetivoDeOutroUsuario() {
        String tokenOutro = cadastrarELogar("outro-" + UUID.randomUUID() + "@email.com");
        UUID idOutro = id(restTemplate.exchange("/objetivos", HttpMethod.POST,
                autenticadoJson(tokenOutro, Map.of("titulo", "Do outro")), Map.class));

        assertProblema(get("/objetivos/" + idOutro), NOT_FOUND, "Objetivo não encontrado");
        assertProblema(patch(idOutro, Map.of("titulo", "Invadido")), NOT_FOUND, "Objetivo não encontrado");
        assertProblema(post("/objetivos/" + idOutro + "/concluir", null), NOT_FOUND, "Objetivo não encontrado");
        assertProblema(post("/objetivos/" + idOutro + "/arquivar", null), NOT_FOUND, "Objetivo não encontrado");
        assertProblema(post("/objetivos/" + idOutro + "/reabrir", null), NOT_FOUND, "Objetivo não encontrado");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select titulo, status from objetivo where id = ?", idOutro);
        assertThat(row.get("titulo")).isEqualTo("Do outro");
        assertThat(row.get("status")).isEqualTo("ATIVO");
    }

    @Test
    void listagemNaoMostraObjetivosDeOutroUsuario() {
        String tokenOutro = cadastrarELogar("outro-" + UUID.randomUUID() + "@email.com");
        restTemplate.exchange("/objetivos", HttpMethod.POST,
                autenticadoJson(tokenOutro, Map.of("titulo", "Do outro")), Map.class);
        UUID meu = criar("Meu", null);

        List<Map<String, Object>> content = content(get("/objetivos"));

        assertThat(content).extracting(o -> o.get("id")).containsExactly(meu.toString());
    }

    @Test
    void patchSoComTituloMantemDescricaoEPrazo() {
        String prazo = hoje.plusDays(30).toString();
        UUID id = id(post("/objetivos", Map.of("titulo", "Antigo", "descricao", "detalhe", "prazo", prazo)));

        ResponseEntity<Map> response = patch(id, Map.of("titulo", "Novo"));

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody().get("titulo")).isEqualTo("Novo");
        assertThat(response.getBody().get("descricao")).isEqualTo("detalhe");
        assertThat(response.getBody().get("prazo")).isEqualTo(prazo);
    }

    @Test
    void patchComPrazoNuloRemovePrazo() {
        UUID id = criar("Com prazo", hoje.plusDays(10));
        Map<String, Object> body = new HashMap<>();
        body.put("prazo", null);

        ResponseEntity<Map> response = patch(id, body);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody().get("prazo")).isNull();
        assertThat(jdbcTemplate.queryForObject("select prazo from objetivo where id = ?", Date.class, id)).isNull();
    }

    @Test
    void patchEmObjetivoConcluidoMantemStatusEData() {
        UUID id = criar("Meta", null);
        post("/objetivos/" + id + "/concluir", null);
        Timestamp concluidoEm = dataNoBanco(id, "concluido_em");

        ResponseEntity<Map> response = patch(id, Map.of("titulo", "Meta batida"));

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody().get("titulo")).isEqualTo("Meta batida");
        assertThat(response.getBody().get("status")).isEqualTo("CONCLUIDO");
        assertThat(response.getBody().get("concluidoEm")).isNotNull();
        assertThat(dataNoBanco(id, "concluido_em")).isEqualTo(concluidoEm);
    }

    @Test
    void patchComTituloEmBrancoRetorna400SemAlterar() {
        UUID id = criar("Original", null);

        ResponseEntity<Map> response = patch(id, Map.of("titulo", "   "));

        assertProblema(response, BAD_REQUEST, "Dados inválidos");
        assertThat(propriedadesComErro(response)).contains("titulo");
        assertThat(tituloNoBanco(id)).isEqualTo("Original");
    }

    @Test
    void patchComPrazoMalformadoRetorna400SemAlterar() {
        UUID id = criar("Original", hoje.plusDays(5));

        ResponseEntity<Map> response = patch(id, Map.of("prazo", "amanhã"));

        assertProblema(response, BAD_REQUEST, "Requisição inválida");
        assertThat(prazoNoBanco(id)).isEqualTo(hoje.plusDays(5));
    }

    @Test
    void patchReenviandoMesmoPrazoVencidoAceitaEdicao() {
        UUID id = criar("Vencido", null);
        LocalDate vencido = hoje.minusDays(5);
        jdbcTemplate.update("update objetivo set prazo = ? where id = ?", Date.valueOf(vencido), id);

        ResponseEntity<Map> response = patch(id, Map.of("titulo", "Corrigido", "prazo", vencido.toString()));

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody().get("titulo")).isEqualTo("Corrigido");
        assertThat(response.getBody().get("prazo")).isEqualTo(vencido.toString());
    }

    @Test
    void patchTrocandoParaOutroPrazoNoPassadoRetorna400SemAlterar() {
        UUID id = criar("Vencido", null);
        LocalDate vencido = hoje.minusDays(5);
        jdbcTemplate.update("update objetivo set prazo = ? where id = ?", Date.valueOf(vencido), id);

        ResponseEntity<Map> response = patch(id, Map.of("titulo", "Outro", "prazo", hoje.minusDays(1).toString()));

        assertProblema(response, BAD_REQUEST, "Prazo inválido");
        assertThat(prazoNoBanco(id)).isEqualTo(vencido);
        assertThat(tituloNoBanco(id)).isEqualTo("Vencido");
    }

    @Test
    void deveConcluirObjetivoAtivo() {
        UUID id = criar("Meta", null);

        ResponseEntity<Map> response = post("/objetivos/" + id + "/concluir", null);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody().get("status")).isEqualTo("CONCLUIDO");
        assertThat(response.getBody().get("concluidoEm")).isNotNull();
        assertThat(response.getBody().get("arquivadoEm")).isNull();
    }

    @Test
    void deveRetornar409AoConcluirDuasVezesSemMudarData() {
        UUID id = criar("Meta", null);
        post("/objetivos/" + id + "/concluir", null);
        Timestamp antes = dataNoBanco(id, "concluido_em");

        ResponseEntity<Map> response = post("/objetivos/" + id + "/concluir", null);

        assertProblema(response, CONFLICT, "Objetivo já concluído");
        assertThat(dataNoBanco(id, "concluido_em")).isEqualTo(antes);
    }

    @Test
    void deveRetornar409AoConcluirObjetivoArquivado() {
        UUID id = criar("Meta", null);
        post("/objetivos/" + id + "/arquivar", null);

        ResponseEntity<Map> response = post("/objetivos/" + id + "/concluir", null);

        assertProblema(response, CONFLICT, "Objetivo arquivado");
        assertThat(statusNoBanco(id)).isEqualTo("ARQUIVADO");
        assertThat(dataNoBanco(id, "concluido_em")).isNull();
    }

    @Test
    void deveArquivarObjetivoAtivoSemDataDeConclusao() {
        UUID id = criar("Meta", null);

        ResponseEntity<Map> response = post("/objetivos/" + id + "/arquivar", null);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody().get("status")).isEqualTo("ARQUIVADO");
        assertThat(response.getBody().get("arquivadoEm")).isNotNull();
        assertThat(response.getBody().get("concluidoEm")).isNull();
    }

    @Test
    void deveArquivarConcluidoMantendoDataDeConclusao() {
        UUID id = criar("Meta", null);
        post("/objetivos/" + id + "/concluir", null);
        Timestamp concluidoEm = dataNoBanco(id, "concluido_em");

        ResponseEntity<Map> response = post("/objetivos/" + id + "/arquivar", null);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody().get("status")).isEqualTo("ARQUIVADO");
        assertThat(response.getBody().get("concluidoEm")).isNotNull();
        assertThat(response.getBody().get("arquivadoEm")).isNotNull();
        assertThat(dataNoBanco(id, "concluido_em")).isEqualTo(concluidoEm);
    }

    @Test
    void deveRetornar409AoArquivarDuasVezesSemMudarData() {
        UUID id = criar("Meta", null);
        post("/objetivos/" + id + "/arquivar", null);
        Timestamp antes = dataNoBanco(id, "arquivado_em");

        ResponseEntity<Map> response = post("/objetivos/" + id + "/arquivar", null);

        assertProblema(response, CONFLICT, "Objetivo já arquivado");
        assertThat(dataNoBanco(id, "arquivado_em")).isEqualTo(antes);
    }

    @Test
    void deveReabrirConcluido() {
        UUID id = criar("Meta", null);
        post("/objetivos/" + id + "/concluir", null);

        ResponseEntity<Map> response = post("/objetivos/" + id + "/reabrir", null);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody().get("status")).isEqualTo("ATIVO");
        assertThat(response.getBody().get("concluidoEm")).isNull();
    }

    @Test
    void deveReabrirArquivadoQueFoiConcluidoLimpandoAsDuasDatas() {
        UUID id = criar("Meta", null);
        post("/objetivos/" + id + "/concluir", null);
        post("/objetivos/" + id + "/arquivar", null);

        ResponseEntity<Map> response = post("/objetivos/" + id + "/reabrir", null);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody().get("status")).isEqualTo("ATIVO");
        assertThat(response.getBody().get("concluidoEm")).isNull();
        assertThat(response.getBody().get("arquivadoEm")).isNull();
        assertThat(dataNoBanco(id, "concluido_em")).isNull();
        assertThat(dataNoBanco(id, "arquivado_em")).isNull();
    }

    @Test
    void deveRetornar409AoReabrirObjetivoAtivo() {
        UUID id = criar("Meta", null);

        ResponseEntity<Map> response = post("/objetivos/" + id + "/reabrir", null);

        assertProblema(response, CONFLICT, "Objetivo já ativo");
        assertThat(statusNoBanco(id)).isEqualTo("ATIVO");
    }

    @Test
    void listagemPadraoTrazSoAtivosOrdenadosPorPrazoComSemPrazoNoFim() {
        UUID semPrazo = criar("Sem prazo", null);
        UUID longe = criar("Longe", hoje.plusDays(40));
        UUID perto = criar("Perto", hoje.plusDays(3));
        UUID concluido = criar("Concluído", null);
        post("/objetivos/" + concluido + "/concluir", null);
        UUID arquivado = criar("Arquivado", null);
        post("/objetivos/" + arquivado + "/arquivar", null);

        ResponseEntity<Map> response = get("/objetivos");

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(content(response)).extracting(o -> o.get("id"))
                .containsExactly(perto.toString(), longe.toString(), semPrazo.toString());
    }

    @Test
    void listagemDeConcluidosTrazMaisRecentePrimeiro() {
        UUID antigo = criar("Antigo", null);
        UUID recente = criar("Recente", null);
        post("/objetivos/" + antigo + "/concluir", null);
        post("/objetivos/" + recente + "/concluir", null);
        jdbcTemplate.update("update objetivo set concluido_em = ? where id = ?",
                Timestamp.from(Instant.now().minusSeconds(86_400)), antigo);

        ResponseEntity<Map> response = get("/objetivos?status=CONCLUIDO");

        assertThat(content(response)).extracting(o -> o.get("id"))
                .containsExactly(recente.toString(), antigo.toString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listagemDeArquivadosVaziaRetornaPaginaVazia() {
        criar("Ativo", null);

        ResponseEntity<Map> response = get("/objetivos?status=ARQUIVADO");

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(content(response)).isEmpty();
        Map<String, Object> page = (Map<String, Object>) response.getBody().get("page");
        assertThat(page.get("totalElements")).isEqualTo(0);
    }

    @Test
    void listagemComStatusInvalidoRetorna400() {
        ResponseEntity<Map> response = get("/objetivos?status=PAUSADO");

        assertProblema(response, BAD_REQUEST, "Parâmetro inválido");
    }

    private String cadastrarELogar(String email) {
        assertThat(restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Dono Objetivo", "email", email, "senha", SENHA)),
                Map.class).getStatusCode()).isEqualTo(CREATED);
        ResponseEntity<Map> login = restTemplate.postForEntity(
                "/auth/login", json(Map.of("email", email, "senha", SENHA)), Map.class);
        assertThat(login.getStatusCode()).isEqualTo(OK);
        return login.getBody().get("accessToken").toString();
    }

    private UUID criar(String titulo, LocalDate prazo) {
        Map<String, Object> body = new HashMap<>();
        body.put("titulo", titulo);
        if (prazo != null) {
            body.put("prazo", prazo.toString());
        }
        ResponseEntity<Map> response = post("/objetivos", body);
        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        return id(response);
    }

    private ResponseEntity<Map> post(String path, Object body) {
        return restTemplate.exchange(path, HttpMethod.POST, autenticadoJson(accessToken, body), Map.class);
    }

    private ResponseEntity<Map> patch(UUID id, Object body) {
        return restTemplate.exchange(
                "/objetivos/" + id, HttpMethod.PATCH, autenticadoJson(accessToken, body), Map.class);
    }

    private ResponseEntity<Map> get(String path) {
        return restTemplate.exchange(path, HttpMethod.GET, autenticadoJson(accessToken, null), Map.class);
    }

    private int totalObjetivos() {
        return jdbcTemplate.queryForObject(
                "select count(*) from objetivo where usuario_id = ?", Integer.class, usuarioId);
    }

    private String tituloNoBanco(UUID id) {
        return jdbcTemplate.queryForObject("select titulo from objetivo where id = ?", String.class, id);
    }

    private String statusNoBanco(UUID id) {
        return jdbcTemplate.queryForObject("select status from objetivo where id = ?", String.class, id);
    }

    private LocalDate prazoNoBanco(UUID id) {
        Date prazo = jdbcTemplate.queryForObject("select prazo from objetivo where id = ?", Date.class, id);
        return prazo == null ? null : prazo.toLocalDate();
    }

    private Timestamp dataNoBanco(UUID id, String coluna) {
        return jdbcTemplate.queryForObject(
                "select " + coluna + " from objetivo where id = ?", Timestamp.class, id);
    }

    private static UUID id(ResponseEntity<Map> response) {
        return UUID.fromString(response.getBody().get("id").toString());
    }

    private static void assertProblema(ResponseEntity<Map> response, HttpStatus status, String title) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("title")).isEqualTo(title);
    }

    @SuppressWarnings("unchecked")
    private static List<String> propriedadesComErro(ResponseEntity<Map> response) {
        List<Map<String, Object>> errors = (List<Map<String, Object>>) response.getBody().get("errors");
        return errors.stream().map(e -> e.get("property").toString()).toList();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> content(ResponseEntity<Map> response) {
        assertThat(response.getBody()).isNotNull();
        return (List<Map<String, Object>>) response.getBody().get("content");
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
