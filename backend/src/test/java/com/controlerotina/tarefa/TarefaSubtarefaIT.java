package com.controlerotina.tarefa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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

import com.controlerotina.historico.PeriodoBRT;
import com.controlerotina.tarefa.job.TarefaAtrasoJob;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class TarefaSubtarefaIT {

    private static final String SENHA = "Senha@123";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    TarefaAtrasoJob tarefaAtrasoJob;

    private String accessToken;
    private UUID usuarioId;

    @BeforeEach
    void cadastrarELogar() {
        String email = "subtarefa-" + UUID.randomUUID() + "@email.com";
        assertThat(restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Dono Subtarefa", "email", email, "senha", SENHA)),
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
    void deveExporColunaParentIdNullableComFkEIndice() {
        Boolean nullable = jdbcTemplate.queryForObject(
                """
                select is_nullable = 'YES'
                  from information_schema.columns
                 where table_name = 'tarefa' and column_name = 'parent_id'
                """,
                Boolean.class);
        assertThat(nullable).isTrue();

        Integer fks = jdbcTemplate.queryForObject(
                """
                select count(*)
                  from information_schema.table_constraints
                 where table_name = 'tarefa'
                   and constraint_type = 'FOREIGN KEY'
                   and constraint_name = 'fk_tarefa_parent'
                """,
                Integer.class);
        assertThat(fks).isEqualTo(1);

        Boolean indice = jdbcTemplate.queryForObject(
                """
                select exists (
                    select 1
                      from pg_indexes
                     where tablename = 'tarefa'
                       and indexname = 'idx_tarefa_usuario_parent'
                )
                """,
                Boolean.class);
        assertThat(indice).isTrue();
    }

    @Test
    void deveCriarFilhoDeRaizComParentId() {
        UUID paiId = criarTarefa("Estudar Security");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/tarefas",
                HttpMethod.POST,
                autenticadoJson(filhoDe(paiId, "Ler OWASP")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("parentId")).isEqualTo(paiId.toString());
        assertThat(response.getBody().get("parentTitulo")).isEqualTo("Estudar Security");
        assertThat(response.getBody().get("temFilhos")).isEqualTo(false);
        assertThat(response.getBody().get("status")).isEqualTo("PENDENTE");

        UUID filhoId = UUID.fromString(response.getBody().get("id").toString());
        assertThat(jdbcTemplate.queryForObject(
                "select parent_id from tarefa where id = ?",
                UUID.class,
                filhoId)).isEqualTo(paiId);
    }

    @Test
    void deveExporHierarquiaVaziaNaRaiz() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/tarefas",
                HttpMethod.POST,
                autenticadoJson(Map.of("titulo", "Raiz")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        assertThat(response.getBody().get("parentId")).isNull();
        assertThat(response.getBody().get("parentTitulo")).isNull();
        assertThat(response.getBody().get("temFilhos")).isEqualTo(false);
    }

    @Test
    void deveRejeitarNetoCom400() {
        UUID paiId = criarTarefa("Pai");
        UUID filhoId = criarFilho(paiId, "Filho");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/tarefas",
                HttpMethod.POST,
                autenticadoJson(filhoDe(filhoId, "Neto")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from tarefa where parent_id = ?",
                Integer.class,
                filhoId)).isZero();
    }

    @Test
    void deveRetornar404QuandoPaiEDeOutroUsuario() {
        String tokenOutro = tokenDeNovoUsuario();
        UUID paiOutro = criarTarefaCom(tokenOutro, "Pai alheio");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/tarefas",
                HttpMethod.POST,
                autenticadoJson(filhoDe(paiOutro, "Intruso")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from tarefa where parent_id = ?",
                Integer.class,
                paiOutro)).isZero();
    }

    @Test
    void deveHerdarProjetoQuandoOmitido() {
        UUID projetoId = criarProjeto("TCC");
        UUID paiId = criarTarefaNoProjeto("Pai", projetoId);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/tarefas",
                HttpMethod.POST,
                autenticadoJson(filhoDe(paiId, "Filho sem projeto")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        assertThat(response.getBody().get("projetoId")).isEqualTo(projetoId.toString());
        assertThat(jdbcTemplate.queryForObject(
                "select projeto_id from tarefa where id = ?",
                UUID.class,
                UUID.fromString(response.getBody().get("id").toString()))).isEqualTo(projetoId);
    }

    @Test
    void deveUsarProjetoExplicitoNoFilho() {
        UUID projetoPai = criarProjeto("PaiProj");
        UUID projetoFilho = criarProjeto("FilhoProj");
        UUID paiId = criarTarefaNoProjeto("Pai", projetoPai);

        Map<String, Object> body = filhoDe(paiId, "Filho com projeto");
        body.put("projetoId", projetoFilho.toString());

        ResponseEntity<Map> response = restTemplate.exchange(
                "/tarefas",
                HttpMethod.POST,
                autenticadoJson(body),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        assertThat(response.getBody().get("projetoId")).isEqualTo(projetoFilho.toString());
    }

    @Test
    void naoDeveCopiarTagsDoPai() {
        UUID tagId = criarTag("backend");
        Map<String, Object> paiBody = tarefa("Pai com tag");
        paiBody.put("tagIds", List.of(tagId.toString()));
        UUID paiId = UUID.fromString(restTemplate.exchange(
                "/tarefas", HttpMethod.POST, autenticadoJson(paiBody), Map.class
        ).getBody().get("id").toString());

        ResponseEntity<Map> response = restTemplate.exchange(
                "/tarefas",
                HttpMethod.POST,
                autenticadoJson(filhoDe(paiId, "Filho sem tag")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        assertThat((List<?>) response.getBody().get("tags")).isEmpty();
        UUID filhoId = UUID.fromString(response.getBody().get("id").toString());
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from tarefa_tag where tarefa_id = ?",
                Integer.class,
                filhoId)).isZero();
    }

    @Test
    void deveReabrirPaiFinalizadaAoCriarFilho() {
        UUID paiId = criarTarefa("Pai finalizado");
        ResponseEntity<Map> finalizada = restTemplate.exchange(
                "/tarefas/" + paiId,
                HttpMethod.PATCH,
                autenticadoJson(Map.of("status", "FINALIZADA")),
                Map.class);
        assertThat(finalizada.getStatusCode()).isEqualTo(OK);
        assertThat(finalizada.getBody().get("status")).isEqualTo("FINALIZADA");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/tarefas",
                HttpMethod.POST,
                autenticadoJson(filhoDe(paiId, "Novo passo")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        assertThat(response.getBody().get("status")).isEqualTo("PENDENTE");
        assertThat(jdbcTemplate.queryForObject(
                "select status from tarefa where id = ?",
                String.class,
                paiId)).isEqualTo("PENDENTE");
    }

    @Test
    void deveRecusarFilhoQuandoPaiTemBlocoConcluido() {
        UUID paiId = criarTarefa("Pai com progresso");
        inserirBlocoConcluido(paiId);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/tarefas",
                HttpMethod.POST,
                autenticadoJson(filhoDe(paiId, "Tarde demais")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(CONFLICT);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from tarefa where parent_id = ?",
                Integer.class,
                paiId)).isZero();
    }

    @Test
    void deveRetornar404QuandoPaiEstaDeletado() {
        UUID paiId = criarTarefa("Pai excluído");
        restTemplate.exchange(
                "/tarefas/" + paiId,
                HttpMethod.DELETE,
                autenticado(),
                Void.class).getStatusCode();

        ResponseEntity<Map> response = restTemplate.exchange(
                "/tarefas",
                HttpMethod.POST,
                autenticadoJson(filhoDe(paiId, "Órfão")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND);
    }

    @Test
    void deveSomarDuracaoDosFilhosNoPai() {
        UUID paiId = criarTarefa("Pai 60+60");
        criarFilho(paiId, "Filho A");
        criarFilho(paiId, "Filho B");

        Map<String, Object> pai = buscar(paiId);
        assertThat(pai.get("duracaoMinutos")).isEqualTo(120);
        assertThat(pai.get("temFilhos")).isEqualTo(true);
        assertThat(jdbcTemplate.queryForObject(
                "select duracao_minutos from tarefa where id = ?",
                Integer.class,
                paiId)).isEqualTo(120);
    }

    @Test
    void deveRejeitarPatchDeDuracaoNoPaiComFilhos() {
        UUID paiId = criarTarefa("Pai bloqueado");
        criarFilho(paiId, "Filho");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/tarefas/" + paiId,
                HttpMethod.PATCH,
                autenticadoJson(Map.of("duracaoMinutos", 180)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
        assertThat(jdbcTemplate.queryForObject(
                "select duracao_minutos from tarefa where id = ?",
                Integer.class,
                paiId)).isEqualTo(60);
    }

    @Test
    void deveManterDuracaoDoPaiAoExcluirUltimoFilho() {
        UUID paiId = criarTarefa("Pai 90");
        UUID filhoId = criarFilho(paiId, "Único", 90);

        assertThat(buscar(paiId).get("duracaoMinutos")).isEqualTo(90);

        ResponseEntity<Void> exclusao = restTemplate.exchange(
                "/tarefas/" + filhoId,
                HttpMethod.DELETE,
                autenticado(),
                Void.class);
        assertThat(exclusao.getStatusCode()).isEqualTo(NO_CONTENT);

        Map<String, Object> pai = buscar(paiId);
        assertThat(pai.get("duracaoMinutos")).isEqualTo(90);
        assertThat(pai.get("temFilhos")).isEqualTo(false);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from tarefa where parent_id = ? and deleted_at is null",
                Integer.class,
                paiId)).isZero();
    }

    @Test
    void deveListarSomenteRaizesQuandoSolicitado() {
        UUID paiId = criarTarefa("Raiz visível");
        criarFilho(paiId, "Filho oculto");
        criarTarefa("Outra raiz");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/tarefas?somenteRaizes=true",
                HttpMethod.GET,
                autenticado(),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        List<Map<String, Object>> content = content(response);
        assertThat(content).extracting(m -> m.get("titulo"))
                .containsExactlyInAnyOrder("Raiz visível", "Outra raiz");
        Map<String, Object> pai = content.stream()
                .filter(m -> "Raiz visível".equals(m.get("titulo")))
                .findFirst()
                .orElseThrow();
        assertThat(pai.get("temFilhos")).isEqualTo(true);
        assertThat(pai.get("parentId")).isNull();
    }

    @Test
    void deveListarFilhosPorParentIdComTituloDoPai() {
        UUID paiId = criarTarefa("Estudar Security");
        criarFilho(paiId, "Ler OWASP");
        criarFilho(paiId, "Fazer lab");
        criarTarefa("Fora da família");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/tarefas?parentId=" + paiId,
                HttpMethod.GET,
                autenticado(),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        List<Map<String, Object>> content = content(response);
        assertThat(content).extracting(m -> m.get("titulo"))
                .containsExactlyInAnyOrder("Ler OWASP", "Fazer lab");
        assertThat(content).allSatisfy(item -> {
            assertThat(item.get("parentId")).isEqualTo(paiId.toString());
            assertThat(item.get("parentTitulo")).isEqualTo("Estudar Security");
            assertThat(item.get("temFilhos")).isEqualTo(false);
        });
    }

    @Test
    void deveReordenarSubtarefasDoMesmoPai() {
        UUID paiId = criarTarefa("Estudar Security");
        UUID primeiro = criarFilho(paiId, "Ler OWASP");
        UUID segundo = criarFilho(paiId, "Fazer lab");

        ResponseEntity<Void> reordenar = restTemplate.exchange(
                "/tarefas/ordem",
                HttpMethod.PUT,
                autenticadoJson(Map.of("ids", List.of(segundo, primeiro))),
                Void.class);
        assertThat(reordenar.getStatusCode()).isEqualTo(NO_CONTENT);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/tarefas?parentId=" + paiId + "&sort=ordem,asc",
                HttpMethod.GET,
                autenticado(),
                Map.class);
        assertThat(content(response)).extracting(m -> m.get("titulo"))
                .containsExactly("Fazer lab", "Ler OWASP");
    }

    @Test
    void deveRejeitarReordenarPaiComFilho() {
        UUID paiId = criarTarefa("Pai");
        UUID filhoId = criarFilho(paiId, "Filho");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/tarefas/ordem",
                HttpMethod.PUT,
                autenticadoJson(Map.of("ids", List.of(filhoId, paiId))),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
    }

    @Test
    void deveIncluirRaizesEFilhosNaListaSemFiltroDeHierarquia() {
        UUID paiId = criarTarefa("Raiz");
        criarFilho(paiId, "Filho");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/tarefas",
                HttpMethod.GET,
                autenticado(),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(content(response)).extracting(m -> m.get("titulo"))
                .containsExactlyInAnyOrder("Raiz", "Filho");
    }

    @Test
    void deveFinalizarFilhosAtivosAoPatchFinalizadaNoPaiSemConcederXp() {
        UUID paiId = criarTarefa("Pai a finalizar");
        UUID filhoA = criarFilho(paiId, "Filho A");
        UUID filhoB = criarFilho(paiId, "Filho B");

        ResponseEntity<Map> response = patchStatus(paiId, "FINALIZADA");

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody().get("status")).isEqualTo("FINALIZADA");
        assertThat(statusDa(filhoA)).isEqualTo("FINALIZADA");
        assertThat(statusDa(filhoB)).isEqualTo("FINALIZADA");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from xp_evento where usuario_id = ?",
                Integer.class,
                usuarioId)).isZero();
    }

    @Test
    void deveCancelarFilhosAtivosAoPatchCanceladaNoPai() {
        UUID paiId = criarTarefa("Pai a cancelar");
        UUID filhoA = criarFilho(paiId, "Aberto A");
        UUID filhoB = criarFilho(paiId, "Aberto B");

        ResponseEntity<Map> response = patchStatus(paiId, "CANCELADA");

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody().get("status")).isEqualTo("CANCELADA");
        assertThat(statusDa(filhoA)).isEqualTo("CANCELADA");
        assertThat(statusDa(filhoB)).isEqualTo("CANCELADA");
    }

    @Test
    void naoDeveReabrirFilhosAoReabrirPai() {
        UUID paiId = criarTarefa("Pai reabrir");
        UUID filhoA = criarFilho(paiId, "Filho A");
        UUID filhoB = criarFilho(paiId, "Filho B");
        assertThat(patchStatus(filhoA, "FINALIZADA").getStatusCode()).isEqualTo(OK);
        assertThat(patchStatus(filhoB, "FINALIZADA").getStatusCode()).isEqualTo(OK);
        assertThat(patchStatus(paiId, "FINALIZADA").getStatusCode()).isEqualTo(OK);

        ResponseEntity<Map> response = patchStatus(paiId, "PENDENTE");

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody().get("status")).isEqualTo("PENDENTE");
        assertThat(statusDa(filhoA)).isEqualTo("FINALIZADA");
        assertThat(statusDa(filhoB)).isEqualTo("FINALIZADA");
    }

    @Test
    void deveReabrirPaiFinalizadaAoReabrirFilho() {
        UUID paiId = criarTarefa("Pai fechado");
        UUID filhoId = criarFilho(paiId, "Filho único");
        assertThat(patchStatus(filhoId, "FINALIZADA").getStatusCode()).isEqualTo(OK);
        assertThat(patchStatus(paiId, "FINALIZADA").getStatusCode()).isEqualTo(OK);
        assertThat(statusDa(paiId)).isEqualTo("FINALIZADA");
        assertThat(statusDa(filhoId)).isEqualTo("FINALIZADA");

        ResponseEntity<Map> response = patchStatus(filhoId, "PENDENTE");

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody().get("status")).isEqualTo("PENDENTE");
        assertThat(statusDa(paiId)).isEqualTo("PENDENTE");
    }

    @Test
    void deveSoftDeletarFilhosAtivosAoExcluirPai() {
        UUID paiId = criarTarefa("Pai excluir");
        UUID filhoA = criarFilho(paiId, "Filho A");
        UUID filhoB = criarFilho(paiId, "Filho B");

        ResponseEntity<Void> exclusao = restTemplate.exchange(
                "/tarefas/" + paiId,
                HttpMethod.DELETE,
                autenticado(),
                Void.class);

        assertThat(exclusao.getStatusCode()).isEqualTo(NO_CONTENT);
        assertThat(buscar(paiId).get("deletada")).isEqualTo(true);
        assertThat(buscar(filhoA).get("deletada")).isEqualTo(true);
        assertThat(buscar(filhoB).get("deletada")).isEqualTo(true);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from tarefa where parent_id = ? and deleted_at is null",
                Integer.class,
                paiId)).isZero();
    }

    @Test
    void deveRestaurarTodosOsFilhosAoRestaurarPai() {
        UUID paiId = criarTarefa("Pai restaurar");
        UUID filhoA = criarFilho(paiId, "Filho A");
        UUID filhoB = criarFilho(paiId, "Filho B");
        assertThat(restTemplate.exchange(
                "/tarefas/" + paiId, HttpMethod.DELETE, autenticado(), Void.class
        ).getStatusCode()).isEqualTo(NO_CONTENT);
        assertThat(buscar(filhoA).get("deletada")).isEqualTo(true);
        assertThat(buscar(filhoB).get("deletada")).isEqualTo(true);

        ResponseEntity<Map> restaurado = restTemplate.exchange(
                "/tarefas/" + paiId + "/restore",
                HttpMethod.POST,
                autenticado(),
                Map.class);

        assertThat(restaurado.getStatusCode()).isEqualTo(OK);
        assertThat(restaurado.getBody().get("deletada")).isEqualTo(false);
        assertThat(buscar(filhoA).get("deletada")).isEqualTo(false);
        assertThat(buscar(filhoB).get("deletada")).isEqualTo(false);
    }

    @Test
    void deveRestaurarPaiAoRestaurarFilhoDeixandoIrmaosDeletados() {
        UUID paiId = criarTarefa("Família");
        UUID filhoA = criarFilho(paiId, "A");
        UUID filhoB = criarFilho(paiId, "B");
        assertThat(restTemplate.exchange(
                "/tarefas/" + paiId, HttpMethod.DELETE, autenticado(), Void.class
        ).getStatusCode()).isEqualTo(NO_CONTENT);

        ResponseEntity<Map> restaurado = restTemplate.exchange(
                "/tarefas/" + filhoA + "/restore",
                HttpMethod.POST,
                autenticado(),
                Map.class);

        assertThat(restaurado.getStatusCode()).isEqualTo(OK);
        assertThat(restaurado.getBody().get("deletada")).isEqualTo(false);
        assertThat(buscar(paiId).get("deletada")).isEqualTo(false);
        assertThat(buscar(filhoB).get("deletada")).isEqualTo(true);
    }

    @Test
    void naoDeveMarcarPaiAtrasadaPorPrazoProprioQuandoTemFilhos() {
        UUID paiId = criarTarefa("Pai com prazo velho");
        UUID filhoFuturo = criarFilho(paiId, "Filho futuro");
        UUID filhoAtrasado = criarFilho(paiId, "Filho atrasado");

        Instant prazoOntem = PeriodoBRT.intervaloDia(LocalDate.now(PeriodoBRT.ZONE))
                .inicioInclusivo()
                .minus(1, ChronoUnit.HOURS);
        jdbcTemplate.update(
                "update tarefa set prazo = ? where id = ?",
                Timestamp.from(prazoOntem),
                paiId);
        jdbcTemplate.update(
                "update tarefa set prazo = ? where id = ?",
                Timestamp.from(prazoOntem),
                filhoAtrasado);

        tarefaAtrasoJob.processarAtrasos();

        assertThat(statusDa(paiId)).isEqualTo("PENDENTE");
        assertThat(statusDa(filhoFuturo)).isEqualTo("PENDENTE");
        assertThat(statusDa(filhoAtrasado)).isEqualTo("ATRASADA");
    }

    private ResponseEntity<Map> patchStatus(UUID id, String status) {
        return restTemplate.exchange(
                "/tarefas/" + id,
                HttpMethod.PATCH,
                autenticadoJson(Map.of("status", status)),
                Map.class);
    }

    private String statusDa(UUID id) {
        return jdbcTemplate.queryForObject(
                "select status from tarefa where id = ?",
                String.class,
                id);
    }

    private Map<String, Object> buscar(UUID id) {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/tarefas/" + id,
                HttpMethod.GET,
                autenticado(),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> content(ResponseEntity<Map> response) {
        assertThat(response.getBody()).isNotNull();
        return (List<Map<String, Object>>) response.getBody().get("content");
    }

    private void inserirBlocoConcluido(UUID tarefaId) {
        UUID planoId = UUID.randomUUID();
        UUID blocoId = UUID.randomUUID();
        UUID execucaoId = UUID.randomUUID();
        Timestamp agora = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                """
                insert into plano (
                    id, usuario_id, data, versao, status, tem_conflito,
                    itens_nao_alocados, gerado_em, created_at, updated_at
                ) values (?, ?, ?, 1, 'TAREFA_AVULSA', false, '[]'::jsonb, ?, ?, ?)
                """,
                planoId, usuarioId, LocalDate.now(), agora, agora, agora);
        jdbcTemplate.update(
                """
                insert into bloco_plano (
                    id, plano_id, tarefa_id, duracao_minutos, ordem, created_at, updated_at
                ) values (?, ?, ?, 30, 1, ?, ?)
                """,
                blocoId, planoId, tarefaId, agora, agora);
        jdbcTemplate.update(
                """
                insert into execucao (
                    id, usuario_id, bloco_plano_id, status, iniciado_em, concluido_em,
                    created_at, updated_at
                ) values (?, ?, ?, 'CONCLUIDO', ?, ?, ?, ?)
                """,
                execucaoId, usuarioId, blocoId, agora, agora, agora, agora);
    }

    private UUID criarTarefa(String titulo) {
        return criarTarefaCom(accessToken, titulo);
    }

    private UUID criarTarefaCom(String token, String titulo) {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/tarefas",
                HttpMethod.POST,
                autenticadoJsonCom(token, Map.of("titulo", titulo)),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        return UUID.fromString(response.getBody().get("id").toString());
    }

    private UUID criarFilho(UUID paiId, String titulo) {
        return criarFilho(paiId, titulo, 60);
    }

    private UUID criarFilho(UUID paiId, String titulo, int duracaoMinutos) {
        Map<String, Object> body = filhoDe(paiId, titulo);
        body.put("duracaoMinutos", duracaoMinutos);
        ResponseEntity<Map> response = restTemplate.exchange(
                "/tarefas",
                HttpMethod.POST,
                autenticadoJson(body),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        return UUID.fromString(response.getBody().get("id").toString());
    }

    private UUID criarTarefaNoProjeto(String titulo, UUID projetoId) {
        Map<String, Object> body = tarefa(titulo);
        body.put("projetoId", projetoId.toString());
        ResponseEntity<Map> response = restTemplate.exchange(
                "/tarefas", HttpMethod.POST, autenticadoJson(body), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        return UUID.fromString(response.getBody().get("id").toString());
    }

    private UUID criarProjeto(String nome) {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/projetos",
                HttpMethod.POST,
                autenticadoJson(Map.of("nome", nome)),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        return UUID.fromString(response.getBody().get("id").toString());
    }

    private UUID criarTag(String nome) {
        return UUID.fromString(restTemplate.exchange(
                "/tags",
                HttpMethod.POST,
                autenticadoJson(Map.of("nome", nome)),
                Map.class).getBody().get("id").toString());
    }

    private String tokenDeNovoUsuario() {
        String email = "outro-sub-" + UUID.randomUUID() + "@email.com";
        restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Outro", "email", email, "senha", SENHA)),
                Map.class);
        return restTemplate.postForEntity(
                "/auth/login",
                json(Map.of("email", email, "senha", SENHA)),
                Map.class).getBody().get("accessToken").toString();
    }

    private Map<String, Object> filhoDe(UUID paiId, String titulo) {
        Map<String, Object> body = tarefa(titulo);
        body.put("parentId", paiId.toString());
        return body;
    }

    private Map<String, Object> tarefa(String titulo) {
        Map<String, Object> body = new HashMap<>();
        body.put("titulo", titulo);
        body.put("duracaoMinutos", 60);
        body.put("prazo", Instant.now().plus(2, ChronoUnit.DAYS).toString());
        return body;
    }

    private HttpEntity<Void> autenticado() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return new HttpEntity<>(headers);
    }

    private HttpEntity<?> autenticadoJson(Object body) {
        return autenticadoJsonCom(accessToken, body);
    }

    private HttpEntity<?> autenticadoJsonCom(String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    private static HttpEntity<Map<String, ?>> json(Map<String, ?> body) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
