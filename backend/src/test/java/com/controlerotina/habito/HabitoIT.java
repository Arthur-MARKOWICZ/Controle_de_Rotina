package com.controlerotina.habito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

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
class HabitoIT {

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
        String email = "habito-" + UUID.randomUUID() + "@email.com";
        assertThat(restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Dono Habito", "email", email, "senha", SENHA)),
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
    void t7_deveRetornar401AoCriarSemToken() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/habitos",
                json(bodyValido()),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
    }

    @Test
    void t7_deveCriarHabitoAtivoCom201() {
        Map<String, Object> body = bodyValido();
        body.put("descricao", "Treino de força");
        body.put("horarioPreferencial", "07:30");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/habitos",
                HttpMethod.POST,
                autenticadoJson(body),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("id")).isNotNull();
        assertThat(response.getBody().get("titulo")).isEqualTo("Academia");
        assertThat(response.getBody().get("descricao")).isEqualTo("Treino de força");
        assertThat(response.getBody().get("duracaoMinutos")).isEqualTo(60);
        assertThat(response.getBody().get("status")).isEqualTo("ATIVO");
        assertThat(response.getBody().get("streakAtual")).isEqualTo(0);
        assertThat(response.getBody().get("melhorStreak")).isEqualTo(0);
        assertThat(response.getBody().get("deletada")).isEqualTo(false);
        assertThat(response.getBody().get("horarioPreferencial")).asString().startsWith("07:30");
        @SuppressWarnings("unchecked")
        List<String> diasResponse = (List<String>) response.getBody().get("diasSemana");
        assertThat(diasResponse).containsExactlyInAnyOrder("SEGUNDA", "QUARTA", "SEXTA");

        UUID id = UUID.fromString(response.getBody().get("id").toString());
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select usuario_id, titulo, status, duracao_minutos from habito where id = ?", id);
        assertThat(row.get("usuario_id")).isEqualTo(usuarioId);
        assertThat(row.get("titulo")).isEqualTo("Academia");
        assertThat(row.get("status")).isEqualTo("ATIVO");
        assertThat(row.get("duracao_minutos")).isEqualTo(60);

