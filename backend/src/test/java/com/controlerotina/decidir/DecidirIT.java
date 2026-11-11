package com.controlerotina.decidir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;

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
class DecidirIT {

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

    @BeforeEach
    void cadastrarELogar() {
        String email = "decidir-" + UUID.randomUUID() + "@email.com";
        assertThat(restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Dono Decidir", "email", email, "senha", SENHA)),
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
    void deveCriarSoComTituloEListarComoAberto() {
        ResponseEntity<Map> created = restTemplate.exchange(
                "/decidir",
                HttpMethod.POST,
                autenticadoJson(Map.of("titulo", "Comprar assinatura")),
                Map.class);

        assertThat(created.getStatusCode()).isEqualTo(CREATED);
        assertThat(created.getBody().get("titulo")).isEqualTo("Comprar assinatura");
        assertThat(created.getBody().get("tarefaId")).isNull();
        assertThat(created.getBody().get("tags")).isEqualTo(List.of());

        UUID id = UUID.fromString(created.getBody().get("id").toString());
        ResponseEntity<Map> lista = restTemplate.exchange(
                "/decidir",
                HttpMethod.GET,
                autenticado(),
                Map.class);

        assertThat(lista.getStatusCode()).isEqualTo(OK);
        assertThat(content(lista)).extracting(m -> m.get("id").toString()).contains(id.toString());
    }

    @Test
    void deveRejeitarTituloEmBranco() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/decidir",
                HttpMethod.POST,
                autenticadoJson(Map.of("titulo", "  ")),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
    }

    @Test
    void devePatchAbertoEDeleteHardE404DeOutroUsuario() {
        UUID id = criarItem("Editar me");

        ResponseEntity<Map> patched = restTemplate.exchange(
                "/decidir/" + id,
                HttpMethod.PATCH,
                autenticadoJson(Map.of("titulo", "Editado", "descricao", "Detalhe")),
                Map.class);
        assertThat(patched.getStatusCode()).isEqualTo(OK);
        assertThat(patched.getBody().get("titulo")).isEqualTo("Editado");
        assertThat(patched.getBody().get("descricao")).isEqualTo("Detalhe");

        assertThat(restTemplate.exchange(
                "/decidir/" + id,
                HttpMethod.DELETE,
                autenticado(),
                Void.class).getStatusCode()).isEqualTo(NO_CONTENT);

        assertThat(restTemplate.exchange(
                "/decidir/" + id,
                HttpMethod.GET,
                autenticado(),
                Map.class).getStatusCode()).isEqualTo(NOT_FOUND);

        UUID idOutro = criarItem("Privado");
        String tokenOutro = cadastrarOutroUsuario();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenOutro);
        assertThat(restTemplate.exchange(
                "/decidir/" + idOutro,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class).getStatusCode()).isEqualTo(NOT_FOUND);
    }

    @Test
    void devePromoverMinimoRejeitarJaPromovidoEPatchPromovido() {
        UUID id = criarItem("Promover");

        ResponseEntity<Map> promovido = restTemplate.exchange(
                "/decidir/" + id + "/promover",
                HttpMethod.POST,
                autenticadoJson(Map.of()),
                Map.class);
        assertThat(promovido.getStatusCode()).isEqualTo(OK);
        assertThat(promovido.getBody().get("tarefaId")).isNotNull();
        UUID tarefaId = UUID.fromString(promovido.getBody().get("tarefaId").toString());

        Map<String, Object> tarefa = jdbcTemplate.queryForMap(
                "select titulo, deleted_at from tarefa where id = ?", tarefaId);
        assertThat(tarefa.get("titulo")).isEqualTo("Promover");
        assertThat(tarefa.get("deleted_at")).isNull();

        assertThat(content(restTemplate.exchange(
                "/decidir",
                HttpMethod.GET,
                autenticado(),
                Map.class))).extracting(m -> m.get("id").toString()).doesNotContain(id.toString());

        assertThat(content(restTemplate.exchange(
                "/decidir?promovidos=true",
                HttpMethod.GET,
                autenticado(),
                Map.class))).extracting(m -> m.get("id").toString()).contains(id.toString());

        assertThat(restTemplate.exchange(
                "/decidir/" + id + "/promover",
                HttpMethod.POST,
                autenticadoJson(Map.of()),
                Map.class).getStatusCode()).isEqualTo(CONFLICT);

        assertThat(restTemplate.exchange(
                "/decidir/" + id,
                HttpMethod.PATCH,
                autenticadoJson(Map.of("titulo", "Nao")),
                Map.class).getStatusCode()).isEqualTo(CONFLICT);
    }

