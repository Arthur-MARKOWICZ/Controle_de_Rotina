package com.controlerotina.habito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NOT_FOUND;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
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
import com.controlerotina.planner.entity.BlocoPlano;
import com.controlerotina.planner.entity.Plano;
import com.controlerotina.planner.entity.StatusPlano;
import com.controlerotina.planner.repository.BlocoPlanoRepository;
import com.controlerotina.planner.repository.PlanoRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class HabitoExecucaoAvulsaIT {

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

    @Autowired
    PlanoRepository planoRepository;

    @Autowired
    BlocoPlanoRepository blocoPlanoRepository;

    private String accessToken;
    private UUID usuarioId;

    @BeforeEach
    void cadastrarELogar() {
        jdbcTemplate.update("delete from execucao");
        jdbcTemplate.update("delete from bloco_plano");
        jdbcTemplate.update("delete from plano");
        jdbcTemplate.update("delete from habito_dia_semana");
        jdbcTemplate.update("delete from habito");

        String email = "habito-avulso-" + UUID.randomUUID() + "@email.com";
        assertThat(restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Dono", "email", email, "senha", SENHA)),
                Map.class).getStatusCode()).isEqualTo(CREATED);

        ResponseEntity<Map> login = restTemplate.postForEntity(
                "/auth/login",
                json(Map.of("email", email, "senha", SENHA)),
                Map.class);
        accessToken = login.getBody().get("accessToken").toString();
        usuarioId = jdbcTemplate.queryForObject(
                "select id from usuario where email = ?", UUID.class, email);
    }

    @Test
    void t6_deveIniciarHabitoSemPlanoAprovadoERepetir409() {
        UUID habitoId = criarHabitoCorrer();

        ResponseEntity<Map> primeiro = restTemplate.exchange(
                "/habitos/" + habitoId + "/iniciar?data=" + TERCA,
                HttpMethod.POST,
                authed(null),
                Map.class);
        assertThat(primeiro.getStatusCode()).isIn(CREATED, OK);
        assertThat(primeiro.getBody().get("status")).isEqualTo("EM_ANDAMENTO");

        assertThat(planoRepository.findByUsuarioIdAndDataAndStatus(
                usuarioId, TERCA, StatusPlano.HABITO_AVULSO)).isPresent();
        assertThat(planoRepository.findFirstByUsuarioIdAndDataAndStatusOrderByVersaoDesc(
                usuarioId, TERCA, StatusPlano.APROVADO)).isEmpty();

        ResponseEntity<Map> segundo = restTemplate.exchange(
                "/habitos/" + habitoId + "/iniciar?data=" + TERCA,
                HttpMethod.POST,
                authed(null),
                Map.class);
        assertThat(segundo.getStatusCode()).isEqualTo(CONFLICT);
    }

    @Test
    void t7_deveConcluirDiretoERepetir409() {
        UUID habitoId = criarHabitoCorrer();

        ResponseEntity<Map> primeiro = restTemplate.exchange(
                "/habitos/" + habitoId + "/concluir-direto?data=" + TERCA,
                HttpMethod.POST,
                authed(null),
                Map.class);
        assertThat(primeiro.getStatusCode()).isEqualTo(OK);
        assertThat(primeiro.getBody().get("status")).isEqualTo("CONCLUIDO");
        assertThat(primeiro.getBody().get("xp")).isNotNull();

        Integer streak = jdbcTemplate.queryForObject(
                "select streak_atual from habito where id = ?", Integer.class, habitoId);
        assertThat(streak).isEqualTo(1);

        ResponseEntity<Map> segundo = restTemplate.exchange(
                "/habitos/" + habitoId + "/concluir-direto?data=" + TERCA,
                HttpMethod.POST,
                authed(null),
                Map.class);
        assertThat(segundo.getStatusCode()).isEqualTo(CONFLICT);
    }

    @Test
    void t8_devePermitirExecucaoAvulsaAlemDoPlanoAprovadoComStreakUnico() {
        UUID habitoId = criarHabitoCorrer();
        Plano aprovado = planoRepository.save(new Plano(
                UUID.randomUUID(), usuarioId, TERCA, 1, StatusPlano.APROVADO,
                false, null, null, AGORA, AGORA, AGORA));
        BlocoPlano blocoPlano = blocoPlanoRepository.save(new BlocoPlano(
                UUID.randomUUID(), aprovado.getId(), null, habitoId, 30, 1, AGORA, AGORA));

        ResponseEntity<Map> inicioPlano = restTemplate.exchange(
                "/execucoes/iniciar",
                HttpMethod.POST,
                authed(Map.of("blocoId", blocoPlano.getId().toString())),
                Map.class);
        assertThat(inicioPlano.getStatusCode()).isIn(CREATED, OK);
        String execPlanoId = inicioPlano.getBody().get("id").toString();

        ResponseEntity<Map> concluiPlano = restTemplate.exchange(
                "/execucoes/" + execPlanoId + "/concluir",
                HttpMethod.POST,
                authed(null),
                Map.class);
        assertThat(concluiPlano.getStatusCode()).isEqualTo(OK);

        Integer streakAposPlano = jdbcTemplate.queryForObject(
                "select streak_atual from habito where id = ?", Integer.class, habitoId);
        assertThat(streakAposPlano).isEqualTo(1);

        ResponseEntity<Map> avulso = restTemplate.exchange(
                "/habitos/" + habitoId + "/concluir-direto?data=" + TERCA,
                HttpMethod.POST,
                authed(null),
                Map.class);
        assertThat(avulso.getStatusCode()).isEqualTo(OK);
        assertThat(avulso.getBody().get("status")).isEqualTo("CONCLUIDO");

        Integer streakAposAvulso = jdbcTemplate.queryForObject(
                "select streak_atual from habito where id = ?", Integer.class, habitoId);
        assertThat(streakAposAvulso).isEqualTo(1);

        Long execucoes = jdbcTemplate.queryForObject(
                """
                        select count(*) from execucao e
                        join bloco_plano b on b.id = e.bloco_plano_id
                        where b.habito_id = ? and e.status = 'CONCLUIDO'
                        """,
                Long.class,
                habitoId);
        assertThat(execucoes).isEqualTo(2L);
    }

    @Test
    void t9_doDiaECalendarioDevemListarHabitoSemPlanoE401SemToken() {
        UUID habitoId = criarHabitoCorrer();

        ResponseEntity<List> doDia = restTemplate.exchange(
                "/habitos/do-dia?data=" + TERCA,
                HttpMethod.GET,
                authed(null),
                List.class);
        assertThat(doDia.getStatusCode()).isEqualTo(OK);
        assertThat(doDia.getBody()).hasSize(1);
        assertThat(((Map<?, ?>) doDia.getBody().get(0)).get("id")).isEqualTo(habitoId.toString());

        ResponseEntity<Map> cal = restTemplate.exchange(
                "/calendario?dataDe=" + TERCA + "&dataAte=" + TERCA,
                HttpMethod.GET,
                authed(null),
                Map.class);
        assertThat(cal.getStatusCode()).isEqualTo(OK);
        List<?> dias = (List<?>) cal.getBody().get("dias");
        Map<?, ?> dia = (Map<?, ?>) dias.get(0);
        List<?> habitos = (List<?>) dia.get("habitosDoDia");
        assertThat(habitos).hasSize(1);

        ResponseEntity<Map> semToken = restTemplate.getForEntity(
                "/habitos/do-dia?data=" + TERCA,
                Map.class);
        assertThat(semToken.getStatusCode()).isEqualTo(UNAUTHORIZED);
    }

    @Test
    void t10_deveExecutarBlocoHabitoAvulsoViaExecucoes() {
        UUID habitoId = criarHabitoCorrer();
        ResponseEntity<Map> inicio = restTemplate.exchange(
                "/habitos/" + habitoId + "/iniciar?data=" + TERCA,
                HttpMethod.POST,
                authed(null),
                Map.class);
        String execucaoId = inicio.getBody().get("id").toString();
        String blocoId = inicio.getBody().get("blocoPlanoId").toString();

        ResponseEntity<Map> abortar = restTemplate.exchange(
                "/execucoes/" + execucaoId + "/abortar",
                HttpMethod.POST,
                authed(null),
                Map.class);
        assertThat(abortar.getStatusCode()).isEqualTo(OK);

        ResponseEntity<Map> reiniciar = restTemplate.exchange(
                "/execucoes/iniciar",
                HttpMethod.POST,
                authed(Map.of("blocoId", blocoId)),
                Map.class);
        assertThat(reiniciar.getStatusCode()).isEqualTo(OK);

        assertThat(planoRepository.findFirstByUsuarioIdAndDataAndStatusOrderByVersaoDesc(
                usuarioId, TERCA, StatusPlano.APROVADO)).isEmpty();
    }

    @Test
    void deveRetornar404AoIniciarHabitoDeOutroUsuario() {
        UUID habitoId = criarHabitoCorrer();
        String emailOutro = "hab-avulso-outro-" + UUID.randomUUID() + "@email.com";
        restTemplate.postForEntity("/usuarios", json(Map.of("nome", "Outro", "email", emailOutro, "senha", SENHA)), Map.class);
        String tokenOutro = restTemplate.postForEntity(
                "/auth/login", json(Map.of("email", emailOutro, "senha", SENHA)), Map.class)
                .getBody().get("accessToken").toString();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenOutro);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/habitos/" + habitoId + "/iniciar?data=" + TERCA,
                HttpMethod.POST,
                new HttpEntity<>(headers),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND);
        assertThat(response.getBody().get("title")).isEqualTo("Hábito não encontrado");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from execucao where usuario_id <> ?", Integer.class, usuarioId)).isZero();
    }

    @Test
    void deveRetornar409AoIniciarHabitoEmDiaNaoElegivel() {
        UUID habitoId = criarHabitoCorrer();
        LocalDate segunda = LocalDate.of(2026, 10, 19);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/habitos/" + habitoId + "/iniciar?data=" + segunda,
                HttpMethod.POST,
                authed(null),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(CONFLICT);
        assertThat(response.getBody().get("title")).isEqualTo("Hábito não elegível");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from execucao e join bloco_plano b on b.id = e.bloco_plano_id where b.habito_id = ?",
                Long.class,
                habitoId)).isZero();
    }

    @Test
    void deveGravarMinutosInformadosECreditarXpPorEles() {
        UUID habitoId = criarHabitoCorrer();

        ResponseEntity<Map> response = concluirDireto(habitoId, TERCA, Map.of("minutos", 20));

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody().get("status")).isEqualTo("CONCLUIDO");
        assertThat(xpGanho(response)).isEqualTo(30);
        assertThat(minutosInformados(habitoId)).isEqualTo(20);
    }

    @Test
    void deveUsarDuracaoDoBlocoECreditarXpCheioQuandoConcluirDiretoSemBody() {
        UUID habitoId = criarHabitoCorrer();

        ResponseEntity<Map> response = concluirDireto(habitoId, TERCA, null);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(xpGanho(response)).isEqualTo(45);
        assertThat(minutosInformados(habitoId)).isEqualTo(30);
    }

    @Test
    void deveLimitarXpNaDuracaoQuandoMinutosInformadosPassamDaMeta() {
        UUID habitoId = criarHabitoCorrer();

        ResponseEntity<Map> response = concluirDireto(habitoId, TERCA, Map.of("minutos", 60));

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(xpGanho(response)).isEqualTo(45);
        assertThat(minutosInformados(habitoId)).isEqualTo(60);
    }

    @Test
    void deveUsarTimerEIgnorarMinutosQuandoExecucaoJaEstaEmAndamento() {
        UUID habitoId = criarHabitoCorrer();
        ResponseEntity<Map> inicio = restTemplate.exchange(
                "/habitos/" + habitoId + "/iniciar?data=" + TERCA,
                HttpMethod.POST,
                authed(null),
                Map.class);
        assertThat(inicio.getBody().get("status")).isEqualTo("EM_ANDAMENTO");

        ResponseEntity<Map> response = concluirDireto(habitoId, TERCA, Map.of("minutos", 25));

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody().get("status")).isEqualTo("CONCLUIDO");
        assertThat(minutosInformados(habitoId)).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 1441})
    void deveRetornar400SemCriarExecucaoNemXpQuandoMinutosForaDoLimite(int minutos) {
        UUID habitoId = criarHabitoCorrer();

        ResponseEntity<Map> response = concluirDireto(habitoId, TERCA, Map.of("minutos", minutos));

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
        assertThat(response.getBody().get("title")).isEqualTo("Minutos informados inválidos");
        assertThat(response.getBody().get("minutosMaximo")).isEqualTo(1440);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from execucao where usuario_id = ?", Long.class, usuarioId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from bloco_plano b join plano p on p.id = b.plano_id where p.usuario_id = ?",
                Long.class, usuarioId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from xp_evento where usuario_id = ?", Long.class, usuarioId)).isZero();
    }

    @Test
    void deveRetornar404AoConcluirDiretoHabitoDeOutroUsuario() {
        UUID habitoId = criarHabitoCorrer();
        String emailOutro = "hab-minutos-outro-" + UUID.randomUUID() + "@email.com";
        restTemplate.postForEntity("/usuarios", json(Map.of("nome", "Outro", "email", emailOutro, "senha", SENHA)), Map.class);
        String tokenOutro = restTemplate.postForEntity(
                "/auth/login", json(Map.of("email", emailOutro, "senha", SENHA)), Map.class)
                .getBody().get("accessToken").toString();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenOutro);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/habitos/" + habitoId + "/concluir-direto?data=" + TERCA,
                HttpMethod.POST,
                new HttpEntity<>(Map.of("minutos", 20), headers),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND);
        assertThat(response.getBody().get("title")).isEqualTo("Hábito não encontrado");
        assertThat(minutosInformados(habitoId)).isNull();
    }

    @Test
    void naoDeveConcederXpDuasVezesAoRepetirConcluirDireto() {
        UUID habitoId = criarHabitoCorrer();
        assertThat(concluirDireto(habitoId, TERCA, Map.of("minutos", 20)).getStatusCode()).isEqualTo(OK);

        ResponseEntity<Map> repetido = concluirDireto(habitoId, TERCA, Map.of("minutos", 20));

        assertThat(repetido.getStatusCode()).isEqualTo(CONFLICT);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from xp_evento where usuario_id = ?", Long.class, usuarioId)).isEqualTo(1L);
    }

    @Test
    void deveManterSequenciaMesmoComTempoInsuficiente() {
        UUID habitoId = criarHabitoCorrer();
        assertThat(concluirDireto(habitoId, TERCA.minusWeeks(1), Map.of("minutos", 30)).getStatusCode())
                .isEqualTo(OK);

        assertThat(concluirDireto(habitoId, TERCA, Map.of("minutos", 5)).getStatusCode()).isEqualTo(OK);

        assertThat(jdbcTemplate.queryForObject(
                "select streak_atual from habito where id = ?", Integer.class, habitoId)).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1441})
    void bancoDeveRejeitarMinutosInformadosForaDoLimite(int minutos) {
        UUID habitoId = criarHabitoCorrer();
        assertThat(concluirDireto(habitoId, TERCA, null).getStatusCode()).isEqualTo(OK);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "update execucao set minutos_informados = ? where usuario_id = ?", minutos, usuarioId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(minutosInformados(habitoId)).isEqualTo(30);
    }

    private ResponseEntity<Map> concluirDireto(UUID habitoId, LocalDate data, Map<String, Object> body) {
        return restTemplate.exchange(
                "/habitos/" + habitoId + "/concluir-direto?data=" + data,
                HttpMethod.POST,
                authed(body),
                Map.class);
    }

    private static int xpGanho(ResponseEntity<Map> response) {
        return ((Number) ((Map<?, ?>) response.getBody().get("xp")).get("ganhoNesteRequest")).intValue();
    }

    private Integer minutosInformados(UUID habitoId) {
        List<Integer> valores = jdbcTemplate.queryForList(
                """
                        select e.minutos_informados from execucao e
                        join bloco_plano b on b.id = e.bloco_plano_id
                        where b.habito_id = ? and e.status = 'CONCLUIDO'
                        """,
                Integer.class,
                habitoId);
        return valores.isEmpty() ? null : valores.get(0);
    }

    private UUID criarHabitoCorrer() {
        Habito habito = new Habito(
                UUID.randomUUID(),
                usuarioId,
                "Correr",
                null,
                30,
                Set.of(DiaSemana.TERCA),
                null,
                StatusHabito.ATIVO,
                0,
                0,
                AGORA,
                AGORA
        );
        return habitoRepository.save(habito).getId();
    }

    private HttpEntity<?> authed(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (body == null) {
            return new HttpEntity<>(headers);
        }
        return new HttpEntity<>(body, headers);
    }

    private static HttpEntity<Map<String, Object>> json(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(new HashMap<>(body), headers);
    }
}
