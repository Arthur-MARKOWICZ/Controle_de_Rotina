package com.controlerotina.tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.CREATED;
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
class TagVinculoIT {

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
        String email = "tag-vinc-" + UUID.randomUUID() + "@email.com";
        assertThat(restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Dono Vinculo", "email", email, "senha", SENHA)),
                Map.class).getStatusCode()).isEqualTo(CREATED);

        ResponseEntity<Map> login = restTemplate.postForEntity(
                "/auth/login",
                json(Map.of("email", email, "senha", SENHA)),
                Map.class);
        assertThat(login.getStatusCode()).isEqualTo(OK);
        accessToken = login.getBody().get("accessToken").toString();
    }

    @Test
    void deveCriarHabitoEProjetoComTags() {
        UUID tagA = criarTag("alpha");
        UUID tagB = criarTag("beta");

        ResponseEntity<Map> habito = restTemplate.exchange(
                "/habitos",
                HttpMethod.POST,
                autenticadoJson(Map.of(
                        "titulo", "H1",
                        "duracaoMinutos", 30,
                        "diasSemana", List.of("SEGUNDA"),
                        "tagIds", List.of(tagA.toString(), tagB.toString())
                )),
                Map.class);
        assertThat(habito.getStatusCode()).isEqualTo(CREATED);
        List<?> tagsHabito = (List<?>) habito.getBody().get("tags");
        assertThat(tagsHabito).hasSize(2);

        ResponseEntity<Map> projeto = restTemplate.exchange(
                "/projetos",
                HttpMethod.POST,
                autenticadoJson(Map.of(
                        "nome", "P1",
                        "tagIds", List.of(tagA.toString())
                )),
                Map.class);
        assertThat(projeto.getStatusCode()).isEqualTo(CREATED);
        List<?> tagsProjeto = (List<?>) projeto.getBody().get("tags");
        assertThat(tagsProjeto).hasSize(1);
    }

    @Test
    void deveSubstituirTagsNoPatchDeHabitoEProjeto() {
        UUID tagA = criarTag("patch_a");
        UUID tagB = criarTag("patch_b");

        UUID habitoId = UUID.fromString(restTemplate.exchange(
                "/habitos",
                HttpMethod.POST,
                autenticadoJson(habitoBody("Patch H", tagA)),
                Map.class).getBody().get("id").toString());

        ResponseEntity<Map> patchHabito = restTemplate.exchange(
                "/habitos/" + habitoId,
                HttpMethod.PATCH,
                autenticadoJson(Map.of("tagIds", List.of(tagB.toString()))),
                Map.class);
        assertThat(patchHabito.getStatusCode()).isEqualTo(OK);
        List<Map<?, ?>> tags = (List<Map<?, ?>>) patchHabito.getBody().get("tags");
        assertThat(tags).hasSize(1);
        assertThat(tags.get(0).get("id")).isEqualTo(tagB.toString());

        UUID projetoId = UUID.fromString(restTemplate.exchange(
                "/projetos",
                HttpMethod.POST,
                autenticadoJson(Map.of("nome", "Patch P", "tagIds", List.of(tagA.toString()))),
                Map.class).getBody().get("id").toString());

        ResponseEntity<Map> patchProjeto = restTemplate.exchange(
                "/projetos/" + projetoId,
                HttpMethod.PATCH,
                autenticadoJson(Map.of("tagIds", List.of())),
                Map.class);
        assertThat(patchProjeto.getStatusCode()).isEqualTo(OK);
        assertThat((List<?>) patchProjeto.getBody().get("tags")).isEmpty();
    }

    @Test
    void deveFiltrarPorUniaoDeTags() {
        UUID tagA = criarTag("uni_a");
        UUID tagB = criarTag("uni_b");

        UUID idA = criarHabitoComTag("Só A", tagA);
        UUID idB = criarHabitoComTag("Só B", tagB);
        criarHabitoComTag("Sem tag", null);

        ResponseEntity<Map> lista = restTemplate.exchange(
                "/habitos?tagId=" + tagA + "&tagId=" + tagB + "&size=50",
                HttpMethod.GET,
                autenticado(),
                Map.class);

        assertThat(lista.getStatusCode()).isEqualTo(OK);
        List<Map<?, ?>> content = (List<Map<?, ?>>) lista.getBody().get("content");
        assertThat(content).extracting(c -> c.get("id").toString())
                .containsExactlyInAnyOrder(idA.toString(), idB.toString());
    }

    @Test
    void deveOrdenarMatchCompletoAntesDeParcial() {
        UUID tagA = criarTag("ord_a");
        UUID tagB = criarTag("ord_b");

        criarHabitoComTags("Parcial", tagA);
        criarHabitoComTags("Completo", tagA, tagB);

        ResponseEntity<Map> lista = restTemplate.exchange(
                "/habitos?tagId=" + tagA + "&tagId=" + tagB + "&size=50",
                HttpMethod.GET,
                autenticado(),
                Map.class);

        assertThat(lista.getStatusCode()).isEqualTo(OK);
        List<Map<?, ?>> content = (List<Map<?, ?>>) lista.getBody().get("content");
        assertThat(content).hasSize(2);
        assertThat(content.get(0).get("titulo")).isEqualTo("Completo");
        assertThat(content.get(0).get("tagMatchScore")).isEqualTo(2);
        assertThat(content.get(1).get("titulo")).isEqualTo("Parcial");
        assertThat(content.get(1).get("tagMatchScore")).isEqualTo(1);
    }

    @Test
    void tagNoProjetoNaoFazTarefaAparecerNoFiltroDeTarefas() {
        UUID tag = criarTag("proj_only");

        UUID projetoId = UUID.fromString(restTemplate.exchange(
                "/projetos",
                HttpMethod.POST,
                autenticadoJson(Map.of("nome", "Com tag", "tagIds", List.of(tag.toString()))),
                Map.class).getBody().get("id").toString());

        restTemplate.exchange(
                "/tarefas",
                HttpMethod.POST,
                autenticadoJson(Map.of("titulo", "Filha", "projetoId", projetoId.toString())),
                Map.class);

        ResponseEntity<Map> lista = restTemplate.exchange(
                "/tarefas?tagId=" + tag,
                HttpMethod.GET,
                autenticado(),
                Map.class);

        assertThat(lista.getStatusCode()).isEqualTo(OK);
        List<?> content = (List<?>) lista.getBody().get("content");
        assertThat(content).isEmpty();
    }

    private UUID criarTag(String nome) {
        return UUID.fromString(restTemplate.exchange(
                "/tags",
                HttpMethod.POST,
                autenticadoJson(Map.of("nome", nome)),
                Map.class).getBody().get("id").toString());
    }

    private UUID criarHabitoComTag(String titulo, UUID tagId) {
        Map<String, Object> body = habitoBody(titulo, tagId);
        return UUID.fromString(restTemplate.exchange(
                "/habitos",
                HttpMethod.POST,
                autenticadoJson(body),
                Map.class).getBody().get("id").toString());
    }

    private void criarHabitoComTags(String titulo, UUID... tagIds) {
        Map<String, Object> body = new HashMap<>();
        body.put("titulo", titulo);
        body.put("duracaoMinutos", 30);
        body.put("diasSemana", List.of("SEGUNDA"));
        body.put("tagIds", java.util.Arrays.stream(tagIds).map(UUID::toString).toList());
        restTemplate.exchange("/habitos", HttpMethod.POST, autenticadoJson(body), Map.class);
    }

    private static Map<String, Object> habitoBody(String titulo, UUID tagId) {
        Map<String, Object> body = new HashMap<>();
        body.put("titulo", titulo);
        body.put("duracaoMinutos", 30);
        body.put("diasSemana", List.of("SEGUNDA"));
        if (tagId != null) {
            body.put("tagIds", List.of(tagId.toString()));
        }
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

    private static HttpEntity<Map<String, Object>> json(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
