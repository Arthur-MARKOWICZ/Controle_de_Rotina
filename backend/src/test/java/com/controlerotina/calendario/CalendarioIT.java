package com.controlerotina.calendario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.time.Instant;
import java.time.LocalDate;
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

import com.controlerotina.capacidade.entity.CapacidadeSemanal;
import com.controlerotina.capacidade.entity.DiaSemana;
import com.controlerotina.capacidade.repository.CapacidadeSemanalRepository;
import com.controlerotina.planner.entity.BlocoPlano;
import com.controlerotina.planner.entity.Plano;
import com.controlerotina.planner.entity.StatusPlano;
import com.controlerotina.planner.repository.BlocoPlanoRepository;
import com.controlerotina.planner.repository.PlanoRepository;
import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;
import com.controlerotina.tarefa.repository.TarefaRepository;
import com.controlerotina.usuario.entity.Usuario;
import com.controlerotina.usuario.repository.UsuarioRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class CalendarioIT {

    private static final String SENHA = "Senha@123";
    private static final Instant AGORA = Instant.parse("2026-10-27T15:00:00Z");
    private static final LocalDate SEGUNDA = LocalDate.of(2026, 10, 26);
    private static final LocalDate TERCA = LocalDate.of(2026, 10, 27);
    private static final LocalDate DOMINGO = LocalDate.of(2026, 11, 1);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    TarefaRepository tarefaRepository;

    @Autowired
    PlanoRepository planoRepository;

    @Autowired
    BlocoPlanoRepository blocoPlanoRepository;

    @Autowired
    CapacidadeSemanalRepository capacidadeSemanalRepository;

    private String accessToken;
    private UUID usuarioId;

    @BeforeEach
    void limparELogar() {
        jdbcTemplate.update("delete from xp_evento");
        jdbcTemplate.update("delete from execucao");
        jdbcTemplate.update("delete from bloco_plano");
        jdbcTemplate.update("delete from plano");
        jdbcTemplate.update("delete from tarefa");
        jdbcTemplate.update("delete from refresh_token");
        jdbcTemplate.update("delete from capacidade_override");
        jdbcTemplate.update("delete from capacidade_semanal");
        jdbcTemplate.update("delete from usuario");

        String email = "cal-" + UUID.randomUUID() + "@email.com";
        assertThat(restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Cal", "email", email, "senha", SENHA)),
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
    void t5_semanaContiguaComDiaSemPlanoEZeros() {
        capacidadeSemanalRepository.save(new CapacidadeSemanal(
                UUID.randomUUID(), usuarioId, DiaSemana.TERCA, 4, true, AGORA, AGORA
        ));

        UUID tarefaId = UUID.randomUUID();
        tarefaRepository.save(new Tarefa(
                tarefaId, usuarioId, "Estudar Spring", "d", Prioridade.ALTA,
                Instant.parse("2026-10-27T23:00:00Z"), StatusTarefa.PENDENTE, 60, AGORA, AGORA
        ));

        UUID planoAprovado = UUID.randomUUID();
        planoRepository.save(new Plano(
                planoAprovado, usuarioId, TERCA, 1, StatusPlano.APROVADO,
                false, null, null, AGORA, AGORA, AGORA
        ));
        UUID blocoId = UUID.randomUUID();
        blocoPlanoRepository.save(new BlocoPlano(
                blocoId, planoAprovado, tarefaId, 60, 1, AGORA, AGORA
        ));

        UUID planoRascunho = UUID.randomUUID();
        planoRepository.save(new Plano(
                planoRascunho, usuarioId, TERCA, 2, StatusPlano.RASCUNHO,
                false, null, null, AGORA, AGORA, AGORA
        ));

        ResponseEntity<Map> response = restTemplate.exchange(
                "/calendario?dataDe=" + SEGUNDA + "&dataAte=" + DOMINGO,
                HttpMethod.GET,
                autenticado(),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        Map body = response.getBody();
        Map periodo = (Map) body.get("periodo");
        assertThat(periodo.get("inicio")).isEqualTo(SEGUNDA.toString());
        assertThat(periodo.get("fim")).isEqualTo(DOMINGO.toString());

        List<Map> dias = (List<Map>) body.get("dias");
        assertThat(dias).hasSize(7);
        assertThat(dias).extracting(d -> d.get("data"))
                .containsExactly(
                        "2026-10-26", "2026-10-27", "2026-10-28", "2026-10-29",
                        "2026-10-30", "2026-10-31", "2026-11-01"
                );

        Map segunda = dias.get(0);
        assertThat(segunda.get("plano")).isNull();
        assertThat(segunda.get("statusPlano")).isNull();
        assertThat(segunda.get("minutosAlocados")).isEqualTo(0);
        assertThat(segunda.get("sobrecarga")).isEqualTo(false);
        assertThat((List<?>) segunda.get("tarefasComPrazo")).isEmpty();

        Map terca = dias.get(1);
        assertThat(terca.get("statusPlano")).isEqualTo("APROVADO");
        assertThat(terca.get("capacidadeMinutos")).isEqualTo(240);
        assertThat(terca.get("origemCapacidade")).isEqualTo("SEMANAL");
        assertThat(terca.get("minutosAlocados")).isEqualTo(60);
        Map plano = (Map) terca.get("plano");
        assertThat(plano.get("id")).isEqualTo(planoAprovado.toString());
        assertThat(plano.get("versao")).isEqualTo(1);
        List<Map> blocos = (List<Map>) plano.get("blocos");
        assertThat(blocos).hasSize(1);
        assertThat(blocos.get(0).get("titulo")).isEqualTo("Estudar Spring");
        assertThat(blocos.get(0).get("id")).isEqualTo(blocoId.toString());
    }

    @Test
    void t6_semToken401EUsuarioNaoVeDadosDeOutro() {
        ResponseEntity<Map> semToken = restTemplate.getForEntity(
                "/calendario?dataDe=" + SEGUNDA + "&dataAte=" + DOMINGO,
                Map.class);
        assertThat(semToken.getStatusCode()).isEqualTo(UNAUTHORIZED);

        UUID tarefaId = UUID.randomUUID();
        tarefaRepository.save(new Tarefa(
                tarefaId, usuarioId, "Minha", "d", Prioridade.MEDIA,
                AGORA, StatusTarefa.PENDENTE, 60, AGORA, AGORA
        ));
        UUID meuPlano = UUID.randomUUID();
        planoRepository.save(new Plano(
                meuPlano, usuarioId, TERCA, 1, StatusPlano.APROVADO,
                false, null, null, AGORA, AGORA, AGORA
        ));
        blocoPlanoRepository.save(new BlocoPlano(
                UUID.randomUUID(), meuPlano, tarefaId, 60, 1, AGORA, AGORA
        ));

        UUID outro = UUID.randomUUID();
        usuarioRepository.save(new Usuario(
                outro, "outro-cal-" + outro + "@email.com", "$2a$10$hash", "Outro", AGORA, AGORA
        ));
        UUID tarefaOutro = UUID.randomUUID();
        tarefaRepository.save(new Tarefa(
                tarefaOutro, outro, "Do outro", "d", Prioridade.ALTA,
                AGORA, StatusTarefa.PENDENTE, 90, AGORA, AGORA
        ));
        UUID planoOutro = UUID.randomUUID();
        planoRepository.save(new Plano(
                planoOutro, outro, TERCA, 1, StatusPlano.APROVADO,
                false, null, null, AGORA, AGORA, AGORA
        ));
        blocoPlanoRepository.save(new BlocoPlano(
                UUID.randomUUID(), planoOutro, tarefaOutro, 90, 1, AGORA, AGORA
        ));

        ResponseEntity<Map> response = restTemplate.exchange(
                "/calendario?dataDe=" + TERCA + "&dataAte=" + TERCA,
                HttpMethod.GET,
                autenticado(),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        List<Map> dias = (List<Map>) response.getBody().get("dias");
        assertThat(dias).hasSize(1);
        Map plano = (Map) dias.get(0).get("plano");
        assertThat(plano.get("id")).isEqualTo(meuPlano.toString());
        List<Map> blocos = (List<Map>) plano.get("blocos");
        assertThat(blocos).hasSize(1);
        assertThat(blocos.get(0).get("titulo")).isEqualTo("Minha");
        assertThat(blocos.get(0).get("titulo")).isNotEqualTo("Do outro");
    }

    private HttpEntity<Void> autenticado() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return new HttpEntity<>(headers);
    }

    private static HttpEntity<Map<String, Object>> json(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
