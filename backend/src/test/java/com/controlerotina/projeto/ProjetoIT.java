package com.controlerotina.projeto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.controlerotina.capacidade.entity.DiaSemana;
import com.controlerotina.historico.PeriodoBRT;

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
class ProjetoIT {

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
        String email = "projeto-" + UUID.randomUUID() + "@email.com";
        assertThat(restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Dono Projeto", "email", email, "senha", SENHA)),
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
    void deveRetornar401AoCriarSemToken() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/projetos",
                json(Map.of("nome", "TCC")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
    }

    @Test
    void deveCriarProjetoAtivoCom201() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/projetos",
                HttpMethod.POST,
                autenticadoJson(Map.of(
                        "nome", "TCC",
                        "descricao", "Trabalho de conclusão"
                )),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("id")).isNotNull();
        assertThat(response.getBody().get("nome")).isEqualTo("TCC");
        assertThat(response.getBody().get("descricao")).isEqualTo("Trabalho de conclusão");
        assertThat(response.getBody().get("status")).isEqualTo("ATIVO");
        assertThat(response.getBody().get("createdAt")).isNotNull();
        assertThat(response.getBody().get("updatedAt")).isNotNull();

        UUID id = UUID.fromString(response.getBody().get("id").toString());
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select usuario_id, nome, descricao, status from projeto where id = ?", id);
        assertThat(row.get("usuario_id")).isEqualTo(usuarioId);
        assertThat(row.get("nome")).isEqualTo("TCC");
        assertThat(row.get("descricao")).isEqualTo("Trabalho de conclusão");
        assertThat(row.get("status")).isEqualTo("ATIVO");
    }

    @Test
    void deveCriarProjetoSemDescricao() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/projetos",
                HttpMethod.POST,
                autenticadoJson(Map.of("nome", "API trabalho")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        assertThat(response.getBody().get("nome")).isEqualTo("API trabalho");
        assertThat(response.getBody().get("descricao")).isNull();
        assertThat(response.getBody().get("status")).isEqualTo("ATIVO");
    }

    @Test
    void deveRejeitarCreateSemNomeCom400() {
        Map<String, Object> semNome = new HashMap<>();
        semNome.put("descricao", "sem nome");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/projetos",
                HttpMethod.POST,
                autenticadoJson(semNome),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
    }

    @Test
    void deveRejeitarNomeMaiorQue80Com400() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/projetos",
                HttpMethod.POST,
                autenticadoJson(Map.of("nome", "a".repeat(81))),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
    }

    @Test
    void deveRejeitarDescricaoMaiorQue500Com400() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/projetos",
                HttpMethod.POST,
                autenticadoJson(Map.of(
                        "nome", "Ok",
                        "descricao", "b".repeat(501)
                )),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
    }

    @Test
    void deveRetornar401AoListarSemToken() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/projetos", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
    }

    @Test
    void deveListarSomenteProjetosAtivosDoUsuarioPorPadrao() {
        UUID ativoId = criarProjeto("Ativo A", null);
        UUID arquivadoId = criarProjeto("Arquivado B", null);
        jdbcTemplate.update("update projeto set status = 'ARQUIVADO' where id = ?", arquivadoId);

        String outroEmail = "outro-proj-" + UUID.randomUUID() + "@email.com";
        restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Outro", "email", outroEmail, "senha", SENHA)),
                Map.class);
        String outroToken = restTemplate.postForEntity(
                "/auth/login",
                json(Map.of("email", outroEmail, "senha", SENHA)),
                Map.class).getBody().get("accessToken").toString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(outroToken);
        restTemplate.exchange(
                "/projetos",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("nome", "Alheio"), headers),
                Map.class);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/projetos",
                HttpMethod.GET,
                autenticado(),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        List<Map<String, Object>> content = content(response);
        assertThat(content).hasSize(1);
        assertThat(content.get(0).get("id")).isEqualTo(ativoId.toString());
        assertThat(content.get(0).get("nome")).isEqualTo("Ativo A");
        assertThat(content.get(0).get("status")).isEqualTo("ATIVO");
    }

    @Test
    void deveFiltrarPorStatusArquivado() {
        criarProjeto("Ativo", null);
        UUID arquivadoId = criarProjeto("Arquivado", "desc");
        jdbcTemplate.update("update projeto set status = 'ARQUIVADO' where id = ?", arquivadoId);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/projetos?status=ARQUIVADO",
                HttpMethod.GET,
                autenticado(),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        List<Map<String, Object>> content = content(response);
        assertThat(content).hasSize(1);
        assertThat(content.get(0).get("id")).isEqualTo(arquivadoId.toString());
        assertThat(content.get(0).get("status")).isEqualTo("ARQUIVADO");
    }

    @Test
    void deveOrdenarPorNomeAscPorPadrao() {
        criarProjeto("Zebra", null);
        criarProjeto("Alpha", null);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/projetos",
                HttpMethod.GET,
                autenticado(),
                Map.class);

        assertThat(content(response)).extracting(m -> m.get("nome"))
                .containsExactly("Alpha", "Zebra");
    }

    @Test
    void devePaginarListaDeProjetos() {
        for (int i = 0; i < 21; i++) {
            criarProjeto(String.format("Proj %02d", i), null);
        }

        ResponseEntity<Map> page0 = restTemplate.exchange(
                "/projetos?page=0&size=20",
                HttpMethod.GET,
                autenticado(),
                Map.class);

        assertThat(page0.getStatusCode()).isEqualTo(OK);
        assertThat(content(page0)).hasSize(20);
        assertThat(((Number) page0.getBody().get("totalElements")).intValue()).isEqualTo(21);

        ResponseEntity<Map> page1 = restTemplate.exchange(
                "/projetos?page=1&size=20",
                HttpMethod.GET,
                autenticado(),
                Map.class);

        assertThat(content(page1)).hasSize(1);
    }

    @Test
    void deveBuscarProjetoPorIdInclusiveArquivado() {
        UUID id = criarProjeto("Detalhe", "desc");

        ResponseEntity<Map> ativo = restTemplate.exchange(
                "/projetos/" + id,
                HttpMethod.GET,
                autenticado(),
                Map.class);

        assertThat(ativo.getStatusCode()).isEqualTo(OK);
        assertThat(ativo.getBody().get("id")).isEqualTo(id.toString());
        assertThat(ativo.getBody().get("nome")).isEqualTo("Detalhe");
        assertThat(ativo.getBody().get("status")).isEqualTo("ATIVO");

        jdbcTemplate.update("update projeto set status = 'ARQUIVADO' where id = ?", id);

        ResponseEntity<Map> arquivado = restTemplate.exchange(
                "/projetos/" + id,
                HttpMethod.GET,
                autenticado(),
                Map.class);

        assertThat(arquivado.getStatusCode()).isEqualTo(OK);
        assertThat(arquivado.getBody().get("status")).isEqualTo("ARQUIVADO");
    }

    @Test
    void deveRetornar404AoBuscarProjetoInexistenteOuDeOutroUsuario() {
        ResponseEntity<Map> inexistente = restTemplate.exchange(
                "/projetos/" + UUID.randomUUID(),
                HttpMethod.GET,
                autenticado(),
                Map.class);
        assertThat(inexistente.getStatusCode()).isEqualTo(NOT_FOUND);

        UUID id = criarProjeto("Meu", null);

        String outroEmail = "outro-get-" + UUID.randomUUID() + "@email.com";
        restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Outro", "email", outroEmail, "senha", SENHA)),
                Map.class);
        String outroToken = restTemplate.postForEntity(
                "/auth/login",
                json(Map.of("email", outroEmail, "senha", SENHA)),
                Map.class).getBody().get("accessToken").toString();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(outroToken);
        ResponseEntity<Map> alheio = restTemplate.exchange(
                "/projetos/" + id,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);
        assertThat(alheio.getStatusCode()).isEqualTo(NOT_FOUND);
    }

    @Test
    void deveAtualizarNomeEDescricaoViaPatch() {
        UUID id = criarProjeto("Antigo", "velha");

        Map<String, Object> body = new HashMap<>();
        body.put("nome", "Novo");
        body.put("descricao", "nova desc");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/projetos/" + id,
                HttpMethod.PATCH,
                autenticadoJson(body),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody().get("nome")).isEqualTo("Novo");
        assertThat(response.getBody().get("descricao")).isEqualTo("nova desc");
        assertThat(response.getBody().get("status")).isEqualTo("ATIVO");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select nome, descricao, status from projeto where id = ?", id);
        assertThat(row.get("nome")).isEqualTo("Novo");
        assertThat(row.get("descricao")).isEqualTo("nova desc");
        assertThat(row.get("status")).isEqualTo("ATIVO");
    }

    @Test
    void deveLimparDescricaoComStringVaziaNoPatch() {
        UUID id = criarProjeto("ComDesc", "tem");

        Map<String, Object> body = new HashMap<>();
        body.put("descricao", "");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/projetos/" + id,
                HttpMethod.PATCH,
                autenticadoJson(body),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody().get("descricao")).isNull();
    }

    @Test
    void deveRejeitarPatchComNomeMaiorQue80() {
        UUID id = criarProjeto("Ok", null);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/projetos/" + id,
                HttpMethod.PATCH,
                autenticadoJson(Map.of("nome", "x".repeat(81))),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
    }

    @Test
    void deveRetornar404AoPatcharProjetoDeOutroUsuario() {
        UUID id = criarProjeto("Meu", null);

        String outroEmail = "outro-patch-" + UUID.randomUUID() + "@email.com";
        restTemplate.postForEntity(
                "/usuarios",
                json(Map.of("nome", "Outro", "email", outroEmail, "senha", SENHA)),
                Map.class);
        String outroToken = restTemplate.postForEntity(
                "/auth/login",
                json(Map.of("email", outroEmail, "senha", SENHA)),
                Map.class).getBody().get("accessToken").toString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(outroToken);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/projetos/" + id,
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("nome", "Hack"), headers),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND);
    }

    @Test
    void deveArquivarProjetoAtivo() {
        UUID id = criarProjeto("Para arquivar", null);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/projetos/" + id + "/arquivar",
                HttpMethod.POST,
                autenticado(),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody().get("status")).isEqualTo("ARQUIVADO");
        assertThat(jdbcTemplate.queryForObject(
                "select status from projeto where id = ?", String.class, id)).isEqualTo("ARQUIVADO");
    }

    @Test
    void deveRetornar409AoArquivarJaArquivado() {
        UUID id = criarProjeto("Ja arquivado", null);
        assertThat(restTemplate.exchange(
                "/projetos/" + id + "/arquivar",
                HttpMethod.POST,
                autenticado(),
                Map.class).getStatusCode()).isEqualTo(OK);

        ResponseEntity<Map> segundo = restTemplate.exchange(
                "/projetos/" + id + "/arquivar",
                HttpMethod.POST,
                autenticado(),
                Map.class);

        assertThat(segundo.getStatusCode()).isEqualTo(CONFLICT);
    }

    @Test
    void deveReativarProjetoArquivado() {
        UUID id = criarProjeto("Reativar", null);
        restTemplate.exchange(
                "/projetos/" + id + "/arquivar",
                HttpMethod.POST,
                autenticado(),
                Map.class);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/projetos/" + id + "/reativar",
                HttpMethod.POST,
                autenticado(),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody().get("status")).isEqualTo("ATIVO");
    }

    @Test
    void deveRetornar409AoReativarJaAtivo() {
        UUID id = criarProjeto("Ja ativo", null);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/projetos/" + id + "/reativar",
                HttpMethod.POST,
                autenticado(),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(CONFLICT);
    }

    @Test
    void deveRetornar404AoArquivarProjetoInexistente() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/projetos/" + UUID.randomUUID() + "/arquivar",
                HttpMethod.POST,
                autenticado(),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND);
    }

    @Test
    void t7_deveCriarReservaEListarDoProjeto() {
        criarCapacidade(Map.of("SEGUNDA", 3, "TERCA", 2));
        UUID projetoId = criarProjeto("Com reserva", null);

        ResponseEntity<Map> criada = restTemplate.exchange(
                "/projetos/" + projetoId + "/reservas",
                HttpMethod.POST,
                autenticadoJson(Map.of(
                        "diaSemana", "SEGUNDA",
                        "minutosReservados", 60
                )),
                Map.class);

        assertThat(criada.getStatusCode()).isEqualTo(CREATED);
        assertThat(criada.getBody().get("projetoId")).isEqualTo(projetoId.toString());
        assertThat(criada.getBody().get("diaSemana")).isEqualTo("SEGUNDA");
        assertThat(criada.getBody().get("minutosReservados")).isEqualTo(60);

        ResponseEntity<List> lista = restTemplate.exchange(
                "/projetos/" + projetoId + "/reservas",
                HttpMethod.GET,
                autenticado(),
                List.class);

        assertThat(lista.getStatusCode()).isEqualTo(OK);
        assertThat(lista.getBody()).hasSize(1);
        assertThat(((Map<?, ?>) lista.getBody().get(0)).get("minutosReservados")).isEqualTo(60);
    }

    @Test
    void t7_deveRetornar409AoCriarSegundaReservaNoMesmoDia() {
        criarCapacidade(Map.of("SEGUNDA", 4));
        UUID projetoId = criarProjeto("Dup", null);

        assertThat(restTemplate.exchange(
                "/projetos/" + projetoId + "/reservas",
                HttpMethod.POST,
                autenticadoJson(Map.of("diaSemana", "SEGUNDA", "minutosReservados", 60)),
                Map.class).getStatusCode()).isEqualTo(CREATED);

        ResponseEntity<Map> segunda = restTemplate.exchange(
                "/projetos/" + projetoId + "/reservas",
                HttpMethod.POST,
                autenticadoJson(Map.of("diaSemana", "SEGUNDA", "minutosReservados", 30)),
                Map.class);

        assertThat(segunda.getStatusCode()).isEqualTo(CONFLICT);
    }

    @Test
    void t7_deveRetornar409QuandoSomaExcedeCapacidade() {
        criarCapacidade(Map.of("SEGUNDA", 1));
        UUID projetoA = criarProjeto("A", null);
        UUID projetoB = criarProjeto("B", null);

        assertThat(restTemplate.exchange(
                "/projetos/" + projetoA + "/reservas",
                HttpMethod.POST,
                autenticadoJson(Map.of("diaSemana", "SEGUNDA", "minutosReservados", 45)),
                Map.class).getStatusCode()).isEqualTo(CREATED);

        ResponseEntity<Map> estoura = restTemplate.exchange(
                "/projetos/" + projetoB + "/reservas",
                HttpMethod.POST,
                autenticadoJson(Map.of("diaSemana", "SEGUNDA", "minutosReservados", 30)),
                Map.class);

        assertThat(estoura.getStatusCode()).isEqualTo(CONFLICT);
    }

    @Test
    void b10b_deveListarReservasConsolidadasComFiltros() {
        criarCapacidade(Map.of("SEGUNDA", 3, "TERCA", 3));
        UUID projetoA = criarProjeto("Filtro A", null);
        UUID projetoB = criarProjeto("Filtro B", null);

        restTemplate.exchange(
                "/projetos/" + projetoA + "/reservas",
                HttpMethod.POST,
                autenticadoJson(Map.of("diaSemana", "SEGUNDA", "minutosReservados", 30)),
                Map.class);
        restTemplate.exchange(
                "/projetos/" + projetoA + "/reservas",
                HttpMethod.POST,
                autenticadoJson(Map.of("diaSemana", "TERCA", "minutosReservados", 45)),
                Map.class);
        restTemplate.exchange(
                "/projetos/" + projetoB + "/reservas",
                HttpMethod.POST,
                autenticadoJson(Map.of("diaSemana", "SEGUNDA", "minutosReservados", 15)),
                Map.class);

        ResponseEntity<List> todas = restTemplate.exchange(
                "/projetos/reservas",
                HttpMethod.GET,
                autenticado(),
                List.class);
        assertThat(todas.getStatusCode()).isEqualTo(OK);
        assertThat(todas.getBody()).hasSize(3);

        ResponseEntity<List> porDia = restTemplate.exchange(
                "/projetos/reservas?diaSemana=SEGUNDA",
                HttpMethod.GET,
                autenticado(),
                List.class);
        assertThat(porDia.getBody()).hasSize(2);

        ResponseEntity<List> porProjeto = restTemplate.exchange(
                "/projetos/reservas?projetoId=" + projetoA,
                HttpMethod.GET,
                autenticado(),
                List.class);
        assertThat(porProjeto.getBody()).hasSize(2);
    }

    @Test
    void b11_deveAtualizarReservaERevalidar() {
        criarCapacidade(Map.of("SEGUNDA", 2, "TERCA", 2));
        UUID projetoId = criarProjeto("Patch reserva", null);

        UUID reservaId = UUID.fromString(restTemplate.exchange(
                "/projetos/" + projetoId + "/reservas",
                HttpMethod.POST,
                autenticadoJson(Map.of("diaSemana", "SEGUNDA", "minutosReservados", 30)),
                Map.class).getBody().get("id").toString());

        ResponseEntity<Map> atualizada = restTemplate.exchange(
                "/projetos/" + projetoId + "/reservas/" + reservaId,
                HttpMethod.PATCH,
                autenticadoJson(Map.of(
                        "diaSemana", "TERCA",
                        "minutosReservados", 60
                )),
                Map.class);

        assertThat(atualizada.getStatusCode()).isEqualTo(OK);
        assertThat(atualizada.getBody().get("diaSemana")).isEqualTo("TERCA");
        assertThat(atualizada.getBody().get("minutosReservados")).isEqualTo(60);

        ResponseEntity<Map> estoura = restTemplate.exchange(
                "/projetos/" + projetoId + "/reservas/" + reservaId,
                HttpMethod.PATCH,
                autenticadoJson(Map.of("minutosReservados", 150)),
                Map.class);
        assertThat(estoura.getStatusCode()).isEqualTo(CONFLICT);
    }

    @Test
    void b11_deveRejeitarAumentoDeReservaEmProjetoArquivado() {
        criarCapacidade(Map.of("SEGUNDA", 3));
        UUID projetoId = criarProjeto("Arquivar depois", null);
        UUID reservaId = UUID.fromString(restTemplate.exchange(
                "/projetos/" + projetoId + "/reservas",
                HttpMethod.POST,
                autenticadoJson(Map.of("diaSemana", "SEGUNDA", "minutosReservados", 30)),
                Map.class).getBody().get("id").toString());

        restTemplate.exchange(
                "/projetos/" + projetoId + "/arquivar",
                HttpMethod.POST,
                autenticado(),
                Map.class);

        ResponseEntity<Map> aumento = restTemplate.exchange(
                "/projetos/" + projetoId + "/reservas/" + reservaId,
                HttpMethod.PATCH,
                autenticadoJson(Map.of("minutosReservados", 60)),
                Map.class);
        assertThat(aumento.getStatusCode()).isEqualTo(CONFLICT);

        ResponseEntity<Map> reducao = restTemplate.exchange(
                "/projetos/" + projetoId + "/reservas/" + reservaId,
                HttpMethod.PATCH,
                autenticadoJson(Map.of("minutosReservados", 15)),
                Map.class);
        assertThat(reducao.getStatusCode()).isEqualTo(OK);
        assertThat(reducao.getBody().get("minutosReservados")).isEqualTo(15);
    }

    @Test
    void b12_deveRemoverReservaComHardDelete() {
        criarCapacidade(Map.of("SEGUNDA", 3));
        UUID projetoId = criarProjeto("Delete reserva", null);
        UUID reservaId = UUID.fromString(restTemplate.exchange(
                "/projetos/" + projetoId + "/reservas",
                HttpMethod.POST,
                autenticadoJson(Map.of("diaSemana", "SEGUNDA", "minutosReservados", 45)),
                Map.class).getBody().get("id").toString());

        ResponseEntity<Void> removida = restTemplate.exchange(
                "/projetos/" + projetoId + "/reservas/" + reservaId,
                HttpMethod.DELETE,
                autenticado(),
                Void.class);

        assertThat(removida.getStatusCode()).isEqualTo(NO_CONTENT);

        ResponseEntity<List> lista = restTemplate.exchange(
                "/projetos/" + projetoId + "/reservas",
                HttpMethod.GET,
                autenticado(),
                List.class);
        assertThat(lista.getBody()).isEmpty();

        ResponseEntity<Map> inexistente = restTemplate.exchange(
                "/projetos/" + projetoId + "/reservas/" + reservaId,
                HttpMethod.DELETE,
                autenticado(),
                Map.class);
        assertThat(inexistente.getStatusCode()).isEqualTo(NOT_FOUND);
    }

    @Test
    void t8_deveRetornar409AoReduzirHorasAbaixoDasReservas() {
        UUID capacidadeId = UUID.fromString(((Map<?, ?>) restTemplate.exchange(
                "/capacidades/semanal",
                HttpMethod.POST,
                autenticadoJson(List.of(Map.of("diaSemana", "SEGUNDA", "horas", 3))),
                List.class).getBody().get(0)).get("id").toString());
        UUID projetoId = criarProjeto("Reserva vs capacidade", null);
        assertThat(restTemplate.exchange(
                "/projetos/" + projetoId + "/reservas",
                HttpMethod.POST,
                autenticadoJson(Map.of("diaSemana", "SEGUNDA", "minutosReservados", 120)),
                Map.class).getStatusCode()).isEqualTo(CREATED);

        ResponseEntity<Map> reduzida = restTemplate.exchange(
                "/capacidades/semanal/" + capacidadeId,
                HttpMethod.PATCH,
                autenticadoJson(Map.of("horas", 1)),
                Map.class);

        assertThat(reduzida.getStatusCode()).isEqualTo(CONFLICT);
    }

    @Test
    void t9_createUpdateDeleteReservaComPlanoAprovadoGeraPropostaSemMutarAprovado() {
        LocalDate hoje = LocalDate.now(PeriodoBRT.ZONE);
        String diaSemana = DiaSemana.de(hoje).name();
        criarCapacidade(Map.of(diaSemana, 4));

        assertThat(restTemplate.exchange(
                "/tarefas",
                HttpMethod.POST,
                autenticadoJson(Map.of(
                        "titulo", "Tarefa proposta reserva",
                        "prioridade", "MEDIA",
                        "duracaoMinutos", 60,
                        "prazo", hoje.plusDays(7).atTime(12, 0).atZone(PeriodoBRT.ZONE).toInstant().toString()
                )),
                Map.class).getStatusCode()).isEqualTo(CREATED);

        ResponseEntity<Map> gerado = restTemplate.exchange(
                "/planos/gerar?data=" + hoje,
                HttpMethod.POST,
                autenticado(),
                Map.class);
        assertThat(gerado.getStatusCode()).isEqualTo(CREATED);
        UUID planoAprovadoId = UUID.fromString(gerado.getBody().get("id").toString());
        int versaoAprovada = ((Number) gerado.getBody().get("versao")).intValue();

        assertThat(restTemplate.exchange(
                "/planos/" + planoAprovadoId + "/aprovar",
                HttpMethod.POST,
                autenticado(),
                Map.class).getStatusCode()).isEqualTo(OK);

        UUID projetoId = criarProjeto("Gatilho proposta", null);

        UUID reservaId = UUID.fromString(restTemplate.exchange(
                "/projetos/" + projetoId + "/reservas",
                HttpMethod.POST,
                autenticadoJson(Map.of("diaSemana", diaSemana, "minutosReservados", 60)),
                Map.class).getBody().get("id").toString());
        assertPropostaPendenteComAprovadoIntacto(hoje, planoAprovadoId, versaoAprovada);

        assertThat(restTemplate.exchange(
                "/projetos/" + projetoId + "/reservas/" + reservaId,
                HttpMethod.PATCH,
                autenticadoJson(Map.of("minutosReservados", 75)),
                Map.class).getStatusCode()).isEqualTo(OK);
        assertPropostaPendenteComAprovadoIntacto(hoje, planoAprovadoId, versaoAprovada);

        assertThat(restTemplate.exchange(
                "/projetos/" + projetoId + "/reservas/" + reservaId,
                HttpMethod.DELETE,
                autenticado(),
                Void.class).getStatusCode()).isEqualTo(NO_CONTENT);
        assertPropostaPendenteComAprovadoIntacto(hoje, planoAprovadoId, versaoAprovada);
    }

    private void assertPropostaPendenteComAprovadoIntacto(
            LocalDate data,
            UUID planoAprovadoId,
            int versaoAprovada
    ) {
        ResponseEntity<Map> corrente = restTemplate.exchange(
                "/planos?data=" + data,
                HttpMethod.GET,
                autenticado(),
                Map.class);
        assertThat(corrente.getStatusCode()).isEqualTo(OK);
        assertThat(corrente.getBody().get("status")).isEqualTo("RASCUNHO");
        assertThat(corrente.getBody().get("propostaId")).isNotNull();
        assertThat(corrente.getBody().get("propostaPendente")).isEqualTo(true);

        ResponseEntity<Map> aprovado = restTemplate.exchange(
                "/planos/" + planoAprovadoId,
                HttpMethod.GET,
                autenticado(),
                Map.class);
        assertThat(aprovado.getStatusCode()).isEqualTo(OK);
        assertThat(aprovado.getBody().get("status")).isEqualTo("APROVADO");
        assertThat(((Number) aprovado.getBody().get("versao")).intValue()).isEqualTo(versaoAprovada);
    }

    private void criarCapacidade(Map<String, Integer> horasPorDia) {
        List<Map<String, Object>> itens = horasPorDia.entrySet().stream()
                .map(e -> Map.<String, Object>of("diaSemana", e.getKey(), "horas", e.getValue()))
                .toList();
        ResponseEntity<List> response = restTemplate.exchange(
                "/capacidades/semanal",
                HttpMethod.POST,
                autenticadoJson(itens),
                List.class);
        assertThat(response.getStatusCode()).isEqualTo(CREATED);
    }

    private UUID criarProjeto(String nome, String descricao) {
        Map<String, Object> body = new HashMap<>();
        body.put("nome", nome);
        if (descricao != null) {
            body.put("descricao", descricao);
        }
        ResponseEntity<Map> response = restTemplate.exchange(
                "/projetos",
                HttpMethod.POST,
                autenticadoJson(body),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        return UUID.fromString(response.getBody().get("id").toString());
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