    @Test
    void devePromoverComDuracaoTagsProjetoEDescricao() {
        UUID tagA = criarTag("alpha", "#112233");
        UUID tagB = criarTag("beta", "#445566");
        UUID projetoId = criarProjeto("Projeto Decidir");

        ResponseEntity<Map> created = restTemplate.exchange(
                "/decidir",
                HttpMethod.POST,
                autenticadoJson(Map.of(
                        "titulo", "Com tags",
                        "descricao", "Texto base",
                        "projetoId", projetoId.toString(),
                        "tagIds", List.of(tagA.toString(), tagB.toString())
                )),
                Map.class);
        UUID id = UUID.fromString(created.getBody().get("id").toString());

        ResponseEntity<Map> promovido = restTemplate.exchange(
                "/decidir/" + id + "/promover",
                HttpMethod.POST,
                autenticadoJson(Map.of("duracaoMinutos", 45)),
                Map.class);
        assertThat(promovido.getStatusCode()).isEqualTo(OK);
        UUID tarefaId = UUID.fromString(promovido.getBody().get("tarefaId").toString());

        Map<String, Object> tarefa = jdbcTemplate.queryForMap(
                "select titulo, descricao, duracao_minutos, projeto_id from tarefa where id = ?",
                tarefaId);
        assertThat(tarefa.get("titulo")).isEqualTo("Com tags");
        assertThat(tarefa.get("descricao")).isEqualTo("Texto base");
        assertThat(tarefa.get("duracao_minutos")).isEqualTo(45);
        assertThat(tarefa.get("projeto_id")).isEqualTo(projetoId);

        List<UUID> tagsTarefa = jdbcTemplate.queryForList(
                "select tag_id from tarefa_tag where tarefa_id = ?",
                UUID.class,
                tarefaId);
        assertThat(tagsTarefa).containsExactlyInAnyOrder(tagA, tagB);
    }

    @Test
    void deveFiltrarPorTagsComMatchEAntesDeOuEApagarTagRemoveVinculo() {
        UUID tagA = criarTag("t1", "#111111");
        UUID tagB = criarTag("t2", "#222222");
        UUID tagC = criarTag("t3", "#333333");

        UUID idA = criarItemComTags("A", tagA, tagB);
        UUID idB = criarItemComTags("B", tagA);
        criarItemComTags("C", tagC);

        ResponseEntity<Map> filtrado = restTemplate.exchange(
                "/decidir?tagId=" + tagA + "&tagId=" + tagB + "&size=50",
                HttpMethod.GET,
                autenticado(),
                Map.class);
        List<Map<String, Object>> items = content(filtrado);
        assertThat(items).extracting(m -> m.get("id").toString())
                .containsExactly(idA.toString(), idB.toString());
        assertThat(items.get(0).get("tagMatchScore")).isEqualTo(2);
        assertThat(items.get(1).get("tagMatchScore")).isEqualTo(1);

        assertThat(restTemplate.exchange(
                "/tags/" + tagA,
                HttpMethod.DELETE,
                autenticado(),
                Void.class).getStatusCode()).isEqualTo(NO_CONTENT);

        Integer vinculos = jdbcTemplate.queryForObject(
                "select count(*) from decidir_tag where tag_id = ?",
                Integer.class,
                tagA);
        assertThat(vinculos).isZero();
    }

    @Test
    void softDeleteTarefaReabreItemERestoreNaoReliga() {
        UUID id = criarItem("Reabrir");
        ResponseEntity<Map> promovido = restTemplate.exchange(
                "/decidir/" + id + "/promover",
                HttpMethod.POST,
                autenticadoJson(Map.of()),
                Map.class);
        UUID tarefaId = UUID.fromString(promovido.getBody().get("tarefaId").toString());

        assertThat(restTemplate.exchange(
                "/tarefas/" + tarefaId,
                HttpMethod.DELETE,
                autenticado(),
                Void.class).getStatusCode()).isEqualTo(NO_CONTENT);

        ResponseEntity<Map> reaberto = restTemplate.exchange(
                "/decidir/" + id,
                HttpMethod.GET,
                autenticado(),
                Map.class);
        assertThat(reaberto.getStatusCode()).isEqualTo(OK);
        assertThat(reaberto.getBody().get("tarefaId")).isNull();
        assertThat(content(restTemplate.exchange(
                "/decidir",
                HttpMethod.GET,
                autenticado(),
                Map.class))).extracting(m -> m.get("id").toString()).contains(id.toString());

        assertThat(restTemplate.exchange(
                "/tarefas/" + tarefaId + "/restore",
                HttpMethod.POST,
                autenticado(),
                Map.class).getStatusCode()).isEqualTo(OK);

        ResponseEntity<Map> aposRestore = restTemplate.exchange(
                "/decidir/" + id,
                HttpMethod.GET,
                autenticado(),
                Map.class);
        assertThat(aposRestore.getBody().get("tarefaId")).isNull();
    }

