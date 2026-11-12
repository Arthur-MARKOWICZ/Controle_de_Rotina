package com.controlerotina.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
import com.controlerotina.capacidade.entity.DiaSemana;
import com.controlerotina.capacidade.repository.CapacidadeOverrideRepository;
import com.controlerotina.pendencia.entity.Espera;
import com.controlerotina.pendencia.entity.TipoRecursoEspera;
import com.controlerotina.pendencia.repository.EsperaRepository;
import com.controlerotina.projeto.entity.Projeto;
import com.controlerotina.projeto.entity.ReservaProjetoSemanal;
import com.controlerotina.projeto.entity.StatusProjeto;
import com.controlerotina.projeto.repository.ProjetoRepository;
import com.controlerotina.projeto.repository.ReservaProjetoSemanalRepository;
import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;
import com.controlerotina.tarefa.repository.TarefaRepository;

/** Geração do plano ignora tarefa bloqueada por espera própria, do pai ou do projeto. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class PlanoGeracaoEsperaIT {

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
    CapacidadeOverrideRepository capacidadeOverrideRepository;

    @Autowired
    TarefaRepository tarefaRepository;

    @Autowired
    ProjetoRepository projetoRepository;

    @Autowired
    EsperaRepository esperaRepository;

    @Autowired
    ReservaProjetoSemanalRepository reservaProjetoSemanalRepository;

    private String accessToken;
    private UUID usuarioId;

    @BeforeEach
    void cadastrarELogar() {
        jdbcTemplate.update("delete from bloco_plano");
        jdbcTemplate.update("delete from plano");
        jdbcTemplate.update("delete from espera");
        jdbcTemplate.update("delete from reserva_projeto_semanal");
        jdbcTemplate.update("delete from tarefa");
        jdbcTemplate.update("delete from projeto");
        jdbcTemplate.update("delete from refresh_token");
        jdbcTemplate.update("delete from capacidade_override");
        jdbcTemplate.update("delete from capacidade_semanal");
        jdbcTemplate.update("delete from xp_evento");
        jdbcTemplate.update("delete from usuario");

        String email = "plano-espera-" + UUID.randomUUID() + "@email.com";
        assertThat(restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Dono Espera", "email", email, "senha", SENHA)),
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
    void naoDeveGerarBlocoParaTarefaComEsperaPropriaIrresolvida() {
        salvarOverride(3);
        Tarefa bloqueada = salvarTarefa("Bloqueada", 60, null, null);
        Tarefa alvo = salvarTarefa("Alvo pendente", 60, null, null);
        Tarefa livre = salvarTarefa("Livre", 60, null, null);
        salvarEspera(TipoRecursoEspera.TAREFA, bloqueada.getId(), TipoRecursoEspera.TAREFA, alvo.getId());

        List<String> tarefasComBloco = gerarEColetarTarefas();

        assertThat(tarefasComBloco).doesNotContain(bloqueada.getId().toString());
        assertThat(tarefasComBloco).contains(livre.getId().toString(), alvo.getId().toString());
    }

    @Test
    void naoDeveGerarBlocoParaTarefaDeProjetoOrigemBloqueado() {
        salvarOverride(3);
        Projeto projeto = salvarProjeto("Projeto bloqueado");
        Tarefa doProjeto = salvarTarefa("Do projeto", 60, projeto.getId(), null);
        Tarefa alvo = salvarTarefa("Alvo do projeto", 60, null, null);
        Tarefa livre = salvarTarefa("Livre", 60, null, null);
        salvarEspera(TipoRecursoEspera.PROJETO, projeto.getId(), TipoRecursoEspera.TAREFA, alvo.getId());

        List<String> tarefasComBloco = gerarEColetarTarefas();

        assertThat(tarefasComBloco).doesNotContain(doProjeto.getId().toString());
        assertThat(tarefasComBloco).contains(livre.getId().toString());
    }

    @Test
    void naoDeveGerarBlocoParaSubtarefaDePaiBloqueado() {
        salvarOverride(3);
        Tarefa pai = salvarTarefa("Pai bloqueado", 60, null, null);
        Tarefa folha = salvarTarefa("Folha herdeira", 60, null, pai.getId());
        Tarefa alvo = salvarTarefa("Alvo do pai", 60, null, null);
        salvarEspera(TipoRecursoEspera.TAREFA, pai.getId(), TipoRecursoEspera.TAREFA, alvo.getId());

        List<String> tarefasComBloco = gerarEColetarTarefas();

        assertThat(tarefasComBloco).doesNotContain(folha.getId().toString(), pai.getId().toString());
        assertThat(tarefasComBloco).contains(alvo.getId().toString());
    }

    @Test
    void deveDevolverReservaDeProjetoBloqueadoAoPoolLivre() {
        salvarOverride(2);
        Projeto projeto = salvarProjeto("Projeto com reserva");
        Tarefa doProjeto = salvarTarefa("Reservada", 60, projeto.getId(), null);
        Tarefa alvo = salvarTarefa("Alvo da reserva", 30, null, null);
        // CANCELADA não resolve a espera e também não concorre por capacidade.
        jdbcTemplate.update("update tarefa set status = 'CANCELADA' where id = ?", alvo.getId());
        Tarefa livre = salvarTarefa("Livre longa", 120, null, null);
        salvarEspera(TipoRecursoEspera.PROJETO, projeto.getId(), TipoRecursoEspera.TAREFA, alvo.getId());
        reservaProjetoSemanalRepository.save(new ReservaProjetoSemanal(
                UUID.randomUUID(),
                usuarioId,
                projeto.getId(),
                DiaSemana.de(DIA_D),
                60,
                AGORA,
                AGORA
        ));

        List<Map<String, Object>> blocos = gerar();

        assertThat(tarefaIds(blocos)).doesNotContain(doProjeto.getId().toString());
        assertThat(minutosDa(blocos, livre.getId())).isEqualTo(120);
    }

    private List<Map<String, Object>> gerar() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/planos/gerar?data=" + DIA_D,
                HttpMethod.POST,
                autenticado(),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blocos = (List<Map<String, Object>>) response.getBody().get("blocos");
        return blocos;
    }

    private List<String> gerarEColetarTarefas() {
        return tarefaIds(gerar());
    }

    private static List<String> tarefaIds(List<Map<String, Object>> blocos) {
        return blocos.stream()
                .map(bloco -> (Map<?, ?>) bloco.get("tarefa"))
                .filter(tarefa -> tarefa != null)
                .map(tarefa -> tarefa.get("id").toString())
                .toList();
    }

    private static int minutosDa(List<Map<String, Object>> blocos, UUID tarefaId) {
        return blocos.stream()
                .filter(bloco -> {
                    Map<?, ?> tarefa = (Map<?, ?>) bloco.get("tarefa");
                    return tarefa != null && tarefaId.toString().equals(tarefa.get("id").toString());
                })
                .mapToInt(bloco -> ((Number) bloco.get("duracaoMinutos")).intValue())
                .sum();
    }

    private void salvarEspera(
            TipoRecursoEspera origemTipo,
            UUID origemId,
            TipoRecursoEspera alvoTipo,
            UUID alvoId
    ) {
        esperaRepository.save(new Espera(
                UUID.randomUUID(),
                usuarioId,
                origemTipo,
                origemId,
                alvoTipo,
                alvoId,
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

    private Projeto salvarProjeto(String nome) {
        return projetoRepository.save(new Projeto(
                UUID.randomUUID(),
                usuarioId,
                nome,
                "descricao",
                StatusProjeto.ATIVO,
                AGORA,
                AGORA
        ));
    }

    private Tarefa salvarTarefa(String titulo, int duracaoMinutos, UUID projetoId, UUID parentId) {
        Instant prazo = DIA_D.plusDays(5).atTime(12, 0)
                .atZone(ZoneId.of("America/Sao_Paulo"))
                .toInstant();
        Tarefa tarefa = new Tarefa(
                UUID.randomUUID(),
                usuarioId,
                titulo,
                "descricao",
                Prioridade.MEDIA,
                prazo,
                StatusTarefa.PENDENTE,
                duracaoMinutos,
                AGORA,
                AGORA
        );
        if (projetoId != null) {
            tarefa.definirProjetoId(projetoId, AGORA);
        }
        if (parentId != null) {
            tarefa.definirParentId(parentId);
        }
        return tarefaRepository.save(tarefa);
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