        Integer qtdDias = jdbcTemplate.queryForObject(
                "select count(*) from habito_dia_semana where habito_id = ?",
                Integer.class,
                id);
        assertThat(qtdDias).isEqualTo(3);
    }

    @Test
    void t7_deveRejeitarDuracaoInvalidaEDiasVazios() {
        Map<String, Object> duracaoRuim = bodyValido();
        duracaoRuim.put("duracaoMinutos", 10);
        ResponseEntity<Map> r1 = restTemplate.exchange(
                "/habitos", HttpMethod.POST, autenticadoJson(duracaoRuim), Map.class);
        assertThat(r1.getStatusCode()).isEqualTo(BAD_REQUEST);

        Map<String, Object> semDias = bodyValido();
        semDias.put("diasSemana", List.of());
        ResponseEntity<Map> r2 = restTemplate.exchange(
                "/habitos", HttpMethod.POST, autenticadoJson(semDias), Map.class);
        assertThat(r2.getStatusCode()).isEqualTo(BAD_REQUEST);
    }

    @Test
    void t7_deveListarEBuscarPorId() {
        UUID id = criarHabito("Leitura", List.of("TERCA"), 45);

        ResponseEntity<Map> lista = restTemplate.exchange(
                "/habitos",
                HttpMethod.GET,
                autenticado(),
                Map.class);
        assertThat(lista.getStatusCode()).isEqualTo(OK);
        List<Map<String, Object>> content = content(lista);
        assertThat(content).extracting(m -> m.get("titulo")).contains("Leitura");

        ResponseEntity<Map> detalhe = restTemplate.exchange(
                "/habitos/" + id,
                HttpMethod.GET,
                autenticado(),
                Map.class);
        assertThat(detalhe.getStatusCode()).isEqualTo(OK);
        assertThat(detalhe.getBody().get("id")).isEqualTo(id.toString());
        assertThat(detalhe.getBody().get("titulo")).isEqualTo("Leitura");
        assertThat(detalhe.getBody().get("duracaoMinutos")).isEqualTo(45);
    }

    @Test
    void t7_deveFiltrarPorStatusEOmitirDeletadosPorPadrao() {
        UUID ativo = criarHabito("Ativo", List.of("SEGUNDA"), 30);
        UUID pausado = criarHabito("Pausado", List.of("SEGUNDA"), 30);
        jdbcTemplate.update("update habito set status = 'PAUSADO' where id = ?", pausado);
        UUID deletado = criarHabito("Deletado", List.of("SEGUNDA"), 30);
        jdbcTemplate.update("update habito set deleted_at = now() where id = ?", deletado);

        ResponseEntity<Map> padrao = restTemplate.exchange(
                "/habitos", HttpMethod.GET, autenticado(), Map.class);
        assertThat(content(padrao)).extracting(m -> m.get("id").toString())
                .contains(ativo.toString(), pausado.toString())
                .doesNotContain(deletado.toString());

        ResponseEntity<Map> soAtivos = restTemplate.exchange(
                "/habitos?status=ATIVO", HttpMethod.GET, autenticado(), Map.class);
        assertThat(content(soAtivos)).extracting(m -> m.get("id").toString())
                .contains(ativo.toString())
                .doesNotContain(pausado.toString(), deletado.toString());

        ResponseEntity<Map> detalheDeletado = restTemplate.exchange(
                "/habitos/" + deletado,
                HttpMethod.GET,
                autenticado(),
                Map.class);
        assertThat(detalheDeletado.getStatusCode()).isEqualTo(OK);
        assertThat(detalheDeletado.getBody().get("deletada")).isEqualTo(true);
    }

    @Test
    void t7_deveAtualizarHabitoParcialmente() {
        UUID id = criarHabitoComHorario("Academia", List.of("SEGUNDA", "QUARTA"), 60, "07:30");

        Map<String, Object> patch = new HashMap<>();
        patch.put("titulo", "Musculação");
        patch.put("duracaoMinutos", 45);
        patch.put("diasSemana", List.of("TERCA", "QUINTA"));
        patch.put("horarioPreferencial", null);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/habitos/" + id,
                HttpMethod.PATCH,
                autenticadoJson(patch),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody().get("titulo")).isEqualTo("Musculação");
        assertThat(response.getBody().get("duracaoMinutos")).isEqualTo(45);
        assertThat(response.getBody().get("horarioPreferencial")).isNull();
        @SuppressWarnings("unchecked")
        List<String> dias = (List<String>) response.getBody().get("diasSemana");
        assertThat(dias).containsExactlyInAnyOrder("TERCA", "QUINTA");
        assertThat(response.getBody().get("status")).isEqualTo("ATIVO");
    }

    @Test
    void t7_patchNaoAlteraStatusNemStreak() {
        UUID id = criarHabito("Meditação", List.of("SEGUNDA"), 30);
        jdbcTemplate.update(
                "update habito set streak_atual = 5, melhor_streak = 8 where id = ?", id);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/habitos/" + id,
                HttpMethod.PATCH,
                autenticadoJson(Map.of("titulo", "Meditação guiada")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody().get("titulo")).isEqualTo("Meditação guiada");
        assertThat(response.getBody().get("status")).isEqualTo("ATIVO");
        assertThat(response.getBody().get("streakAtual")).isEqualTo(5);
        assertThat(response.getBody().get("melhorStreak")).isEqualTo(8);
    }

    @Test
    void t7_deveRejeitarDuracaoInvalidaNoPatch() {
        UUID id = criarHabito("Corrida", List.of("SABADO"), 30);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/habitos/" + id,
                HttpMethod.PATCH,
                autenticadoJson(Map.of("duracaoMinutos", 10)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
    }

    @Test
    void t8_devePausarHabitoAtivo() {
        UUID id = criarHabito("Pausável", List.of("SEGUNDA"), 30);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/habitos/" + id + "/pausar",
                HttpMethod.POST,
                autenticado(),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody().get("status")).isEqualTo("PAUSADO");
    }

    @Test
    void t8_pausarJaPausadoRetorna409() {
        UUID id = criarHabito("Já pausado", List.of("SEGUNDA"), 30);
        assertThat(restTemplate.exchange(
                "/habitos/" + id + "/pausar",
                HttpMethod.POST,
                autenticado(),
                Map.class).getStatusCode()).isEqualTo(OK);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/habitos/" + id + "/pausar",
                HttpMethod.POST,
                autenticado(),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(CONFLICT);
    }

    @Test
    void t8_pausarDeletadoRetorna404() {
        UUID id = criarHabito("Deletado pausar", List.of("SEGUNDA"), 30);
        jdbcTemplate.update("update habito set deleted_at = now() where id = ?", id);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/habitos/" + id + "/pausar",
                HttpMethod.POST,
                autenticado(),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND);
    }

    @Test
    void t8_deveReativarHabitoPausado() {
        UUID id = criarHabito("Reativável", List.of("SEGUNDA"), 30);
        assertThat(restTemplate.exchange(
                "/habitos/" + id + "/pausar",
                HttpMethod.POST,
                autenticado(),
                Map.class).getStatusCode()).isEqualTo(OK);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/habitos/" + id + "/reativar",
                HttpMethod.POST,
                autenticado(),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody().get("status")).isEqualTo("ATIVO");
    }

    @Test
    void t8_reativarJaAtivoRetorna409() {
        UUID id = criarHabito("Já ativo", List.of("SEGUNDA"), 30);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/habitos/" + id + "/reativar",
                HttpMethod.POST,
                autenticado(),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(CONFLICT);
    }

    @Test
    void t9_deveSoftDeleteEOmitirDaListagem() {
        UUID id = criarHabito("Excluir", List.of("SEGUNDA"), 30);

        ResponseEntity<Void> deleted = restTemplate.exchange(
                "/habitos/" + id,
                HttpMethod.DELETE,
                autenticado(),
                Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(NO_CONTENT);

        ResponseEntity<Map> lista = restTemplate.exchange(
                "/habitos", HttpMethod.GET, autenticado(), Map.class);
        assertThat(content(lista)).extracting(m -> m.get("id").toString())
                .doesNotContain(id.toString());

        ResponseEntity<Map> detalhe = restTemplate.exchange(
                "/habitos/" + id,
                HttpMethod.GET,
                autenticado(),
                Map.class);
        assertThat(detalhe.getStatusCode()).isEqualTo(OK);
        assertThat(detalhe.getBody().get("deletada")).isEqualTo(true);
        assertThat(detalhe.getBody().get("deletedAt")).isNotNull();
    }

    @Test
    void t9_deletarJaDeletadoRetorna404() {
        UUID id = criarHabito("Dupla exclusão", List.of("SEGUNDA"), 30);
        assertThat(restTemplate.exchange(
                "/habitos/" + id,
                HttpMethod.DELETE,
                autenticado(),
                Void.class).getStatusCode()).isEqualTo(NO_CONTENT);

        assertThat(restTemplate.exchange(
                "/habitos/" + id,
                HttpMethod.DELETE,
                autenticado(),
                Void.class).getStatusCode()).isEqualTo(NOT_FOUND);
    }

    @Test
    void t9_deveRestaurarHabitoSoftDeletadoPreservandoStatus() {
        UUID id = criarHabito("Restaurar", List.of("SEGUNDA"), 30);
        assertThat(restTemplate.exchange(
                "/habitos/" + id + "/pausar",
                HttpMethod.POST,
                autenticado(),
                Map.class).getStatusCode()).isEqualTo(OK);
        assertThat(restTemplate.exchange(
                "/habitos/" + id,
                HttpMethod.DELETE,
                autenticado(),
                Void.class).getStatusCode()).isEqualTo(NO_CONTENT);

        ResponseEntity<Map> restaurado = restTemplate.exchange(
                "/habitos/" + id + "/restore",
                HttpMethod.POST,
                autenticado(),
                Map.class);

        assertThat(restaurado.getStatusCode()).isEqualTo(OK);
        assertThat(restaurado.getBody().get("deletada")).isEqualTo(false);
        assertThat(restaurado.getBody().get("deletedAt")).isNull();
        assertThat(restaurado.getBody().get("status")).isEqualTo("PAUSADO");

        ResponseEntity<Map> lista = restTemplate.exchange(
                "/habitos", HttpMethod.GET, autenticado(), Map.class);
        assertThat(content(lista)).extracting(m -> m.get("id").toString())
                .contains(id.toString());

        assertThat(restTemplate.exchange(
                "/habitos/" + id + "/restore",
                HttpMethod.POST,
                autenticado(),
                Map.class).getStatusCode()).isEqualTo(NOT_FOUND);
    }

    @Test
    void t10_outroUsuarioRecebe404() {
        UUID id = criarHabito("Privado", List.of("DOMINGO"), 30);

        String emailOutro = "outro-" + UUID.randomUUID() + "@email.com";
        assertThat(restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Outro", "email", emailOutro, "senha", SENHA)),
                Map.class).getStatusCode()).isEqualTo(CREATED);
        ResponseEntity<Map> login = restTemplate.postForEntity(
                "/auth/login",
                json(Map.of("email", emailOutro, "senha", SENHA)),
                Map.class);
        String tokenOutro = login.getBody().get("accessToken").toString();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenOutro);
        ResponseEntity<Map> response = restTemplate.exchange(
                "/habitos/" + id,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND);
    }

    private UUID criarHabitoComHorario(
            String titulo,
            List<String> dias,
            int duracaoMinutos,
            String horarioPreferencial
    ) {
        Map<String, Object> body = new HashMap<>();
        body.put("titulo", titulo);
        body.put("duracaoMinutos", duracaoMinutos);
        body.put("diasSemana", dias);
        body.put("horarioPreferencial", horarioPreferencial);
        ResponseEntity<Map> response = restTemplate.exchange(
                "/habitos",
                HttpMethod.POST,
                autenticadoJson(body),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        return UUID.fromString(response.getBody().get("id").toString());
    }

    private UUID criarHabito(String titulo, List<String> dias, int duracaoMinutos) {
        Map<String, Object> body = new HashMap<>();
        body.put("titulo", titulo);
        body.put("duracaoMinutos", duracaoMinutos);
        body.put("diasSemana", dias);
        ResponseEntity<Map> response = restTemplate.exchange(
                "/habitos",
                HttpMethod.POST,
                autenticadoJson(body),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        return UUID.fromString(response.getBody().get("id").toString());
    }

    private static Map<String, Object> bodyValido() {
        Map<String, Object> body = new HashMap<>();
        body.put("titulo", "Academia");
        body.put("duracaoMinutos", 60);
        body.put("diasSemana", List.of("SEGUNDA", "QUARTA", "SEXTA"));
        return body;
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