    @Test
    void arquivarProjetoZeraProjetoIdDoItem() {
        UUID projetoId = criarProjeto("Arquivar");
        ResponseEntity<Map> created = restTemplate.exchange(
                "/decidir",
                HttpMethod.POST,
                autenticadoJson(Map.of(
                        "titulo", "Com projeto",
                        "projetoId", projetoId.toString()
                )),
                Map.class);
        UUID id = UUID.fromString(created.getBody().get("id").toString());
        assertThat(created.getBody().get("projetoId").toString()).isEqualTo(projetoId.toString());

        assertThat(restTemplate.exchange(
                "/projetos/" + projetoId + "/arquivar",
                HttpMethod.POST,
                autenticado(),
                Map.class).getStatusCode()).isEqualTo(OK);

        ResponseEntity<Map> item = restTemplate.exchange(
                "/decidir/" + id,
                HttpMethod.GET,
                autenticado(),
                Map.class);
        assertThat(item.getStatusCode()).isEqualTo(OK);
        assertThat(item.getBody().get("projetoId")).isNull();
        assertThat(item.getBody().get("titulo")).isEqualTo("Com projeto");
    }

    @Test
    void substituirTagsNoPatch() {
        UUID tagA = criarTag("a", "#aaaaaa");
        UUID tagB = criarTag("b", "#bbbbbb");
        UUID id = criarItemComTags("Tags", tagA);

        ResponseEntity<Map> patched = restTemplate.exchange(
                "/decidir/" + id,
                HttpMethod.PATCH,
                autenticadoJson(Map.of("tagIds", List.of(tagB.toString()))),
                Map.class);
        assertThat(patched.getStatusCode()).isEqualTo(OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tags = (List<Map<String, Object>>) patched.getBody().get("tags");
        assertThat(tags).extracting(m -> m.get("id").toString()).containsExactly(tagB.toString());
    }

    private UUID criarItem(String titulo) {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/decidir",
                HttpMethod.POST,
                autenticadoJson(Map.of("titulo", titulo)),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        return UUID.fromString(response.getBody().get("id").toString());
    }

    private UUID criarItemComTags(String titulo, UUID... tagIds) {
        Map<String, Object> body = new HashMap<>();
        body.put("titulo", titulo);
        body.put("tagIds", java.util.Arrays.stream(tagIds).map(UUID::toString).toList());
        ResponseEntity<Map> response = restTemplate.exchange(
                "/decidir",
                HttpMethod.POST,
                autenticadoJson(body),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        return UUID.fromString(response.getBody().get("id").toString());
    }

    private UUID criarTag(String nome, String cor) {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/tags",
                HttpMethod.POST,
                autenticadoJson(Map.of("nome", nome, "cor", cor)),
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

    private String cadastrarOutroUsuario() {
        String email = "outro-decidir-" + UUID.randomUUID() + "@email.com";
        assertThat(restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Outro", "email", email, "senha", SENHA)),
                Map.class).getStatusCode()).isEqualTo(CREATED);
        ResponseEntity<Map> login = restTemplate.postForEntity(
                "/auth/login",
                json(Map.of("email", email, "senha", SENHA)),
                Map.class);
        return login.getBody().get("accessToken").toString();
    }

    private HttpEntity<Void> autenticado() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return new HttpEntity<>(headers);
    }

    private HttpEntity<?> autenticadoJson(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        return new HttpEntity<>(body, headers);
    }

    private static HttpEntity<?> json(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> content(ResponseEntity<Map> response) {
        assertThat(response.getBody()).isNotNull();
        return (List<Map<String, Object>>) response.getBody().get("content");
    }
}
