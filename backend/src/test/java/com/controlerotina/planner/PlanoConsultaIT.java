package com.controlerotina.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.controlerotina.capacidade.entity.CapacidadeOverride;
import com.controlerotina.capacidade.repository.CapacidadeOverrideRepository;
import com.controlerotina.planner.entity.Plano;
import com.controlerotina.planner.service.PlannerGeracaoService;
import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;
import com.controlerotina.tarefa.repository.TarefaRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class PlanoConsultaIT {

    private static final String SENHA = "Senha@123";
    private static final LocalDate DIA_D = LocalDate.of(2026, 10, 20);
    private static final Instant AGORA = Instant.parse("2026-10-20T12:00:00Z");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlannerGeracaoService plannerGeracaoService;

    @Autowired
    CapacidadeOverrideRepository capacidadeOverrideRepository;

    @Autowired
    TarefaRepository tarefaRepository;

    private String accessToken;
    private UUID usuarioId;

    @BeforeEach
    void cadastrarELogar() {
        jdbcTemplate.update("delete from bloco_plano");
        jdbcTemplate.update("delete from plano");
        jdbcTemplate.update("delete from tarefa");
        jdbcTemplate.update("delete from refresh_token");
        jdbcTemplate.update("delete from capacidade_override");
        jdbcTemplate.update("delete from capacidade_semanal");
        jdbcTemplate.update("delete from usuario");

        String email = "plano-consulta-" + UUID.randomUUID() + "@email.com";
        assertThat(restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Dono Plano", "email", email, "senha", SENHA)),
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
    void deveRetornar401AoConsultarSemToken() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/planos?data=" + DIA_D,
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
    }

    @Test
    void deveRetornar400QuandoDataAusente() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/planos",
                HttpMethod.GET,
                autenticado(),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
    }

    @Test
    void deveRetornar404QuandoNaoHaPlanoParaAData() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/planos?data=" + DIA_D,
                HttpMethod.GET,
                autenticado(),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND);
    }

    @Test
    void deveRetornarVersaoCorrenteComBlocosOrdenadosEDadosMinimosDaTarefa() {
        salvarOverride(3);
        Tarefa baixa = salvarTarefa("Baixa", Prioridade.BAIXA, 1, AGORA);
        Tarefa alta = salvarTarefa("Alta", Prioridade.ALTA, 1, AGORA.plusSeconds(5));
        plannerGeracaoService.gerar(usuarioId, DIA_D);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/planos?data=" + DIA_D,
                HttpMethod.GET,
                autenticado(),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("data")).isEqualTo(DIA_D.toString());
        assertThat(response.getBody().get("versao")).isEqualTo(1);
        assertThat(response.getBody().get("status")).isEqualTo("RASCUNHO");
        assertThat(response.getBody().get("temConflito")).isEqualTo(false);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blocos = (List<Map<String, Object>>) response.getBody().get("blocos");
        assertThat(blocos).hasSize(2);
        assertThat(blocos.get(0).get("ordem")).isEqualTo(1);
        assertThat(blocos.get(0).get("duracaoMinutos")).isEqualTo(60);
        assertThat(blocos.get(1).get("ordem")).isEqualTo(2);

        @SuppressWarnings("unchecked")
        Map<String, Object> tarefa0 = (Map<String, Object>) blocos.get(0).get("tarefa");
        assertThat(tarefa0.get("id")).isEqualTo(alta.getId().toString());
        assertThat(tarefa0.get("titulo")).isEqualTo("Alta");
        assertThat(tarefa0.get("prioridade")).isEqualTo("ALTA");
        assertThat(tarefa0.get("status")).isEqualTo("PENDENTE");
        assertThat(tarefa0.get("duracaoHoras")).isEqualTo(1);

        @SuppressWarnings("unchecked")
        Map<String, Object> tarefa1 = (Map<String, Object>) blocos.get(1).get("tarefa");
        assertThat(tarefa1.get("id")).isEqualTo(baixa.getId().toString());
        assertThat(tarefa1.get("titulo")).isEqualTo("Baixa");
    }

    @Test
    void deveRetornarVersaoEspecificaQuandoInformada() {
        salvarOverride(2);
        salvarTarefa("Tarefa", Prioridade.MEDIA, 1, AGORA);
        Plano v1 = plannerGeracaoService.gerar(usuarioId, DIA_D);
        Plano v2 = plannerGeracaoService.gerar(usuarioId, DIA_D);

        ResponseEntity<Map> corrente = restTemplate.exchange(
                "/planos?data=" + DIA_D,
                HttpMethod.GET,
                autenticado(),
                Map.class);
        assertThat(corrente.getStatusCode()).isEqualTo(OK);
        assertThat(corrente.getBody().get("id")).isEqualTo(v2.getId().toString());
        assertThat(corrente.getBody().get("versao")).isEqualTo(2);

        ResponseEntity<Map> historica = restTemplate.exchange(
                "/planos?data=" + DIA_D + "&versao=1",
                HttpMethod.GET,
                autenticado(),
                Map.class);
        assertThat(historica.getStatusCode()).isEqualTo(OK);
        assertThat(historica.getBody().get("id")).isEqualTo(v1.getId().toString());
        assertThat(historica.getBody().get("versao")).isEqualTo(1);
    }

    @Test
    void deveRetornar404ParaVersaoInexistenteOuPlanoDeOutroUsuario() {
        salvarOverride(2);
        salvarTarefa("Tarefa", Prioridade.MEDIA, 1, AGORA);
        plannerGeracaoService.gerar(usuarioId, DIA_D);

        ResponseEntity<Map> versaoInexistente = restTemplate.exchange(
                "/planos?data=" + DIA_D + "&versao=99",
                HttpMethod.GET,
                autenticado(),
                Map.class);
        assertThat(versaoInexistente.getStatusCode()).isEqualTo(NOT_FOUND);

        String outroEmail = "outro-plano-" + UUID.randomUUID() + "@email.com";
        assertThat(restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Outro", "email", outroEmail, "senha", SENHA)),
                Map.class).getStatusCode()).isEqualTo(CREATED);
        ResponseEntity<Map> outroLogin = restTemplate.postForEntity(
                "/auth/login",
                json(Map.of("email", outroEmail, "senha", SENHA)),
                Map.class);
        String outroToken = outroLogin.getBody().get("accessToken").toString();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(outroToken);
        ResponseEntity<Map> outroUsuario = restTemplate.exchange(
                "/planos?data=" + DIA_D,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);
        assertThat(outroUsuario.getStatusCode()).isEqualTo(NOT_FOUND);
    }

    private void salvarOverride(int horas) {
        capacidadeOverrideRepository.save(new CapacidadeOverride(
                UUID.randomUUID(),
                usuarioId,
                DIA_D,
                horas,
                true,
                AGORA,
                AGORA
        ));
    }

    private Tarefa salvarTarefa(String titulo, Prioridade prioridade, int duracaoHoras, Instant createdAt) {
        Instant prazo = DIA_D.plusDays(5).atTime(12, 0)
                .atZone(java.time.ZoneId.of("America/Sao_Paulo"))
                .toInstant();
        return tarefaRepository.save(new Tarefa(
                UUID.randomUUID(),
                usuarioId,
                titulo,
                "descricao",
                prioridade,
                prazo,
                StatusTarefa.PENDENTE,
                duracaoHoras,
                createdAt,
                createdAt
        ));
    }

    private HttpEntity<Void> autenticado() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return new HttpEntity<>(headers);
    }

    private static HttpEntity<Map<String, ?>> json(Map<String, ?> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(new HashMap<>(body), headers);
    }
}
