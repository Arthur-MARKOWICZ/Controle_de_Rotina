package com.controlerotina.habito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

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

import com.controlerotina.capacidade.entity.CapacidadeOverride;
import com.controlerotina.capacidade.entity.DiaSemana;
import com.controlerotina.capacidade.repository.CapacidadeOverrideRepository;
import com.controlerotina.habito.entity.Habito;
import com.controlerotina.habito.entity.StatusHabito;
import com.controlerotina.habito.repository.HabitoRepository;
import com.controlerotina.planner.entity.BlocoPlano;
import com.controlerotina.planner.entity.Plano;
import com.controlerotina.planner.entity.StatusPlano;
import com.controlerotina.planner.job.PlannerGeracaoDiariaJob;
import com.controlerotina.planner.repository.BlocoPlanoRepository;
import com.controlerotina.planner.repository.PlanoRepository;
import com.controlerotina.planner.service.PlannerGeracaoService;
import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;
import com.controlerotina.tarefa.repository.TarefaRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class HabitoPlanoIT {

    private static final String SENHA = "Senha@123";
    /** Terça-feira. */
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
    CapacidadeOverrideRepository capacidadeOverrideRepository;

    @Autowired
    TarefaRepository tarefaRepository;

    @Autowired
    HabitoRepository habitoRepository;

    @Autowired
    PlannerGeracaoService plannerGeracaoService;

    @Autowired
    PlannerGeracaoDiariaJob plannerGeracaoDiariaJob;

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
        jdbcTemplate.update("delete from tarefa");
        jdbcTemplate.update("delete from refresh_token");
        jdbcTemplate.update("delete from capacidade_override");
        jdbcTemplate.update("delete from capacidade_semanal");
        jdbcTemplate.update("delete from xp_evento");
        jdbcTemplate.update("delete from usuario");

        String email = "habito-plano-" + UUID.randomUUID() + "@email.com";
        assertThat(restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Dono Habito Plano", "email", email, "senha", SENHA)),
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
    void t14_gerarDeveIncluirHabitoDoWeekday() {
        salvarOverride(2);
        Habito habito = salvarHabito("Corrida", Set.of(DiaSemana.TERCA), 45);

        Plano plano = plannerGeracaoService.gerar(usuarioId, DIA_D);

        List<BlocoPlano> blocos = blocoPlanoRepository.findByPlanoIdOrderByOrdemAsc(plano.getId());
        assertThat(blocos).anyMatch(b -> habito.getId().equals(b.getHabitoId()));
    }

    @Test
    void t14_jobPulaQuandoJaAprovado() {
        salvarOverride(2);
        salvarHabito("Yoga", Set.of(DiaSemana.TERCA), 30);
        Plano gerado = plannerGeracaoService.gerar(usuarioId, DIA_D);
        jdbcTemplate.update(
                "update plano set status = ? where id = ?",
                StatusPlano.APROVADO.name(),
                gerado.getId());

        plannerGeracaoDiariaJob.gerarPlanosPara(DIA_D);

        Integer versoes = jdbcTemplate.queryForObject(
                "select count(*) from plano where usuario_id = ? and data = ?",
                Integer.class,
                usuarioId,
                DIA_D);
        assertThat(versoes).isEqualTo(1);
    }

    @Test
    void t12_getPlanoComOrigemHabitoEItensNaoAlocadosSobPressao() {
        salvarOverride(1);
        Habito cabivel = salvarHabito("Meditação", Set.of(DiaSemana.TERCA), 30);
        Habito omitido = salvarHabito("Estudo longo", Set.of(DiaSemana.TERCA), 60);

        ResponseEntity<Map> gerado = restTemplate.exchange(
                "/planos/gerar?data=" + DIA_D,
                HttpMethod.POST,
                autenticado(),
                Map.class);

        assertThat(gerado.getStatusCode()).isEqualTo(CREATED);
        assertThat(gerado.getBody().get("temConflito")).isEqualTo(true);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blocos = (List<Map<String, Object>>) gerado.getBody().get("blocos");
        assertThat(blocos).isNotEmpty();
        assertThat(blocos).anySatisfy(b -> {
            assertThat(b.get("origem")).isEqualTo("HABITO");
            assertThat(b.get("habitoId")).isEqualTo(cabivel.getId().toString());
            assertThat(b.get("habito")).isNotNull();
            @SuppressWarnings("unchecked")
            Map<String, Object> habitoResumo = (Map<String, Object>) b.get("habito");
            assertThat(habitoResumo.get("diasSemana")).isNotNull();
        });

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> omitidos =
                (List<Map<String, Object>>) gerado.getBody().get("itensNaoAlocados");
        assertThat(omitidos).extracting(m -> m.get("id").toString())
                .contains(omitido.getId().toString());
        assertThat(omitidos).anySatisfy(m -> {
            assertThat(m.get("tipo")).isEqualTo("HABITO");
            assertThat(m.get("motivo")).isEqualTo("CAPACIDADE");
        });
    }

    @Test
    void correcoes_t6_getDevolveSnapshotPersistidoSemInventarTarefaDeOutroDia() {
        salvarOverride(1);
        Habito omitido = salvarHabito("Estudo longo", Set.of(DiaSemana.TERCA), 60);
        // Capacidade 1h em D; tarefa de 90 min com prazo em D+1 deve ir para a janela (não omitida em D).
        capacidadeOverrideRepository.save(new CapacidadeOverride(
                UUID.randomUUID(),
                usuarioId,
                DIA_D.plusDays(1),
                2,
                true,
                AGORA,
                AGORA
        ));
        Tarefa tarefaAmanha = tarefaRepository.save(new Tarefa(
                UUID.randomUUID(),
                usuarioId,
                "Tarefa D+1",
                "desc",
                Prioridade.ALTA,
                DIA_D.plusDays(1).atTime(18, 0)
                        .atZone(java.time.ZoneId.of("America/Sao_Paulo"))
                        .toInstant(),
                StatusTarefa.PENDENTE,
                90,
                AGORA,
                AGORA
        ));

        ResponseEntity<Map> gerado = restTemplate.exchange(
                "/planos/gerar?data=" + DIA_D,
                HttpMethod.POST,
                autenticado(),
                Map.class);
        assertThat(gerado.getStatusCode()).isEqualTo(CREATED);
        String planoId = gerado.getBody().get("id").toString();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> omitidosGeracao =
                (List<Map<String, Object>>) gerado.getBody().get("itensNaoAlocados");

        ResponseEntity<Map> consultado = restTemplate.exchange(
                "/planos/" + planoId,
                HttpMethod.GET,
                autenticado(),
                Map.class);
        assertThat(consultado.getStatusCode()).isEqualTo(OK);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> omitidosGet =
                (List<Map<String, Object>>) consultado.getBody().get("itensNaoAlocados");
        assertThat(omitidosGet).isEqualTo(omitidosGeracao);
        assertThat(omitidosGet).extracting(m -> m.get("id").toString())
                .contains(omitido.getId().toString())
                .doesNotContain(tarefaAmanha.getId().toString());
    }

    @Test
    void correcoes_t7_getHabitosMantemStreakAposCumprimentoComDiaAnteriorFaltoso() {
        LocalDate hoje = LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"));
        DiaSemana diaHoje = DiaSemana.de(hoje);
        DiaSemana diaAnterior = DiaSemana.de(hoje.minusDays(1));
        // Garante pelo menos dois dias na agenda (hoje e ontem).
        Habito habito = salvarHabito(
                "Streak GET",
                Set.of(diaAnterior, diaHoje),
                30
        );

        ResponseEntity<Map> conclusao = restTemplate.exchange(
                "/habitos/" + habito.getId() + "/concluir-direto?data=" + hoje,
                HttpMethod.POST,
                autenticado(),
                Map.class);
        assertThat(conclusao.getStatusCode()).isEqualTo(OK);

        Integer streakDb = jdbcTemplate.queryForObject(
                "select streak_atual from habito where id = ?", Integer.class, habito.getId());
        assertThat(streakDb).isEqualTo(1);

        ResponseEntity<Map> lista = restTemplate.exchange(
                "/habitos",
                HttpMethod.GET,
                autenticado(),
                Map.class);
        assertThat(lista.getStatusCode()).isEqualTo(OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) lista.getBody().get("content");
        assertThat(content).anySatisfy(h -> {
            assertThat(h.get("id").toString()).isEqualTo(habito.getId().toString());
            assertThat(h.get("streakAtual")).isEqualTo(1);
        });
    }

    @Test
    void t11_deveAdicionarBlocoComHabitoId() {
        salvarOverride(3);
        Plano plano = plannerGeracaoService.gerar(usuarioId, DIA_D);
        Habito habito = salvarHabito("Leitura", Set.of(DiaSemana.TERCA), 30);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/planos/" + plano.getId() + "/blocos",
                HttpMethod.POST,
                autenticadoJson(Map.of("habitoId", habito.getId().toString())),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blocos = (List<Map<String, Object>>) response.getBody().get("blocos");
        assertThat(blocos).anySatisfy(b -> {
            assertThat(b.get("origem")).isEqualTo("HABITO");
            assertThat(b.get("habitoId")).isEqualTo(habito.getId().toString());
            assertThat(b.get("duracaoMinutos")).isEqualTo(30);
        });
    }

    @Test
    void t11_xorEDuplicadoRetornamErro() {
        salvarOverride(3);
        Plano plano = plannerGeracaoService.gerar(usuarioId, DIA_D);
        Habito habito = salvarHabito("Duplicado", Set.of(DiaSemana.TERCA), 30);
        Tarefa tarefa = salvarTarefa("T", 30);

        assertThat(restTemplate.exchange(
                "/planos/" + plano.getId() + "/blocos",
                HttpMethod.POST,
                autenticadoJson(Map.of()),
                Map.class).getStatusCode()).isEqualTo(BAD_REQUEST);

        Map<String, Object> ambos = new HashMap<>();
        ambos.put("tarefaId", tarefa.getId().toString());
        ambos.put("habitoId", habito.getId().toString());
        assertThat(restTemplate.exchange(
                "/planos/" + plano.getId() + "/blocos",
                HttpMethod.POST,
                autenticadoJson(ambos),
                Map.class).getStatusCode()).isEqualTo(BAD_REQUEST);

        assertThat(restTemplate.exchange(
                "/planos/" + plano.getId() + "/blocos",
                HttpMethod.POST,
                autenticadoJson(Map.of("habitoId", habito.getId().toString())),
                Map.class).getStatusCode()).isEqualTo(CREATED);

        assertThat(restTemplate.exchange(
                "/planos/" + plano.getId() + "/blocos",
                HttpMethod.POST,
                autenticadoJson(Map.of("habitoId", habito.getId().toString())),
                Map.class).getStatusCode()).isEqualTo(CONFLICT);
    }

    @Test
    void t13_concluirBlocoHabitoCreditaXpAtualizaStreakSemMutarTarefa() {
        salvarOverride(2);
        Habito habito = salvarHabito("Streak", Set.of(DiaSemana.TERCA), 30);
        jdbcTemplate.update(
                "update habito set streak_atual = 2, melhor_streak = 2 where id = ?",
                habito.getId());

        Plano plano = plannerGeracaoService.gerar(usuarioId, DIA_D);
        jdbcTemplate.update(
                "update plano set status = ? where id = ?",
                StatusPlano.APROVADO.name(),
                plano.getId());

        BlocoPlano bloco = blocoPlanoRepository.findByPlanoIdOrderByOrdemAsc(plano.getId()).stream()
                .filter(b -> habito.getId().equals(b.getHabitoId()))
                .findFirst()
                .orElseThrow();

        Tarefa tarefaControle = salvarTarefa("Nao mutar", 60);
        String statusAntes = jdbcTemplate.queryForObject(
                "select status from tarefa where id = ?", String.class, tarefaControle.getId());

        ResponseEntity<Map> inicio = restTemplate.exchange(
                "/execucoes/iniciar",
                HttpMethod.POST,
                autenticadoJson(Map.of("blocoId", bloco.getId().toString())),
                Map.class);
        assertThat(inicio.getStatusCode()).isEqualTo(CREATED);
        UUID execucaoId = UUID.fromString(inicio.getBody().get("id").toString());

        jdbcTemplate.update(
                "update execucao set iniciado_em = ? where id = ?",
                java.sql.Timestamp.from(AGORA.minusSeconds(3600)),
                execucaoId);

        ResponseEntity<Map> conclusao = restTemplate.exchange(
                "/execucoes/" + execucaoId + "/concluir",
                HttpMethod.POST,
                autenticado(),
                Map.class);
        assertThat(conclusao.getStatusCode()).isEqualTo(OK);

        Integer xpEventosTarefa = jdbcTemplate.queryForObject(
                "select count(*) from xp_evento where tipo = 'TAREFA_FINALIZADA'",
                Integer.class);
        assertThat(xpEventosTarefa).isZero();

        Integer xpBloco = jdbcTemplate.queryForObject(
                "select count(*) from xp_evento where tipo = 'BLOCO_CONCLUIDO' and origem_id = ?",
                Integer.class,
                execucaoId);
        assertThat(xpBloco).isEqualTo(1);

        Map<String, Object> habitoRow = jdbcTemplate.queryForMap(
                "select streak_atual, melhor_streak from habito where id = ?", habito.getId());
        assertThat(habitoRow.get("streak_atual")).isEqualTo(1);
        assertThat(habitoRow.get("melhor_streak")).isEqualTo(2);

        String statusDepois = jdbcTemplate.queryForObject(
                "select status from tarefa where id = ?", String.class, tarefaControle.getId());
        assertThat(statusDepois).isEqualTo(statusAntes);
    }

    private Habito salvarHabito(String titulo, Set<DiaSemana> dias, int duracao) {
        Habito habito = new Habito(
                UUID.randomUUID(),
                usuarioId,
                titulo,
                null,
                duracao,
                dias,
                null,
                StatusHabito.ATIVO,
                0,
                0,
                AGORA,
                AGORA
        );
        return habitoRepository.save(habito);
    }

    private Tarefa salvarTarefa(String titulo, int duracao) {
        return tarefaRepository.save(new Tarefa(
                UUID.randomUUID(),
                usuarioId,
                titulo,
                "desc",
                Prioridade.MEDIA,
                AGORA.plusSeconds(86_400),
                StatusTarefa.PENDENTE,
                duracao,
                AGORA,
                AGORA
        ));
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
}
