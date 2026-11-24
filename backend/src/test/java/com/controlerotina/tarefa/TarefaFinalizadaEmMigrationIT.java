package com.controlerotina.tarefa;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Migra até a V26, semeia tarefas e eventos de XP como estavam antes e sobe para a última versão,
 * para provar o preenchimento de {@code tarefa.finalizada_em} pela V27.
 */
@Testcontainers
class TarefaFinalizadaEmMigrationIT {

    private static final Instant EVENTO_XP = Instant.parse("2026-10-20T15:00:00Z");

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    private static JdbcTemplate jdbc;
    private static final UUID USUARIO = UUID.randomUUID();
    private static final UUID FINALIZADA_COM_EVENTO = UUID.randomUUID();
    private static final UUID FINALIZADA_SEM_EVENTO = UUID.randomUUID();
    private static final UUID REABERTA_COM_EVENTO = UUID.randomUUID();
    private static final UUID PENDENTE = UUID.randomUUID();
    private static final UUID FINALIZADA_DELETADA_COM_EVENTO = UUID.randomUUID();

    @BeforeAll
    static void migrarSemearEMigrar() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(dataSource);

        Flyway.configure().dataSource(dataSource).target("26").load().migrate();

        Instant criada = Instant.parse("2026-10-01T12:00:00Z");
        jdbc.update("INSERT INTO usuario (id, email, senha_hash, nome, created_at, updated_at) VALUES (?, ?, 'x', 'U', ?, ?)",
                USUARIO, "mig-" + UUID.randomUUID() + "@email.com", Timestamp.from(criada), Timestamp.from(criada));
        semearTarefa(FINALIZADA_COM_EVENTO, "FINALIZADA", criada);
        semearTarefa(FINALIZADA_SEM_EVENTO, "FINALIZADA", criada);
        semearTarefa(REABERTA_COM_EVENTO, "PENDENTE", criada);
        semearTarefa(PENDENTE, "PENDENTE", criada);
        semearTarefa(FINALIZADA_DELETADA_COM_EVENTO, "FINALIZADA", criada);
        semearEventoXp(FINALIZADA_COM_EVENTO);
        semearEventoXp(REABERTA_COM_EVENTO);
        semearEventoXp(FINALIZADA_DELETADA_COM_EVENTO);

        Flyway.configure().dataSource(dataSource).load().migrate();
    }

    @Test
    void devePreencherComADataDoEventoDeXpQuandoFinalizadaComEvento() {
        assertThat(finalizadaEm(FINALIZADA_COM_EVENTO)).isEqualTo(EVENTO_XP);
    }

    @Test
    void deveDeixarSemDataQuandoFinalizadaSemEventoDeXp() {
        assertThat(finalizadaEm(FINALIZADA_SEM_EVENTO)).isNull();
    }

    @Test
    void naoDevePreencherTarefaReabertaMesmoComEventoDeXp() {
        assertThat(finalizadaEm(REABERTA_COM_EVENTO)).isNull();
    }

    @Test
    void naoDevePreencherTarefaNuncaFinalizada() {
        assertThat(finalizadaEm(PENDENTE)).isNull();
    }

    @Test
    void devePreencherTarefaFinalizadaMesmoComSoftDelete() {
        assertThat(finalizadaEm(FINALIZADA_DELETADA_COM_EVENTO)).isEqualTo(EVENTO_XP);
    }

    @Test
    void naoDeveAlterarOutrasColunasDaTarefaNaMigracao() {
        String status = jdbc.queryForObject("SELECT status FROM tarefa WHERE id = ?", String.class, FINALIZADA_COM_EVENTO);
        Timestamp atualizada = jdbc.queryForObject("SELECT updated_at FROM tarefa WHERE id = ?", Timestamp.class, FINALIZADA_COM_EVENTO);

        assertThat(status).isEqualTo("FINALIZADA");
        assertThat(atualizada.toInstant()).isEqualTo(Instant.parse("2026-10-01T12:00:00Z"));
    }

    private static Instant finalizadaEm(UUID tarefaId) {
        Timestamp valor = jdbc.queryForObject("SELECT finalizada_em FROM tarefa WHERE id = ?", Timestamp.class, tarefaId);
        return valor == null ? null : valor.toInstant();
    }

    private static void semearTarefa(UUID id, String status, Instant criada) {
        Timestamp ts = Timestamp.from(criada);
        jdbc.update("""
                INSERT INTO tarefa (id, usuario_id, titulo, descricao, prioridade, prazo, status, duracao_minutos, created_at, updated_at, deleted_at)
                VALUES (?, ?, 'T', '', 'MEDIA', NULL, ?, 60, ?, ?, ?)
                """, id, USUARIO, status, ts, ts, FINALIZADA_DELETADA_COM_EVENTO.equals(id) ? ts : null);
    }

    private static void semearEventoXp(UUID tarefaId) {
        jdbc.update("""
                INSERT INTO xp_evento (id, usuario_id, tipo, origem_id, quantidade, created_at)
                VALUES (?, ?, 'TAREFA_FINALIZADA', ?, 10, ?)
                """, UUID.randomUUID(), USUARIO, tarefaId, Timestamp.from(EVENTO_XP));
    }
}
