package com.controlerotina.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.controlerotina.auth.jwt.JwtAuthenticationFilter;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class ExportIT {

    private static final String SENHA = "Senha@123";
    /** 2026-11-24 23:50 BRT (já 25/11 em UTC). */
    private static final Instant AGORA = Instant.parse("2026-11-25T02:50:00Z");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @TestConfiguration
    static class RelogioConfig {
        @Bean
        @Primary
        Relogio relogio() {
            return new Relogio();
        }
    }

    static class Relogio extends Clock {
        Instant agora = AGORA;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(agora, zone);
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }

    @Autowired TestRestTemplate restTemplate;
    @Autowired JdbcTemplate jdbc;
    @Autowired Relogio relogio;

    private UUID usuarioId;
    private String accessToken;
    private DadosUsuarioSeeder.Ids idsA;
    private DadosUsuarioSeeder.Ids idsB;

    @BeforeEach
    void setUp() {
        relogio.agora = AGORA;
        DadosUsuarioSeeder seeder = new DadosUsuarioSeeder(jdbc);
        String email = "export-" + UUID.randomUUID() + "@email.com";
        accessToken = cadastrarELogar(email);
        usuarioId = UUID.fromString(chamar(HttpMethod.GET, "/me", accessToken).getBody().get("id").toString());
        idsA = seeder.popularTudo(usuarioId);
        UUID b = seeder.criarUsuario("outro-" + UUID.randomUUID() + "@email.com");
        idsB = seeder.popularTudo(b);
    }

    @AfterEach
    void restaurarRelogio() {
        relogio.agora = AGORA;
    }

    @Test
    void deveExportarTodosOsDominiosComoAnexo() {
        ResponseEntity<Map> response = chamar(HttpMethod.GET, "/me/export", accessToken);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"controle-rotina-export-2026-11-24.json\"");
        Map<String, Object> body = response.getBody();
        assertThat(body).containsEntry("versao", 1).containsEntry("geradoEm", "2026-11-25T02:50:00Z");
        assertThat((Map<String, Object>) body.get("conta")).containsEntry("id", usuarioId.toString());
        assertThat(ids(body, "tarefas")).containsExactlyInAnyOrder(
                idsA.get("tarefa").toString(), idsA.get("subtarefa").toString(), idsA.get("tarefaApagada").toString());
        assertThat(ids(body, "projetos")).hasSize(2);
        assertThat(ids(body, "reservasProjeto")).containsExactly(idsA.get("reserva").toString());
        assertThat(ids(body, "tags")).containsExactly(idsA.get("tag").toString());
        assertThat((List<?>) body.get("vinculosTag")).hasSize(4);
        assertThat(ids(body, "habitos")).hasSize(2);
        assertThat(ids(body, "itensDecidir")).containsExactly(idsA.get("itemDecidir").toString());
        assertThat(ids(body, "pendencias")).containsExactly(idsA.get("espera").toString());
        assertThat(ids(body, "objetivos")).hasSize(2);
        assertThat((List<?>) body.get("vinculosObjetivo")).hasSize(3);
        assertThat(ids(body, "capacidadeOverrides")).containsExactly(idsA.get("capacidadeOverride").toString());
        assertThat(ids(body, "planos")).containsExactly(idsA.get("plano").toString());
        assertThat(ids(body, "blocosPlano")).containsExactlyInAnyOrder(
                idsA.get("blocoTarefa").toString(), idsA.get("blocoHabito").toString());
        assertThat(ids(body, "execucoes")).containsExactly(idsA.get("execucao").toString());
        assertThat(ids(body, "eventosXp")).containsExactly(idsA.get("xpEvento").toString());
    }

    @Test
    void naoDeveConterNenhumIdDeOutroUsuario() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/me/export", HttpMethod.GET, new HttpEntity<>(bearer(accessToken)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        idsB.porNome().values().forEach(id -> assertThat(response.getBody()).doesNotContain(id.toString()));
    }

    @Test
    void deveIncluirRegistrosApagadosEArquivadosComSeuEstado() {
        Map<String, Object> body = chamar(HttpMethod.GET, "/me/export", accessToken).getBody();

        assertThat(item(body, "tarefas", idsA.get("tarefaApagada")).get("deletedAt")).isNotNull();
        assertThat(item(body, "habitos", idsA.get("habitoApagado")).get("deletedAt")).isNotNull();
        assertThat(item(body, "projetos", idsA.get("projetoArquivado"))).containsEntry("status", "ARQUIVADO");
        assertThat(item(body, "objetivos", idsA.get("objetivoArquivado")))
                .containsEntry("status", "ARQUIVADO")
                .extractingByKey("arquivadoEm").isNotNull();
    }

    @Test
    void contaSemDadosDeveTrazerListasVaziasECapacidadePadrao() {
        String token = cadastrarELogar("vazio-" + UUID.randomUUID() + "@email.com");
        UUID id = UUID.fromString(chamar(HttpMethod.GET, "/me", token).getBody().get("id").toString());
        long capacidadePadrao = jdbc.queryForObject(
                "SELECT count(*) FROM capacidade_semanal WHERE usuario_id = ?", Long.class, id);

        Map<String, Object> body = chamar(HttpMethod.GET, "/me/export", token).getBody();

        assertThat((List<?>) body.get("tarefas")).isEmpty();
        assertThat((List<?>) body.get("planos")).isEmpty();
        assertThat((List<?>) body.get("vinculosTag")).isEmpty();
        assertThat((List<?>) body.get("blocosPlano")).isEmpty();
        assertThat(capacidadePadrao).isPositive();
        assertThat((List<?>) body.get("capacidadeSemanal")).hasSize((int) capacidadePadrao);
    }

    @Test
    void naoDeveConterSenhaNemRefreshToken() {
        String hashRefresh = jdbc.queryForObject(
                "SELECT token_hash FROM refresh_token WHERE id = ?", String.class, idsA.get("refreshToken"));

        ResponseEntity<String> response = restTemplate.exchange(
                "/me/export", HttpMethod.GET, new HttpEntity<>(bearer(accessToken)), String.class);

        assertThat(response.getBody())
                .doesNotContainIgnoringCase("senha")
                .doesNotContainIgnoringCase("refresh")
                .doesNotContain(hashRefresh);
    }

    @Test
    void nomeDoArquivoDeveVirarODiaNaMeiaNoiteBrt() {
        relogio.agora = Instant.parse("2026-11-25T03:00:00Z");

        ResponseEntity<Map> response = chamar(HttpMethod.GET, "/me/export", accessToken);

        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("controle-rotina-export-2026-11-25.json");
    }

    @Test
    void semTokenDeveRetornar401NaoAutorizado() {
        ResponseEntity<Map> response = chamar(HttpMethod.GET, "/me/export", null);

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("title", "Não autorizado");
    }

    @Test
    void tokenComAssinaturaInvalidaDeveRetornar401NaoAutorizado() {
        ResponseEntity<Map> response = chamar(HttpMethod.GET, "/me/export", accessToken + "x");

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("title", "Não autorizado");
    }

    @Test
    void contaExcluidaDeveRetornar401ContaExcluida() {
        String email = "excluida-" + UUID.randomUUID() + "@email.com";
        String dispositivo1 = cadastrarELogar(email);
        String dispositivo2 = login(email);
        HttpHeaders headers = bearer(dispositivo1);
        headers.setContentType(MediaType.APPLICATION_JSON);
        assertThat(restTemplate.exchange("/me", HttpMethod.DELETE,
                new HttpEntity<>(Map.of("senhaAtual", SENHA), headers), Void.class).getStatusCode())
                .isEqualTo(NO_CONTENT);

        ResponseEntity<Map> response = chamar(HttpMethod.GET, "/me/export", dispositivo2);

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("title", JwtAuthenticationFilter.TITULO_CONTA_EXCLUIDA);
    }

    private static List<String> ids(Map<String, Object> body, String secao) {
        return ((List<Map<String, Object>>) body.get(secao)).stream().map(m -> m.get("id").toString()).toList();
    }

    private static Map<String, Object> item(Map<String, Object> body, String secao, UUID id) {
        return ((List<Map<String, Object>>) body.get(secao)).stream()
                .filter(m -> id.toString().equals(m.get("id")))
                .findFirst()
                .orElseThrow();
    }

    private String cadastrarELogar(String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        assertThat(restTemplate.postForEntity("/usuarios",
                new HttpEntity<>(Map.of("nome", "Ana", "email", email, "senha", SENHA), headers), Map.class)
                .getStatusCode()).isEqualTo(CREATED);
        return login(email);
    }

    private String login(String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> login = restTemplate.postForEntity("/auth/login",
                new HttpEntity<>(Map.of("email", email, "senha", SENHA), headers), Map.class);
        assertThat(login.getStatusCode()).isEqualTo(OK);
        return login.getBody().get("accessToken").toString();
    }

    private ResponseEntity<Map> chamar(HttpMethod method, String path, String token) {
        return restTemplate.exchange(path, method, new HttpEntity<>(bearer(token)), Map.class);
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }
}
