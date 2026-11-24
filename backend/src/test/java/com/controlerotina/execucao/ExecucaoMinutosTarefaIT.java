package com.controlerotina.execucao;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.controlerotina.execucao.entity.StatusExecucao;
import com.controlerotina.execucao.repository.ExecucaoRepository;
import com.controlerotina.execucao.repository.MinutoTarefaLinha;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ExecucaoMinutosTarefaIT {

    private static final Instant BASE = Instant.parse("2026-11-01T12:00:00Z");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    ExecucaoRepository execucaoRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private final AtomicInteger versaoPlano = new AtomicInteger();

    private UUID usuarioId;

    @BeforeEach
    void criarUsuario() {
        usuarioId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into usuario (id, email, senha_hash, nome, created_at, updated_at)
                values (?, ?, 'x', 'U', ?, ?)
                """, usuarioId, "minutos-" + usuarioId + "@email.com", Timestamp.from(BASE), Timestamp.from(BASE));
    }

    @Test
    void deveDevolverUmaLinhaPorExecucaoConcluidaComDiaEMinutoDoBloco() {
        UUID tarefa = semearTarefa(usuarioId);
        Instant concluido = Instant.parse("2026-11-03T14:00:00Z");
        semearExecucao(usuarioId, tarefa, LocalDate.of(2026, 11, 3), 30, "CONCLUIDO", concluido);
        semearExecucao(usuarioId, tarefa, LocalDate.of(2026, 11, 5), 45, "CONCLUIDO", Instant.parse("2026-11-05T14:00:00Z"));

        List<MinutoTarefaLinha> linhas = execucaoRepository.listarMinutosConcluidosPorTarefas(
                Set.of(tarefa), usuarioId, StatusExecucao.CONCLUIDO);

        assertThat(linhas).extracting(MinutoTarefaLinha::minutos).containsExactlyInAnyOrder(30, 45);
        assertThat(linhas).extracting(MinutoTarefaLinha::tarefaId).containsOnly(tarefa);
        assertThat(linhas).extracting(MinutoTarefaLinha::concluidoEm).contains(concluido);
        assertThat(linhas).extracting(MinutoTarefaLinha::planoData)
                .containsExactlyInAnyOrder(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 5));
    }

    @Test
    void naoDeveTrazerExecucaoEmAndamentoNemAbortada() {
        UUID tarefa = semearTarefa(usuarioId);
        semearExecucao(usuarioId, tarefa, LocalDate.of(2026, 11, 3), 30, "EM_ANDAMENTO", null);
        semearExecucao(usuarioId, tarefa, LocalDate.of(2026, 11, 4), 30, "ABORTADO", Instant.parse("2026-11-04T14:00:00Z"));

        assertThat(execucaoRepository.listarMinutosConcluidosPorTarefas(
                Set.of(tarefa), usuarioId, StatusExecucao.CONCLUIDO)).isEmpty();
    }

    @Test
    void naoDeveTrazerExecucaoDeOutroUsuario() {
        UUID outro = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into usuario (id, email, senha_hash, nome, created_at, updated_at)
                values (?, ?, 'x', 'Outro', ?, ?)
                """, outro, "outro-" + outro + "@email.com", Timestamp.from(BASE), Timestamp.from(BASE));
        UUID tarefaDoOutro = semearTarefa(outro);
        semearExecucao(outro, tarefaDoOutro, LocalDate.of(2026, 11, 3), 30, "CONCLUIDO", Instant.parse("2026-11-03T14:00:00Z"));

        assertThat(execucaoRepository.listarMinutosConcluidosPorTarefas(
                Set.of(tarefaDoOutro), usuarioId, StatusExecucao.CONCLUIDO)).isEmpty();
    }

    @Test
    void deveDevolverListaVaziaParaTarefaSemExecucao() {
        UUID tarefa = semearTarefa(usuarioId);

        assertThat(execucaoRepository.listarMinutosConcluidosPorTarefas(
                Set.of(tarefa), usuarioId, StatusExecucao.CONCLUIDO)).isEmpty();
    }

    @Test
    void deveFiltrarSoPelasTarefasPedidas() {
        UUID pedida = semearTarefa(usuarioId);
        UUID outra = semearTarefa(usuarioId);
        semearExecucao(usuarioId, pedida, LocalDate.of(2026, 11, 3), 30, "CONCLUIDO", Instant.parse("2026-11-03T14:00:00Z"));
        semearExecucao(usuarioId, outra, LocalDate.of(2026, 11, 3), 60, "CONCLUIDO", Instant.parse("2026-11-03T15:00:00Z"));

        List<MinutoTarefaLinha> linhas = execucaoRepository.listarMinutosConcluidosPorTarefas(
                Set.of(pedida), usuarioId, StatusExecucao.CONCLUIDO);

        assertThat(linhas).hasSize(1);
        assertThat(linhas.get(0).tarefaId()).isEqualTo(pedida);
        assertThat(linhas.get(0).minutos()).isEqualTo(30);
    }

    @Test
    void deveManterOInstanteDeConclusaoNaViradaDeMesEmUtc() {
        UUID tarefa = semearTarefa(usuarioId);
        Instant viradaUtc = Instant.parse("2026-12-01T02:30:00Z");
        semearExecucao(usuarioId, tarefa, LocalDate.of(2026, 11, 30), 30, "CONCLUIDO", viradaUtc);

        List<MinutoTarefaLinha> linhas = execucaoRepository.listarMinutosConcluidosPorTarefas(
                Set.of(tarefa), usuarioId, StatusExecucao.CONCLUIDO);

        assertThat(linhas).singleElement().satisfies(linha -> {
            assertThat(linha.concluidoEm()).isEqualTo(viradaUtc);
            assertThat(linha.planoData()).isEqualTo(LocalDate.of(2026, 11, 30));
        });
    }

    private UUID semearTarefa(UUID dono) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into tarefa (id, usuario_id, titulo, descricao, prioridade, prazo, status,
                                    duracao_minutos, created_at, updated_at)
                values (?, ?, 'T', '', 'MEDIA', null, 'PENDENTE', 120, ?, ?)
                """, id, dono, Timestamp.from(BASE), Timestamp.from(BASE));
        return id;
    }

    private void semearExecucao(UUID dono, UUID tarefaId, LocalDate dia, int minutosBloco, String status, Instant concluidoEm) {
        UUID planoId = UUID.randomUUID();
        UUID blocoId = UUID.randomUUID();
        Timestamp agora = Timestamp.from(BASE);
        jdbcTemplate.update("""
                insert into plano (id, usuario_id, data, versao, status, tem_conflito, itens_nao_alocados,
                                   gerado_em, created_at, updated_at)
                values (?, ?, ?, ?, 'TAREFA_AVULSA', false, '[]'::jsonb, ?, ?, ?)
                """, planoId, dono, java.sql.Date.valueOf(dia), versaoPlano.incrementAndGet(), agora, agora, agora);
        jdbcTemplate.update("""
                insert into bloco_plano (id, plano_id, tarefa_id, duracao_minutos, ordem, created_at, updated_at)
                values (?, ?, ?, ?, 1, ?, ?)
                """, blocoId, planoId, tarefaId, minutosBloco, agora, agora);
        jdbcTemplate.update("""
                insert into execucao (id, usuario_id, bloco_plano_id, status, iniciado_em, concluido_em, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), dono, blocoId, status, agora,
                concluidoEm == null ? null : Timestamp.from(concluidoEm), agora, agora);
    }
}
