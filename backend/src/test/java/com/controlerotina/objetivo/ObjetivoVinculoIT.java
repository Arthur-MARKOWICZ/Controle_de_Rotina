package com.controlerotina.objetivo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.controlerotina.capacidade.entity.DiaSemana;
import com.controlerotina.historico.PeriodoBRT;

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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class ObjetivoVinculoIT {

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
        String email = "objetivo-vinculo-" + UUID.randomUUID() + "@email.com";
        accessToken = cadastrarELogar(email);
    }

    @Test
    void deveRetornar401AoListarItensSemToken() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/objetivos/" + UUID.randomUUID() + "/itens", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
    }

    @Test
    void deveListarTarefaHabitoEProjetoComoDireto() {
        UUID objetivoId = criarObjetivo("Meta");
        UUID tarefaId = criarTarefa("Tarefa raiz", null, List.of(objetivoId));
        UUID habitoId = criarHabito("Hábito", List.of(objetivoId));
        UUID projetoId = criarProjeto("Projeto", List.of(objetivoId));

        ResponseEntity<Map> response = get("/objetivos/" + objetivoId + "/itens");

        assertThat(response.getStatusCode()).isEqualTo(OK);
        List<Map<String, Object>> tarefas = grupo(response, "tarefas");
        List<Map<String, Object>> habitos = grupo(response, "habitos");
        List<Map<String, Object>> projetos = grupo(response, "projetos");
        assertThat(tarefas).hasSize(1);
        assertThat(tarefas.get(0).get("id")).isEqualTo(tarefaId.toString());
        assertThat(tarefas.get(0).get("titulo")).isEqualTo("Tarefa raiz");
        assertThat(tarefas.get(0).get("origem")).isEqualTo("DIRETO");
        assertThat(tarefas.get(0).get("projetoId")).isNull();
        assertThat(habitos).hasSize(1);
        assertThat(habitos.get(0).get("id")).isEqualTo(habitoId.toString());
        assertThat(habitos.get(0).get("titulo")).isEqualTo("Hábito");
        assertThat(habitos.get(0).get("origem")).isEqualTo("DIRETO");
        assertThat(projetos).hasSize(1);
        assertThat(projetos.get(0).get("id")).isEqualTo(projetoId.toString());
        assertThat(projetos.get(0).get("nome")).isEqualTo("Projeto");
        assertThat(projetos.get(0).get("origem")).isEqualTo("DIRETO");
    }

    @Test
    void deveRetornarListasVaziasQuandoObjetivoNaoTemItens() {
        UUID objetivoId = criarObjetivo("Vazio");

        ResponseEntity<Map> response = get("/objetivos/" + objetivoId + "/itens");

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(grupo(response, "tarefas")).isEmpty();
        assertThat(grupo(response, "habitos")).isEmpty();
        assertThat(grupo(response, "projetos")).isEmpty();
    }

    @Test
    void deveListarTarefaDoProjetoComoViaProjeto() {
        UUID objetivoId = criarObjetivo("Herdada");
        UUID projetoId = criarProjeto("P", List.of(objetivoId));
        UUID tarefaId = criarTarefa("No projeto", projetoId, List.of());

        ResponseEntity<Map> response = get("/objetivos/" + objetivoId + "/itens");

        assertThat(response.getStatusCode()).isEqualTo(OK);
        List<Map<String, Object>> tarefas = grupo(response, "tarefas");
        assertThat(tarefas).hasSize(1);
        assertThat(tarefas.get(0).get("id")).isEqualTo(tarefaId.toString());
        assertThat(tarefas.get(0).get("origem")).isEqualTo("VIA_PROJETO");
        assertThat(tarefas.get(0).get("projetoId")).isEqualTo(projetoId.toString());
        assertThat(tarefas.get(0).get("projetoNome")).isEqualTo("P");
        assertThat(grupo(response, "projetos")).extracting(p -> p.get("id"))
                .containsExactly(projetoId.toString());
        assertThat(grupo(response, "habitos")).isEmpty();
    }

    @Test
    void deveListarTarefaUmaVezComoDiretoQuandoTambemHerdada() {
        UUID objetivoId = criarObjetivo("Dedupe");
        UUID projetoId = criarProjeto("P", List.of(objetivoId));
        UUID tarefaId = criarTarefa("Nos dois", projetoId, List.of(objetivoId));

        ResponseEntity<Map> response = get("/objetivos/" + objetivoId + "/itens");

        assertThat(response.getStatusCode()).isEqualTo(OK);
        List<Map<String, Object>> tarefas = grupo(response, "tarefas");
        assertThat(tarefas).hasSize(1);
        assertThat(tarefas.get(0).get("id")).isEqualTo(tarefaId.toString());
        assertThat(tarefas.get(0).get("origem")).isEqualTo("DIRETO");
        assertThat(tarefas.get(0).get("projetoId")).isEqualTo(projetoId.toString());
    }

    @Test
    void softDeleteSomeDaListaERestaurarVoltaDiretoMantendoJoin() {
        UUID objetivoId = criarObjetivo("Soft");
        UUID tarefaId = criarTarefa("Apagar", null, List.of(objetivoId));

        assertThat(restTemplate.exchange(
                "/tarefas/" + tarefaId, HttpMethod.DELETE, autenticadoJson(accessToken, null), Void.class)
                .getStatusCode()).isEqualTo(NO_CONTENT);

        ResponseEntity<Map> aposDelete = get("/objetivos/" + objetivoId + "/itens");
        assertThat(aposDelete.getStatusCode()).isEqualTo(OK);
        assertThat(grupo(aposDelete, "tarefas")).isEmpty();
        assertThat(totalVinculosTarefa(objetivoId, tarefaId)).isEqualTo(1);

        assertThat(restTemplate.exchange(
                "/tarefas/" + tarefaId + "/restore",
                HttpMethod.POST,
                autenticadoJson(accessToken, null),
                Map.class).getStatusCode()).isEqualTo(OK);

        ResponseEntity<Map> aposRestore = get("/objetivos/" + objetivoId + "/itens");
        assertThat(aposRestore.getStatusCode()).isEqualTo(OK);
        List<Map<String, Object>> tarefas = grupo(aposRestore, "tarefas");
        assertThat(tarefas).hasSize(1);
        assertThat(tarefas.get(0).get("id")).isEqualTo(tarefaId.toString());
        assertThat(tarefas.get(0).get("origem")).isEqualTo("DIRETO");
        assertThat(totalVinculosTarefa(objetivoId, tarefaId)).isEqualTo(1);
    }

    @Test
    void arquivarObjetivoMantemTarefaDireta() {
        UUID objetivoId = criarObjetivo("Arquivar");
        UUID tarefaId = criarTarefa("Continua", null, List.of(objetivoId));

        ResponseEntity<Map> arquivar = post("/objetivos/" + objetivoId + "/arquivar", null);
        assertThat(arquivar.getStatusCode()).isEqualTo(OK);

        ResponseEntity<Map> response = get("/objetivos/" + objetivoId + "/itens");

        assertThat(response.getStatusCode()).isEqualTo(OK);
        List<Map<String, Object>> tarefas = grupo(response, "tarefas");
        assertThat(tarefas).hasSize(1);
        assertThat(tarefas.get(0).get("id")).isEqualTo(tarefaId.toString());
        assertThat(tarefas.get(0).get("origem")).isEqualTo("DIRETO");
        assertThat(totalVinculosTarefa(objetivoId, tarefaId)).isEqualTo(1);
    }

    @Test
    void deveRetornar404AoListarItensDeObjetivoDeOutroUsuario() {
        String tokenOutro = cadastrarELogar("outro-vinculo-" + UUID.randomUUID() + "@email.com");
        UUID idOutro = id(restTemplate.exchange(
                "/objetivos",
                HttpMethod.POST,
                autenticadoJson(tokenOutro, Map.of("titulo", "Do outro")),
                Map.class));

        ResponseEntity<Map> response = get("/objetivos/" + idOutro + "/itens");

        assertProblema(response, NOT_FOUND, "Objetivo não encontrado");
        assertThat(totalVinculosTarefaDoObjetivo(idOutro)).isZero();
    }

    @Test
    void criarSubtarefaComObjetivoIdsRetorna400SemPersistirVinculo() {
        UUID objetivoId = criarObjetivo("Sub");
        UUID paiId = criarTarefa("Pai", null, List.of());
        int vinculosAntes = totalVinculosTarefaDoObjetivo(objetivoId);

        Map<String, Object> body = new HashMap<>();
        body.put("titulo", "Filha");
        body.put("parentId", paiId.toString());
        body.put("objetivoIds", List.of(objetivoId.toString()));

        ResponseEntity<Map> response = post("/tarefas", body);

        assertProblema(response, BAD_REQUEST, "Subtarefa não pode ser vinculada a objetivo");
        assertThat(totalVinculosTarefaDoObjetivo(objetivoId)).isEqualTo(vinculosAntes);
    }

    @Test
    void patchHabitoReenviandoMesmosIdsPreservaJanelaDoProgresso() {
        UUID objetivoId = criarObjetivo("Janela");
        UUID habitoId = criarHabitoDiario("Hábito", List.of(objetivoId));
        LocalDate ontem = LocalDate.now(PeriodoBRT.ZONE).minusDays(1);
        Timestamp createdAtOntem = Timestamp.from(
                LocalDateTime.of(ontem.getYear(), ontem.getMonth(), ontem.getDayOfMonth(), 12, 0)
                        .atZone(PeriodoBRT.ZONE)
                        .toInstant());
        jdbcTemplate.update(
                "update objetivo_habito set created_at = ? where habito_id = ? and objetivo_id = ?",
                createdAtOntem, habitoId, objetivoId);

        ResponseEntity<Map> detalheAntes = get("/objetivos/" + objetivoId);
        assertThat(detalheAntes.getStatusCode()).isEqualTo(OK);
        assertProgresso(detalheAntes.getBody(), 0, 0, 60);

        ResponseEntity<Map> patch = restTemplate.exchange(
                "/habitos/" + habitoId,
                HttpMethod.PATCH,
                autenticadoJson(accessToken, Map.of(
                        "titulo", "Hábito atualizado",
                        "objetivoIds", List.of(objetivoId.toString()))),
                Map.class);
        assertThat(patch.getStatusCode()).isEqualTo(OK);

        Timestamp createdAtDepois = jdbcTemplate.queryForObject(
                "select created_at from objetivo_habito where habito_id = ? and objetivo_id = ?",
                Timestamp.class, habitoId, objetivoId);
        assertThat(createdAtDepois).isEqualTo(createdAtOntem);

        ResponseEntity<Map> detalheDepois = get("/objetivos/" + objetivoId);
        assertThat(detalheDepois.getStatusCode()).isEqualTo(OK);
        assertProgresso(detalheDepois.getBody(), 0, 0, 60);
    }

    @Test
    void patchTarefaComObjetivoConcluidoRetorna409SemAlterarBanco() {
        UUID objetivoId = criarObjetivo("Concluído");
        post("/objetivos/" + objetivoId + "/concluir", null);
        UUID tarefaId = criarTarefa("Sem vínculo", null, List.of());
        int vinculosAntes = totalVinculosTarefaDoObjetivo(objetivoId);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/tarefas/" + tarefaId,
                HttpMethod.PATCH,
                autenticadoJson(accessToken, Map.of("objetivoIds", List.of(objetivoId.toString()))),
                Map.class);

        assertProblema(response, CONFLICT, "Objetivo não está ativo");
        assertThat(totalVinculosTarefaDoObjetivo(objetivoId)).isEqualTo(vinculosAntes);
        assertThat(totalVinculosTarefa(objetivoId, tarefaId)).isZero();
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

    private UUID criarObjetivo(String titulo) {
        ResponseEntity<Map> response = post("/objetivos", Map.of("titulo", titulo));
        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        return id(response);
    }

    private UUID criarTarefa(String titulo, UUID projetoId, List<UUID> objetivoIds) {
        Map<String, Object> body = new HashMap<>();
        body.put("titulo", titulo);
        body.put("duracaoMinutos", 30);
        if (projetoId != null) {
            body.put("projetoId", projetoId.toString());
        }
        if (objetivoIds != null && !objetivoIds.isEmpty()) {
            body.put("objetivoIds", objetivoIds.stream().map(UUID::toString).toList());
        }
        ResponseEntity<Map> response = post("/tarefas", body);
        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        return id(response);
    }

    private UUID criarHabito(String titulo, List<UUID> objetivoIds) {
        return criarHabito(titulo, List.of("SEGUNDA"), objetivoIds);
    }

    private UUID criarHabitoDiario(String titulo, List<UUID> objetivoIds) {
        return criarHabito(titulo, List.of(
                DiaSemana.SEGUNDA.name(),
                DiaSemana.TERCA.name(),
                DiaSemana.QUARTA.name(),
                DiaSemana.QUINTA.name(),
                DiaSemana.SEXTA.name(),
                DiaSemana.SABADO.name(),
                DiaSemana.DOMINGO.name()
        ), objetivoIds);
    }

    private UUID criarHabito(String titulo, List<String> diasSemana, List<UUID> objetivoIds) {
        Map<String, Object> body = new HashMap<>();
        body.put("titulo", titulo);
        body.put("duracaoMinutos", 30);
        body.put("diasSemana", diasSemana);
        if (objetivoIds != null && !objetivoIds.isEmpty()) {
            body.put("objetivoIds", objetivoIds.stream().map(UUID::toString).toList());
        }
        ResponseEntity<Map> response = post("/habitos", body);
        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        return id(response);
    }

    private UUID criarProjeto(String nome, List<UUID> objetivoIds) {
        Map<String, Object> body = new HashMap<>();
        body.put("nome", nome);
        if (objetivoIds != null && !objetivoIds.isEmpty()) {
            body.put("objetivoIds", objetivoIds.stream().map(UUID::toString).toList());
        }
        ResponseEntity<Map> response = post("/projetos", body);
        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        return id(response);
    }

    private int totalVinculosTarefa(UUID objetivoId, UUID tarefaId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from objetivo_tarefa where objetivo_id = ? and tarefa_id = ?",
                Integer.class,
                objetivoId,
                tarefaId);
    }

    private int totalVinculosTarefaDoObjetivo(UUID objetivoId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from objetivo_tarefa where objetivo_id = ?",
                Integer.class,
                objetivoId);
    }

    private ResponseEntity<Map> post(String path, Object body) {
        return restTemplate.exchange(path, HttpMethod.POST, autenticadoJson(accessToken, body), Map.class);
    }

    private ResponseEntity<Map> get(String path) {
        return restTemplate.exchange(path, HttpMethod.GET, autenticadoJson(accessToken, null), Map.class);
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
    private static void assertProgresso(
            Map<?, ?> body,
            Integer percentual,
            Integer minutosConcluidos,
            Integer minutosTotais
    ) {
        Object raw = body.get("progresso");
        if (percentual == null) {
            assertThat(raw).isNull();
            return;
        }
        assertThat(raw).isInstanceOf(Map.class);
        Map<String, Object> progresso = (Map<String, Object>) raw;
        assertThat(progresso).containsOnlyKeys("percentual", "minutosConcluidos", "minutosTotais");
        assertThat(((Number) progresso.get("percentual")).intValue()).isEqualTo(percentual);
        assertThat(((Number) progresso.get("minutosConcluidos")).intValue()).isEqualTo(minutosConcluidos);
        assertThat(((Number) progresso.get("minutosTotais")).intValue()).isEqualTo(minutosTotais);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> grupo(ResponseEntity<Map> response, String chave) {
        assertThat(response.getBody()).isNotNull();
        return (List<Map<String, Object>>) response.getBody().get(chave);
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
