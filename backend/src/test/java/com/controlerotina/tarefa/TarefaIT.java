package com.controlerotina.tarefa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
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

import com.controlerotina.historico.PeriodoBRT;
import com.controlerotina.tarefa.job.TarefaAtrasoJob;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class TarefaIT {

    private static final String SENHA = "Senha@123";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    TarefaAtrasoJob tarefaAtrasoJob;

    private String accessToken;
    private UUID usuarioId;

    @BeforeEach
    void cadastrarELogar() {
        String email = "tarefa-" + UUID.randomUUID() + "@email.com";
        assertThat(restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Dono Tarefa", "email", email, "senha", SENHA)),
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
    void deveRetornar401AoCriarSemToken() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/tarefas",
                json(tarefaValida()),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
    }

    @Test
    void t1_deveCriarTarefaSemPrazoCom201() {
        Map<String, Object> body = tarefaValida();
        body.remove("prazo");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/tarefas",
                HttpMethod.POST,
                autenticadoJson(body),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("prazo")).isNull();
        assertThat(response.getBody().get("status")).isEqualTo("PENDENTE");
    }

    @Test
    void t3_jobNaoMarcaAtrasadaQuandoPrazoNulo() {
        Map<String, Object> body = tarefaValida();
        body.remove("prazo");
        ResponseEntity<Map> criada = restTemplate.exchange(
                "/tarefas",
                HttpMethod.POST,
                autenticadoJson(body),
                Map.class);
        UUID id = UUID.fromString(criada.getBody().get("id").toString());

        tarefaAtrasoJob.processarAtrasos();

        assertThat(statusDaTarefa(id)).isEqualTo("PENDENTE");
    }

    @Test
    void deveCriarTarefaComStatusPendenteERetornar201() {
        Instant prazo = Instant.now().plus(2, ChronoUnit.DAYS);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/tarefas",
                HttpMethod.POST,
                autenticadoJson(tarefaValida(prazo)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("id")).isNotNull();
        assertThat(response.getBody().get("titulo")).isEqualTo("Estudar Spring");
        assertThat(response.getBody().get("descricao")).isEqualTo("Revisar JPA e testes");
        assertThat(response.getBody().get("prioridade")).isEqualTo("ALTA");
        assertThat(response.getBody().get("status")).isEqualTo("PENDENTE");
        assertThat(response.getBody().get("duracaoMinutos")).isEqualTo(180);
        assertThat(response.getBody().get("deletada")).isEqualTo(false);
        assertThat(response.getBody().get("deletedAt")).isNull();
        assertThat(response.getBody().get("createdAt")).isNotNull();
        assertThat(response.getBody().get("updatedAt")).isNotNull();

        UUID id = UUID.fromString(response.getBody().get("id").toString());
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select usuario_id, status, titulo from tarefa where id = ?", id);
        assertThat(row.get("usuario_id")).isEqualTo(usuarioId);
        assertThat(row.get("status")).isEqualTo("PENDENTE");
        assertThat(row.get("titulo")).isEqualTo("Estudar Spring");
    }

    @Test
    void deveRejeitarPrazoNoPassadoCom400() {
        Instant prazoPassado = Instant.now().minus(1, ChronoUnit.HOURS);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/tarefas",
                HttpMethod.POST,
                autenticadoJson(tarefaValida(prazoPassado)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
    }

    @Test
    void t1_deveAceitarDuracao90Minutos() {
        Map<String, Object> body = tarefaValida();
        body.put("duracaoMinutos", 90);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/tarefas",
                HttpMethod.POST,
                autenticadoJson(body),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        assertThat(response.getBody().get("duracaoMinutos")).isEqualTo(90);
    }

    @Test
    void t1_deveRejeitarDuracao10MinutosCom400() {
        Map<String, Object> body = tarefaValida();
        body.put("duracaoMinutos", 10);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/tarefas",
                HttpMethod.POST,
                autenticadoJson(body),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
    }

    @Test
    void deveRejeitarDuracaoInvalidaCom400() {
        Map<String, Object> body = tarefaValida();
        body.put("duracaoMinutos", 0);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/tarefas",
                HttpMethod.POST,
                autenticadoJson(body),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
    }

    @Test
    void deveRejeitarTituloLongoCom400() {
        Map<String, Object> body = tarefaValida();
        body.put("titulo", "a".repeat(121));

        ResponseEntity<Map> response = restTemplate.exchange(
                "/tarefas",
                HttpMethod.POST,
                autenticadoJson(body),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
    }

    @Test
    void t1_deveCriarTarefaSemDescricaoCom201() {
        Map<String, Object> semCampo = tarefaValida();
        semCampo.remove("descricao");

        ResponseEntity<Map> omitida = restTemplate.exchange(
                "/tarefas",
                HttpMethod.POST,
                autenticadoJson(semCampo),
                Map.class);

        assertThat(omitida.getStatusCode()).isEqualTo(CREATED);
        assertThat(omitida.getBody()).isNotNull();
        assertThat(omitida.getBody().get("descricao")).isEqualTo("");

        Map<String, Object> vazia = tarefaValida();
        vazia.put("titulo", "Sem descricao vazia");
        vazia.put("descricao", "");

        ResponseEntity<Map> blank = restTemplate.exchange(
                "/tarefas",
                HttpMethod.POST,
                autenticadoJson(vazia),
                Map.class);

        assertThat(blank.getStatusCode()).isEqualTo(CREATED);
        assertThat(blank.getBody()).isNotNull();
        assertThat(blank.getBody().get("descricao")).isEqualTo("");
    }

    @Test
    void t2_deveRejeitarPostSemTituloCom400() {
        Map<String, Object> semTitulo = tarefaValida();
        semTitulo.remove("titulo");

        ResponseEntity<Map> semTituloResp = restTemplate.exchange(
                "/tarefas",
                HttpMethod.POST,
                autenticadoJson(semTitulo),
                Map.class);
        assertThat(semTituloResp.getStatusCode()).isEqualTo(BAD_REQUEST);
    }

    @Test
    void deveRejeitarPrioridadeInvalidaCom400() {
        Map<String, Object> body = tarefaValida();
        body.put("prioridade", "URGENTE");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/tarefas",
                HttpMethod.POST,
                autenticadoJson(body),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
    }

    @Test
    void deveRetornar401AoListarSemToken() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/tarefas", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
    }

    @Test
    void deveListarSomenteTarefasDoUsuarioAutenticado() {
        criarTarefaAutenticada("Minha tarefa", Instant.now().plus(1, ChronoUnit.DAYS), "MEDIA", 120);

        String outroEmail = "outro-" + UUID.randomUUID() + "@email.com";
        restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Outro", "email", outroEmail, "senha", SENHA)),
                Map.class);
        String outroToken = restTemplate.postForEntity(
                "/auth/login",
                json(Map.of("email", outroEmail, "senha", SENHA)),
                Map.class).getBody().get("accessToken").toString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(outroToken);
        restTemplate.exchange(
                "/tarefas",
                HttpMethod.POST,
                new HttpEntity<>(tarefaValida("Tarefa alheia", Instant.now().plus(1, ChronoUnit.DAYS), "BAIXA", 60), headers),
                Map.class);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/tarefas",
                HttpMethod.GET,
                autenticado(),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        List<Map<String, Object>> content = content(response);
        assertThat(content).hasSize(1);
        assertThat(content.get(0).get("titulo")).isEqualTo("Minha tarefa");
    }

    @Test
    void deveOrdenarPorPrazoAscPorPadrao() {
        Instant agora = Instant.now();
        criarTarefaAutenticada("Depois", agora.plus(3, ChronoUnit.DAYS), "BAIXA", 60);
        criarTarefaAutenticada("Antes", agora.plus(1, ChronoUnit.DAYS), "ALTA", 60);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/tarefas",
                HttpMethod.GET,
                autenticado(),
                Map.class);

        List<Map<String, Object>> content = content(response);
        assertThat(content).extracting(m -> m.get("titulo")).containsExactly("Antes", "Depois");
    }

    @Test
    void deveOrdenarPorDuracaoDescQuandoSolicitado() {
        Instant prazo = Instant.now().plus(2, ChronoUnit.DAYS);
        criarTarefaAutenticada("Curta", prazo, "MEDIA", 60);
        criarTarefaAutenticada("Longa", prazo.plus(1, ChronoUnit.HOURS), "MEDIA", 300);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/tarefas?sort=duracao,desc",
                HttpMethod.GET,
                autenticado(),
                Map.class);

        List<Map<String, Object>> content = content(response);
        assertThat(content).extracting(m -> m.get("titulo")).containsExactly("Longa", "Curta");
    }

    @Test
    void deveFiltrarPorStatusEPrioridadeMultiValor() {
        Instant prazo = Instant.now().plus(2, ChronoUnit.DAYS);
        UUID idAlta = criarTarefaAutenticada("Alta", prazo, "ALTA", 60);
        criarTarefaAutenticada("Baixa", prazo.plus(1, ChronoUnit.HOURS), "BAIXA", 60);
        jdbcTemplate.update("update tarefa set status = 'EM_PROGRESSO' where id = ?", idAlta);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/tarefas?status=EM_PROGRESSO&status=FINALIZADA&prioridade=ALTA&prioridade=MEDIA",
                HttpMethod.GET,
                autenticado(),
                Map.class);

        List<Map<String, Object>> content = content(response);
        assertThat(content).hasSize(1);
        assertThat(content.get(0).get("titulo")).isEqualTo("Alta");
    }

    @Test
    void deveFiltrarPorIntervaloDePrazo() {
        Instant base = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        criarTarefaAutenticada("Fora", base.plus(10, ChronoUnit.DAYS), "MEDIA", 60);
        criarTarefaAutenticada("Dentro", base.plus(2, ChronoUnit.DAYS), "MEDIA", 60);

        String prazoDe = base.plus(1, ChronoUnit.DAYS).toString();
        String prazoAte = base.plus(3, ChronoUnit.DAYS).toString();

        ResponseEntity<Map> response = restTemplate.exchange(
                "/tarefas?prazoDe=" + prazoDe + "&prazoAte=" + prazoAte,
                HttpMethod.GET,
                autenticado(),
                Map.class);

        List<Map<String, Object>> content = content(response);
        assertThat(content).extracting(m -> m.get("titulo")).containsExactly("Dentro");
    }

    @Test
    void devePaginarComSizeDefaultEMaximo() {
        Instant prazo = Instant.now().plus(1, ChronoUnit.DAYS);
        for (int i = 0; i < 21; i++) {
            criarTarefaAutenticada("T" + i, prazo.plus(i, ChronoUnit.HOURS), "MEDIA", 60);
        }

        ResponseEntity<Map> page0 = restTemplate.exchange(
                "/tarefas",
                HttpMethod.GET,
                autenticado(),
                Map.class);
        assertThat(page0.getStatusCode()).isEqualTo(OK);
        assertThat(content(page0)).hasSize(20);
        assertThat(((Number) page0.getBody().get("totalElements")).intValue()).isEqualTo(21);

        ResponseEntity<Map> page1 = restTemplate.exchange(
                "/tarefas?page=1&size=20",
                HttpMethod.GET,
                autenticado(),
                Map.class);
        assertThat(content(page1)).hasSize(1);
    }

    @Test
    void deveExcluirSoftDeletadasDaListagemPadraoEPermitirFiltros() {
        Instant prazo = Instant.now().plus(2, ChronoUnit.DAYS);
        UUID ativaId = criarTarefaAutenticada("Ativa", prazo, "MEDIA", 60);
        UUID deletadaId = criarTarefaAutenticada("Deletada", prazo.plus(1, ChronoUnit.HOURS), "MEDIA", 60);
        jdbcTemplate.update(
                "update tarefa set deleted_at = ? where id = ?",
                Timestamp.from(Instant.now()),
                deletadaId);

        ResponseEntity<Map> padrao = restTemplate.exchange(
                "/tarefas",
                HttpMethod.GET,
                autenticado(),
                Map.class);
        assertThat(content(padrao)).extracting(m -> m.get("titulo")).containsExactly("Ativa");

        ResponseEntity<Map> incluir = restTemplate.exchange(
                "/tarefas?incluirDeletadas=true",
                HttpMethod.GET,
                autenticado(),
                Map.class);
        assertThat(content(incluir)).extracting(m -> m.get("titulo"))
                .containsExactlyInAnyOrder("Ativa", "Deletada");

        ResponseEntity<Map> somente = restTemplate.exchange(
                "/tarefas?somenteDeletadas=true",
                HttpMethod.GET,
                autenticado(),
                Map.class);
        List<Map<String, Object>> somenteContent = content(somente);
        assertThat(somenteContent).hasSize(1);
        assertThat(somenteContent.get(0).get("titulo")).isEqualTo("Deletada");
        assertThat(somenteContent.get(0).get("deletada")).isEqualTo(true);
        assertThat(somenteContent.get(0).get("id").toString()).isEqualTo(deletadaId.toString());
        assertThat(ativaId).isNotNull();
    }

    @Test
    void deveBuscarTarefaPorIdInclusiveSoftDeletada() {
        UUID id = criarTarefaAutenticada("Detalhe", Instant.now().plus(2, ChronoUnit.DAYS), "MEDIA", 120);

        ResponseEntity<Map> ativa = restTemplate.exchange(
                "/tarefas/" + id,
                HttpMethod.GET,
                autenticado(),
                Map.class);
        assertThat(ativa.getStatusCode()).isEqualTo(OK);
        assertThat(ativa.getBody().get("titulo")).isEqualTo("Detalhe");
        assertThat(ativa.getBody().get("deletada")).isEqualTo(false);

        jdbcTemplate.update(
                "update tarefa set deleted_at = ? where id = ?",
                Timestamp.from(Instant.now()),
                id);

        ResponseEntity<Map> deletada = restTemplate.exchange(
                "/tarefas/" + id,
                HttpMethod.GET,
                autenticado(),
                Map.class);
        assertThat(deletada.getStatusCode()).isEqualTo(OK);
        assertThat(deletada.getBody().get("deletada")).isEqualTo(true);
        assertThat(deletada.getBody().get("deletedAt")).isNotNull();
    }

    @Test
    void deveRetornar404AoBuscarTarefaDeOutroUsuarioOuInexistente() {
        UUID id = criarTarefaAutenticada("Alheia", Instant.now().plus(1, ChronoUnit.DAYS), "BAIXA", 60);
        String outroToken = tokenDeNovoUsuario();

        ResponseEntity<Map> alheia = restTemplate.exchange(
                "/tarefas/" + id,
                HttpMethod.GET,
                autenticadoCom(outroToken),
                Map.class);
        assertThat(alheia.getStatusCode()).isEqualTo(NOT_FOUND);

        ResponseEntity<Map> inexistente = restTemplate.exchange(
                "/tarefas/" + UUID.randomUUID(),
                HttpMethod.GET,
                autenticado(),
                Map.class);
        assertThat(inexistente.getStatusCode()).isEqualTo(NOT_FOUND);
    }

    @Test
    void deveAtualizarParcialmenteComPatch() {
        UUID id = criarTarefaAutenticada("Original", Instant.now().plus(3, ChronoUnit.DAYS), "BAIXA", 60);
        Instant novoPrazo = Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/tarefas/" + id,
                HttpMethod.PATCH,
                autenticadoJson(Map.of(
                        "titulo", "Atualizada",
                        "status", "EM_PROGRESSO",
                        "prazo", novoPrazo.toString(),
                        "duracaoMinutos", 240
                )),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody().get("titulo")).isEqualTo("Atualizada");
        assertThat(response.getBody().get("status")).isEqualTo("EM_PROGRESSO");
        assertThat(response.getBody().get("duracaoMinutos")).isEqualTo(240);
        assertThat(response.getBody().get("prioridade")).isEqualTo("BAIXA");
        assertThat(response.getBody().get("descricao")).isEqualTo("Revisar JPA e testes");
        assertThat(Instant.parse(response.getBody().get("prazo").toString())).isEqualTo(novoPrazo);
    }

    @Test
    void deveRejeitarPatchComPrazoNoPassado() {
        UUID id = criarTarefaAutenticada("Prazo", Instant.now().plus(2, ChronoUnit.DAYS), "MEDIA", 60);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/tarefas/" + id,
                HttpMethod.PATCH,
                autenticadoJson(Map.of("prazo", Instant.now().minus(1, ChronoUnit.HOURS).toString())),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
    }

    @Test
    void deveRejeitarStatusAtrasadaComPrazoFuturo() {
        UUID id = criarTarefaAutenticada("Futura", Instant.now().plus(2, ChronoUnit.DAYS), "MEDIA", 60);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/tarefas/" + id,
                HttpMethod.PATCH,
                autenticadoJson(Map.of("status", "ATRASADA")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
    }

    @Test
    void deveVoltarParaPendenteQuandoAtrasadaGanhaPrazoFuturo() {
        UUID id = criarTarefaAutenticada("Atrasada", Instant.now().plus(2, ChronoUnit.DAYS), "MEDIA", 60);
        jdbcTemplate.update(
                "update tarefa set status = 'ATRASADA', prazo = ? where id = ?",
                Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS)),
                id);

        Instant novoPrazo = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        ResponseEntity<Map> response = restTemplate.exchange(
                "/tarefas/" + id,
                HttpMethod.PATCH,
                autenticadoJson(Map.of("prazo", novoPrazo.toString())),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody().get("status")).isEqualTo("PENDENTE");
        assertThat(Instant.parse(response.getBody().get("prazo").toString())).isEqualTo(novoPrazo);
    }

    @Test
    void deveFazerSoftDeleteENaoRestaurarViaPatch() {
        UUID id = criarTarefaAutenticada("Apagar", Instant.now().plus(2, ChronoUnit.DAYS), "MEDIA", 60);

        ResponseEntity<Void> deleted = restTemplate.exchange(
                "/tarefas/" + id,
                HttpMethod.DELETE,
                autenticado(),
                Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(NO_CONTENT);

        ResponseEntity<Map> detalhe = restTemplate.exchange(
                "/tarefas/" + id,
                HttpMethod.GET,
                autenticado(),
                Map.class);
        assertThat(detalhe.getBody().get("deletada")).isEqualTo(true);

        ResponseEntity<Map> patch = restTemplate.exchange(
                "/tarefas/" + id,
                HttpMethod.PATCH,
                autenticadoJson(Map.of("titulo", "Ainda deletada")),
                Map.class);
        assertThat(patch.getStatusCode()).isEqualTo(OK);
        assertThat(patch.getBody().get("titulo")).isEqualTo("Ainda deletada");
        assertThat(patch.getBody().get("deletada")).isEqualTo(true);
        assertThat(patch.getBody().get("deletedAt")).isNotNull();
    }

    @Test
    void deveRetornar404AoDeletarJaDeletadaOuDeOutroUsuario() {
        UUID id = criarTarefaAutenticada("Dupla", Instant.now().plus(2, ChronoUnit.DAYS), "MEDIA", 60);
        assertThat(restTemplate.exchange(
                "/tarefas/" + id,
                HttpMethod.DELETE,
                autenticado(),
                Void.class).getStatusCode()).isEqualTo(NO_CONTENT);

        assertThat(restTemplate.exchange(
                "/tarefas/" + id,
                HttpMethod.DELETE,
                autenticado(),
                Void.class).getStatusCode()).isEqualTo(NOT_FOUND);

        UUID outra = criarTarefaAutenticada("Outra", Instant.now().plus(2, ChronoUnit.DAYS), "MEDIA", 60);
        assertThat(restTemplate.exchange(
                "/tarefas/" + outra,
                HttpMethod.DELETE,
                autenticadoCom(tokenDeNovoUsuario()),
                Void.class).getStatusCode()).isEqualTo(NOT_FOUND);
    }

    @Test
    void deveRestaurarTarefaSoftDeletada() {
        UUID id = criarTarefaAutenticada("Restaurar", Instant.now().plus(2, ChronoUnit.DAYS), "ALTA", 120);
        assertThat(restTemplate.exchange(
                "/tarefas/" + id,
                HttpMethod.DELETE,
                autenticado(),
                Void.class).getStatusCode()).isEqualTo(NO_CONTENT);

        ResponseEntity<Map> restaurada = restTemplate.exchange(
                "/tarefas/" + id + "/restore",
                HttpMethod.POST,
                autenticado(),
                Map.class);

        assertThat(restaurada.getStatusCode()).isEqualTo(OK);
        assertThat(restaurada.getBody().get("deletada")).isEqualTo(false);
        assertThat(restaurada.getBody().get("deletedAt")).isNull();

        assertThat(restTemplate.exchange(
                "/tarefas/" + id + "/restore",
                HttpMethod.POST,
                autenticado(),
                Map.class).getStatusCode()).isEqualTo(NOT_FOUND);
    }

    @Test
    void deveMarcarPendenteComoAtrasadaQuandoPrazoAntesDoInicioDoDia() {
        UUID id = criarTarefaAutenticada("Atrasar", Instant.now().plus(2, ChronoUnit.DAYS), "MEDIA", 60);
        jdbcTemplate.update(
                "update tarefa set prazo = ? where id = ?",
                Timestamp.from(prazoAntesDoInicioDoDia()),
                id);

        tarefaAtrasoJob.processarAtrasos();

        assertThat(statusDaTarefa(id)).isEqualTo("ATRASADA");
    }

    @Test
    void naoDeveReverterAtrasadaParaPendenteQuandoPrazoNoFuturo() {
        UUID id = criarTarefaAutenticada("Recuperar", Instant.now().plus(2, ChronoUnit.DAYS), "MEDIA", 60);
        jdbcTemplate.update(
                "update tarefa set status = 'ATRASADA' where id = ?",
                id);

        tarefaAtrasoJob.processarAtrasos();

        assertThat(statusDaTarefa(id)).isEqualTo("ATRASADA");
    }

    @Test
    void naoDeveAlterarFinalizadaCanceladaNemSoftDeletadaNoJobDeAtraso() {
        UUID emProgresso = criarTarefaAutenticada("Prog", Instant.now().plus(2, ChronoUnit.DAYS), "MEDIA", 60);
        UUID finalizada = criarTarefaAutenticada("Fim", Instant.now().plus(2, ChronoUnit.DAYS), "MEDIA", 60);
        UUID cancelada = criarTarefaAutenticada("Canc", Instant.now().plus(2, ChronoUnit.DAYS), "MEDIA", 60);
        UUID deletada = criarTarefaAutenticada("Del", Instant.now().plus(2, ChronoUnit.DAYS), "MEDIA", 60);
        UUID pendenteFutura = criarTarefaAutenticada("Futura", Instant.now().plus(2, ChronoUnit.DAYS), "MEDIA", 60);
        UUID pendenteHoje = criarTarefaAutenticada("Hoje", Instant.now().plus(2, ChronoUnit.DAYS), "MEDIA", 60);

        Instant prazoOntem = prazoAntesDoInicioDoDia();
        Instant prazoHojeAposMeiaNoite = PeriodoBRT.intervaloDia(LocalDate.now(PeriodoBRT.ZONE))
                .inicioInclusivo()
                .plus(1, ChronoUnit.HOURS);

        jdbcTemplate.update(
                "update tarefa set status = 'EM_PROGRESSO', prazo = ? where id = ?",
                Timestamp.from(prazoOntem),
                emProgresso);
        jdbcTemplate.update(
                "update tarefa set status = 'FINALIZADA', prazo = ? where id = ?",
                Timestamp.from(prazoOntem),
                finalizada);
        jdbcTemplate.update(
                "update tarefa set status = 'CANCELADA', prazo = ? where id = ?",
                Timestamp.from(prazoOntem),
                cancelada);
        jdbcTemplate.update(
                "update tarefa set prazo = ?, deleted_at = ? where id = ?",
                Timestamp.from(prazoOntem),
                Timestamp.from(Instant.now()),
                deletada);
        jdbcTemplate.update(
                "update tarefa set prazo = ? where id = ?",
                Timestamp.from(prazoHojeAposMeiaNoite),
                pendenteHoje);

        tarefaAtrasoJob.processarAtrasos();

        assertThat(statusDaTarefa(emProgresso)).isEqualTo("ATRASADA");
        assertThat(statusDaTarefa(finalizada)).isEqualTo("FINALIZADA");
        assertThat(statusDaTarefa(cancelada)).isEqualTo("CANCELADA");
        assertThat(statusDaTarefa(deletada)).isEqualTo("PENDENTE");
        assertThat(statusDaTarefa(pendenteFutura)).isEqualTo("PENDENTE");
        assertThat(statusDaTarefa(pendenteHoje)).isEqualTo("PENDENTE");
    }

    private static Instant prazoAntesDoInicioDoDia() {
        return PeriodoBRT.intervaloDia(LocalDate.now(PeriodoBRT.ZONE)).inicioInclusivo()
                .minus(1, ChronoUnit.HOURS);
    }

    private String statusDaTarefa(UUID id) {
        return jdbcTemplate.queryForObject("select status from tarefa where id = ?", String.class, id);
    }

    private String tokenDeNovoUsuario() {
        String email = "outro-" + UUID.randomUUID() + "@email.com";
        restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Outro", "email", email, "senha", SENHA)),
                Map.class);
        return restTemplate.postForEntity(
                "/auth/login",
                json(Map.of("email", email, "senha", SENHA)),
                Map.class).getBody().get("accessToken").toString();
    }

    private HttpEntity<Void> autenticadoCom(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    private UUID criarTarefaAutenticada(String titulo, Instant prazo, String prioridade, int duracaoMinutos) {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/tarefas",
                HttpMethod.POST,
                autenticadoJson(tarefaValida(titulo, prazo, prioridade, duracaoMinutos)),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        return UUID.fromString(response.getBody().get("id").toString());
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> content(ResponseEntity<Map> response) {
        assertThat(response.getBody()).isNotNull();
        return (List<Map<String, Object>>) response.getBody().get("content");
    }

    private Map<String, Object> tarefaValida() {
        return tarefaValida(Instant.now().plus(2, ChronoUnit.DAYS));
    }

    private Map<String, Object> tarefaValida(Instant prazo) {
        return tarefaValida("Estudar Spring", prazo, "ALTA", 180);
    }

    private Map<String, Object> tarefaValida(String titulo, Instant prazo, String prioridade, int duracaoMinutos) {
        Map<String, Object> body = new HashMap<>();
        body.put("titulo", titulo);
        body.put("descricao", "Revisar JPA e testes");
        body.put("prioridade", prioridade);
        body.put("prazo", prazo.toString());
        body.put("duracaoMinutos", duracaoMinutos);
        return body;
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
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
