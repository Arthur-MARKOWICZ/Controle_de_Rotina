package com.controlerotina.habito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

import com.controlerotina.capacidade.entity.DiaSemana;
import com.controlerotina.habito.entity.Habito;
import com.controlerotina.habito.entity.StatusHabito;
import com.controlerotina.habito.repository.HabitoRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class HabitoHistoricoIT {

    private static final String SENHA = "Senha@123";
    private static final LocalDate TERCA = LocalDate.of(2026, 10, 20);
    private static final Instant AGORA = Instant.parse("2026-10-20T12:00:00Z");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    HabitoRepository habitoRepository;

    private String accessToken;
    private UUID usuarioId;

    @BeforeEach
    void cadastrarELogar() {
        usuarioId = null;
        accessToken = cadastrar("historico-" + UUID.randomUUID() + "@email.com");
    }

    @Test
    void deveListarCumprimentosDoMaisRecenteParaOMaisAntigo() {
        UUID habitoId = criarHabito();
        concluirDireto(habitoId, TERCA.minusWeeks(1), 30);
        concluirDireto(habitoId, TERCA, 20);

        ResponseEntity<List> response = historico(habitoId, "?dataDe=" + TERCA.minusWeeks(2) + "&dataAte=" + TERCA);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).hasSize(2);
        Map<?, ?> recente = (Map<?, ?>) response.getBody().get(0);
        assertThat(recente.get("data")).isEqualTo(TERCA.toString());
        assertThat(recente.get("minutosDedicados")).isEqualTo(20);
        assertThat(recente.get("metaMinutos")).isEqualTo(30);
        assertThat(recente.get("suficiente")).isEqualTo(false);
        assertThat(recente.get("origem")).isEqualTo("INFORMADO");
        Map<?, ?> antigo = (Map<?, ?>) response.getBody().get(1);
        assertThat(antigo.get("data")).isEqualTo(TERCA.minusWeeks(1).toString());
        assertThat(antigo.get("suficiente")).isEqualTo(true);
    }

    @Test
    void deveRetornarListaVaziaParaHabitoSemCumprimentos() {
        UUID habitoId = criarHabito();

        ResponseEntity<List> response = historico(habitoId, "");

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void naoDeveListarExecucoesAbortadasNemEmAndamento() {
        UUID habitoId = criarHabito();
        Map<?, ?> abortada = iniciar(habitoId, TERCA.minusWeeks(1));
        assertThat(restTemplate.exchange("/execucoes/" + abortada.get("id") + "/abortar",
                HttpMethod.POST, authed(null), Map.class).getStatusCode()).isEqualTo(OK);
        iniciar(habitoId, TERCA);

        ResponseEntity<List> response = historico(habitoId, "?dataDe=" + TERCA.minusWeeks(2) + "&dataAte=" + TERCA);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void deveListarHistoricoDeHabitoExcluido() {
        UUID habitoId = criarHabito();
        concluirDireto(habitoId, TERCA, 30);
        assertThat(restTemplate.exchange("/habitos/" + habitoId, HttpMethod.DELETE, authed(null), Void.class)
                .getStatusCode()).isEqualTo(NO_CONTENT);

        ResponseEntity<List> response = historico(habitoId, "?dataDe=" + TERCA + "&dataAte=" + TERCA);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void deveRetornar404ParaHabitoDeOutroUsuario() {
        UUID habitoId = criarHabito();
        concluirDireto(habitoId, TERCA, 30);
        accessToken = cadastrar("historico-outro-" + UUID.randomUUID() + "@email.com");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/habitos/" + habitoId + "/historico", HttpMethod.GET, authed(null), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND);
        assertThat(response.getBody().get("title")).isEqualTo("Hábito não encontrado");
    }

    @Test
    void deveRetornar404ParaHabitoInexistente() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/habitos/" + UUID.randomUUID() + "/historico", HttpMethod.GET, authed(null), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND);
        assertThat(response.getBody().get("title")).isEqualTo("Hábito não encontrado");
    }

    @Test
    void deveRetornar400QuandoSoDataDeInformada() {
        assertIntervaloInvalido(criarHabito(), "?dataDe=" + TERCA);
    }

    @Test
    void deveRetornar400QuandoDataAteAntesDeDataDe() {
        assertIntervaloInvalido(criarHabito(), "?dataDe=" + TERCA + "&dataAte=" + TERCA.minusDays(1));
    }

    @Test
    void deveRetornar400QuandoIntervaloPassaDe366Dias() {
        assertIntervaloInvalido(criarHabito(), "?dataDe=" + TERCA.minusDays(366) + "&dataAte=" + TERCA);
    }

    @Test
    void deveRetornar401SemToken() {
        UUID habitoId = criarHabito();

        ResponseEntity<Map> response = restTemplate.getForEntity("/habitos/" + habitoId + "/historico", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
    }

    private void assertIntervaloInvalido(UUID habitoId, String query) {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/habitos/" + habitoId + "/historico" + query, HttpMethod.GET, authed(null), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
        assertThat(response.getBody().get("title")).isEqualTo("Intervalo inválido");
    }

    private ResponseEntity<List> historico(UUID habitoId, String query) {
        return restTemplate.exchange(
                "/habitos/" + habitoId + "/historico" + query, HttpMethod.GET, authed(null), List.class);
    }

    private void concluirDireto(UUID habitoId, LocalDate data, int minutos) {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/habitos/" + habitoId + "/concluir-direto?data=" + data,
                HttpMethod.POST,
                authed(Map.of("minutos", minutos)),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(OK);
    }

    private Map<?, ?> iniciar(UUID habitoId, LocalDate data) {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/habitos/" + habitoId + "/iniciar?data=" + data, HttpMethod.POST, authed(null), Map.class);
        assertThat(response.getStatusCode()).isIn(CREATED, OK);
        return response.getBody();
    }

    private UUID criarHabito() {
        return habitoRepository.save(new Habito(
                UUID.randomUUID(), usuarioId, "Ler", null, 30, Set.of(DiaSemana.TERCA), null,
                StatusHabito.ATIVO, 0, 0, AGORA, AGORA)).getId();
    }

    private String cadastrar(String email) {
        assertThat(restTemplate.postForEntity(
                "/usuarios", json(Map.of("nome", "Dono", "email", email, "senha", SENHA)), Map.class)
                .getStatusCode()).isEqualTo(CREATED);
        String token = restTemplate.postForEntity(
                "/auth/login", json(Map.of("email", email, "senha", SENHA)), Map.class)
                .getBody().get("accessToken").toString();
        if (usuarioId == null) {
            usuarioId = jdbcTemplate.queryForObject("select id from usuario where email = ?", UUID.class, email);
        }
        return token;
    }

    private HttpEntity<?> authed(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return body == null ? new HttpEntity<>(headers) : new HttpEntity<>(body, headers);
    }

    private static HttpEntity<Map<String, Object>> json(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(new HashMap<>(body), headers);
    }
}
