package com.controlerotina.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;

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
import com.controlerotina.execucao.entity.Execucao;
import com.controlerotina.execucao.entity.StatusExecucao;
import com.controlerotina.execucao.repository.ExecucaoRepository;
import com.controlerotina.planner.entity.BlocoPlano;
import com.controlerotina.planner.entity.Plano;
import com.controlerotina.planner.entity.StatusPlano;
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
class PlanoPropostaIT {

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
    PlannerGeracaoService plannerGeracaoService;

    @Autowired
    PlanoRepository planoRepository;

    @Autowired
    BlocoPlanoRepository blocoPlanoRepository;

    @Autowired
    ExecucaoRepository execucaoRepository;

    private String accessToken;
    private UUID usuarioId;

    @BeforeEach
    void cadastrarELogar() {
        jdbcTemplate.update("delete from execucao");
        jdbcTemplate.update("delete from bloco_plano");
        jdbcTemplate.update("delete from plano");
        jdbcTemplate.update("delete from tarefa");
        jdbcTemplate.update("delete from refresh_token");
        jdbcTemplate.update("delete from capacidade_override");
        jdbcTemplate.update("delete from capacidade_semanal");
        jdbcTemplate.update("delete from xp_evento");
        jdbcTemplate.update("delete from usuario");

        String email = "plano-proposta-" + UUID.randomUUID() + "@email.com";
        assertThat(restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Dono Proposta", "email", email, "senha", SENHA)),
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
    void t1ReduzirCapacidadeDeDiaAprovadoGeraRascunhoSemMutarAprovado() {
        CapacidadeOverride override = capacidadeOverrideRepository.save(new CapacidadeOverride(
                UUID.randomUUID(), usuarioId, DIA_D, 4, true, AGORA, AGORA));
        salvarTarefa("Tarefa", Prioridade.MEDIA, 60, AGORA);
        Plano aprovado = plannerGeracaoService.gerar(usuarioId, DIA_D);
        assertThat(restTemplate.exchange(
                "/planos/" + aprovado.getId() + "/aprovar",
                HttpMethod.POST,
                autenticado(),
                Map.class).getStatusCode()).isEqualTo(OK);

        ResponseEntity<Map> patch = restTemplate.exchange(
                "/capacidades/overrides/" + override.getId(),
                HttpMethod.PATCH,
                autenticadoJson(Map.of("horas", 1)),
                Map.class);
        assertThat(patch.getStatusCode()).isEqualTo(OK);

        Plano corrente = planoRepository
                .findFirstByUsuarioIdAndDataAndStatusInOrderByVersaoDesc(
                        usuarioId, DIA_D, List.of(StatusPlano.RASCUNHO, StatusPlano.APROVADO))
                .orElseThrow();
        assertThat(corrente.getStatus()).isEqualTo(StatusPlano.RASCUNHO);
        assertThat(corrente.getVersao()).isGreaterThan(aprovado.getVersao());
        assertThat(corrente.getPropostaId()).isNotNull();

        Plano aprovadoPersistido = planoRepository.findById(aprovado.getId()).orElseThrow();
        assertThat(aprovadoPersistido.getStatus()).isEqualTo(StatusPlano.APROVADO);
        assertThat(aprovadoPersistido.getVersao()).isEqualTo(aprovado.getVersao());
    }

    @Test
    void t2AceitarPreservaBlocosComExecucaoConcluido() {
        capacidadeOverrideRepository.save(new CapacidadeOverride(
                UUID.randomUUID(), usuarioId, DIA_D, 4, true, AGORA, AGORA));
        Tarefa t1 = salvarTarefa("Concluida", Prioridade.ALTA, 60, AGORA);
        Tarefa t2 = salvarTarefa("Pendente", Prioridade.MEDIA, 60, AGORA);
        Plano aprovado = plannerGeracaoService.gerar(usuarioId, DIA_D);
        assertThat(restTemplate.exchange(
                "/planos/" + aprovado.getId() + "/aprovar",
                HttpMethod.POST,
                autenticado(),
                Map.class).getStatusCode()).isEqualTo(OK);

        List<BlocoPlano> blocosAprovados = blocoPlanoRepository.findByPlanoIdOrderByOrdemAsc(aprovado.getId());
        BlocoPlano blocoConcluido = blocosAprovados.stream()
                .filter(b -> b.getTarefaId().equals(t1.getId()))
                .findFirst()
                .orElseThrow();
        execucaoRepository.save(new Execucao(
                UUID.randomUUID(),
                usuarioId,
                blocoConcluido.getId(),
                StatusExecucao.CONCLUIDO,
                AGORA,
                AGORA,
                null,
                AGORA,
                AGORA
        ));

        Plano proposta = plannerGeracaoService.gerarProposta(usuarioId, DIA_D);
        UUID propostaId = proposta.getPropostaId();

        ResponseEntity<Map> aceitar = restTemplate.exchange(
                "/planos/propostas/" + propostaId + "/aceitar",
                HttpMethod.POST,
                autenticado(),
                Map.class);
        assertThat(aceitar.getStatusCode()).isEqualTo(OK);
        assertThat(aceitar.getBody().get("status")).isEqualTo("APROVADO");
        assertThat(aceitar.getBody().get("propostaPendente")).isEqualTo(false);

        UUID novoPlanoId = UUID.fromString(aceitar.getBody().get("id").toString());
        List<BlocoPlano> blocosNovos = blocoPlanoRepository.findByPlanoIdOrderByOrdemAsc(novoPlanoId);
        assertThat(blocosNovos).anyMatch(b -> b.getTarefaId().equals(t1.getId()));
        assertThat(blocosNovos).anyMatch(b -> b.getTarefaId().equals(t2.getId()));

        BlocoPlano blocoT1Novo = blocosNovos.stream()
                .filter(b -> b.getTarefaId().equals(t1.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(execucaoRepository.findByBlocoPlanoId(blocoT1Novo.getId()))
                .isPresent()
                .get()
                .extracting(Execucao::getStatus)
                .isEqualTo(StatusExecucao.CONCLUIDO);
    }

    @Test
    void t4AprovarRascunhoDePropostaPreservaConcluidosEDefineVigenteUnico() {
        // BE-010 / Opção B: /aprovar em rascunho de proposta deve equivaler ao aceite
        // (preservar concluídos + um APROVADO vigente). Vermelho até BE-001.
        capacidadeOverrideRepository.save(new CapacidadeOverride(
                UUID.randomUUID(), usuarioId, DIA_D, 4, true, AGORA, AGORA));
        Tarefa t1 = salvarTarefa("Concluida", Prioridade.ALTA, 60, AGORA);
        Tarefa t2 = salvarTarefa("Pendente", Prioridade.MEDIA, 60, AGORA);
        Plano aprovado = plannerGeracaoService.gerar(usuarioId, DIA_D);
        assertThat(restTemplate.exchange(
                "/planos/" + aprovado.getId() + "/aprovar",
                HttpMethod.POST,
                autenticado(),
                Map.class).getStatusCode()).isEqualTo(OK);

        List<BlocoPlano> blocosAprovados = blocoPlanoRepository.findByPlanoIdOrderByOrdemAsc(aprovado.getId());
        BlocoPlano blocoConcluido = blocosAprovados.stream()
                .filter(b -> b.getTarefaId().equals(t1.getId()))
                .findFirst()
                .orElseThrow();
        execucaoRepository.save(new Execucao(
                UUID.randomUUID(),
                usuarioId,
                blocoConcluido.getId(),
                StatusExecucao.CONCLUIDO,
                AGORA,
                AGORA,
                null,
                AGORA,
                AGORA
        ));

        Plano rascunhoProposta = plannerGeracaoService.gerarProposta(usuarioId, DIA_D);
        assertThat(rascunhoProposta.getPropostaId()).isNotNull();
        assertThat(rascunhoProposta.getStatus()).isEqualTo(StatusPlano.RASCUNHO);

        ResponseEntity<Map> aprovar = restTemplate.exchange(
                "/planos/" + rascunhoProposta.getId() + "/aprovar",
                HttpMethod.POST,
                autenticado(),
                Map.class);
        assertThat(aprovar.getStatusCode()).isEqualTo(OK);
        assertThat(aprovar.getBody().get("status")).isEqualTo("APROVADO");
        assertThat(aprovar.getBody().get("propostaPendente")).isEqualTo(false);

        UUID novoPlanoId = UUID.fromString(aprovar.getBody().get("id").toString());
        assertThat(novoPlanoId).isEqualTo(rascunhoProposta.getId());

        Plano vigente = planoRepository
                .findFirstByUsuarioIdAndDataAndStatusOrderByVersaoDesc(
                        usuarioId, DIA_D, StatusPlano.APROVADO)
                .orElseThrow();
        assertThat(vigente.getId()).isEqualTo(novoPlanoId);
        assertThat(vigente.getVersao()).isGreaterThan(aprovado.getVersao());

        List<BlocoPlano> blocosNovos = blocoPlanoRepository.findByPlanoIdOrderByOrdemAsc(novoPlanoId);
        assertThat(blocosNovos).anyMatch(b -> b.getTarefaId().equals(t1.getId()));
        assertThat(blocosNovos).anyMatch(b -> b.getTarefaId().equals(t2.getId()));

        BlocoPlano blocoT1Novo = blocosNovos.stream()
                .filter(b -> b.getTarefaId().equals(t1.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(blocoT1Novo.getId()).isNotEqualTo(blocoConcluido.getId());
        assertThat(execucaoRepository.findByBlocoPlanoId(blocoT1Novo.getId()))
                .isPresent()
                .get()
                .extracting(Execucao::getStatus)
                .isEqualTo(StatusExecucao.CONCLUIDO);
        assertThat(execucaoRepository.findByBlocoPlanoId(blocoConcluido.getId())).isEmpty();
    }

    @Test
    void t3DescartarDeixaVigenteIgual() {
        CapacidadeOverride override = capacidadeOverrideRepository.save(new CapacidadeOverride(
                UUID.randomUUID(), usuarioId, DIA_D, 4, true, AGORA, AGORA));
        salvarTarefa("Tarefa", Prioridade.MEDIA, 60, AGORA);
        Plano aprovado = plannerGeracaoService.gerar(usuarioId, DIA_D);
        assertThat(restTemplate.exchange(
                "/planos/" + aprovado.getId() + "/aprovar",
                HttpMethod.POST,
                autenticado(),
                Map.class).getStatusCode()).isEqualTo(OK);

        assertThat(restTemplate.exchange(
                "/capacidades/overrides/" + override.getId(),
                HttpMethod.PATCH,
                autenticadoJson(Map.of("horas", 1)),
                Map.class).getStatusCode()).isEqualTo(OK);

        Plano rascunho = planoRepository
                .findFirstByUsuarioIdAndDataAndStatusInOrderByVersaoDesc(
                        usuarioId, DIA_D, List.of(StatusPlano.RASCUNHO, StatusPlano.APROVADO))
                .orElseThrow();
        assertThat(rascunho.getStatus()).isEqualTo(StatusPlano.RASCUNHO);
        UUID propostaId = rascunho.getPropostaId();

        ResponseEntity<Void> descartar = restTemplate.exchange(
                "/planos/propostas/" + propostaId + "/descartar",
                HttpMethod.POST,
                autenticado(),
                Void.class);
        assertThat(descartar.getStatusCode()).isEqualTo(NO_CONTENT);

        Plano corrente = planoRepository
                .findFirstByUsuarioIdAndDataAndStatusInOrderByVersaoDesc(
                        usuarioId, DIA_D, List.of(StatusPlano.RASCUNHO, StatusPlano.APROVADO))
                .orElseThrow();
        assertThat(corrente.getId()).isEqualTo(aprovado.getId());
        assertThat(corrente.getStatus()).isEqualTo(StatusPlano.APROVADO);

        Plano descartado = planoRepository.findById(rascunho.getId()).orElseThrow();
        assertThat(descartado.getStatus()).isEqualTo(StatusPlano.DESCARTADO);
    }

    private Tarefa salvarTarefa(String titulo, Prioridade prioridade, int duracaoMinutos, Instant createdAt) {
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
                duracaoMinutos,
                createdAt,
                createdAt
        ));
    }

    private HttpEntity<Void> autenticado() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return new HttpEntity<>(headers);
    }

    private HttpEntity<Map<String, ?>> autenticadoJson(Map<String, ?> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(new HashMap<>(body), headers);
    }

    private static HttpEntity<Map<String, ?>> json(Map<String, ?> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(new HashMap<>(body), headers);
    }
}
