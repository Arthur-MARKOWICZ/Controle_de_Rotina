package com.controlerotina.execucao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.controlerotina.execucao.entity.Execucao;
import com.controlerotina.execucao.entity.StatusExecucao;
import com.controlerotina.execucao.repository.ExecucaoRepository;
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
@Testcontainers
class ExecucaoPersistenciaIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    ExecucaoRepository execucaoRepository;

    @Autowired
    PlanoRepository planoRepository;

    @Autowired
    BlocoPlanoRepository blocoPlanoRepository;

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    TarefaRepository tarefaRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private UUID usuarioId;
    private UUID blocoPlanoId;

    @BeforeEach
    void limparEPreparar() {
        jdbcTemplate.update("delete from execucao");
        jdbcTemplate.update("delete from bloco_plano");
        jdbcTemplate.update("delete from plano");
        jdbcTemplate.update("delete from tarefa");
        jdbcTemplate.update("delete from refresh_token");
        jdbcTemplate.update("delete from capacidade_override");
        jdbcTemplate.update("delete from capacidade_semanal");
        jdbcTemplate.update("delete from usuario");

        Instant agora = Instant.parse("2026-10-27T12:00:00Z");
        usuarioId = UUID.randomUUID();
        usuarioRepository.save(new Usuario(
                usuarioId,
                "execucao-" + usuarioId + "@email.com",
                "$2a$10$hash",
                "Usuario Execucao",
                agora,
                agora
        ));

        UUID tarefaId = UUID.randomUUID();
        tarefaRepository.save(new Tarefa(
                tarefaId,
                usuarioId,
                "Tarefa execucao",
                "descricao",
                Prioridade.MEDIA,
                agora,
                StatusTarefa.PENDENTE,
                1,
                agora,
                agora
        ));

        UUID planoId = UUID.randomUUID();
        planoRepository.save(new Plano(
                planoId,
                usuarioId,
                LocalDate.of(2026, 10, 27),
                1,
                StatusPlano.APROVADO,
                false,
                null,
                agora,
                agora,
                agora
        ));

        blocoPlanoId = UUID.randomUUID();
        blocoPlanoRepository.save(new BlocoPlano(
                blocoPlanoId, planoId, tarefaId, 60, 1, agora, agora
        ));
    }

    @Test
    void devePersistirERecuperarExecucaoPorBloco() {
        Instant agora = Instant.parse("2026-10-27T14:00:00Z");
        UUID execucaoId = UUID.randomUUID();

        execucaoRepository.save(new Execucao(
                execucaoId,
                usuarioId,
                blocoPlanoId,
                StatusExecucao.EM_ANDAMENTO,
                agora,
                null,
                null,
                agora,
                agora
        ));

        Execucao encontrada = execucaoRepository.findByBlocoPlanoId(blocoPlanoId).orElseThrow();

        assertThat(encontrada.getId()).isEqualTo(execucaoId);
        assertThat(encontrada.getUsuarioId()).isEqualTo(usuarioId);
        assertThat(encontrada.getBlocoPlanoId()).isEqualTo(blocoPlanoId);
        assertThat(encontrada.getStatus()).isEqualTo(StatusExecucao.EM_ANDAMENTO);
        assertThat(encontrada.getIniciadoEm()).isEqualTo(agora);
        assertThat(encontrada.getConcluidoEm()).isNull();
        assertThat(encontrada.getAbortadoEm()).isNull();
        assertThat(execucaoRepository.findByIdAndUsuarioId(execucaoId, usuarioId)).isPresent();
        assertThat(execucaoRepository.existsByBlocoPlanoId(blocoPlanoId)).isTrue();
    }

    @Test
    void deveRejeitarDuasExecucoesParaOMesmoBloco() {
        Instant agora = Instant.parse("2026-10-27T14:00:00Z");

        execucaoRepository.saveAndFlush(new Execucao(
                UUID.randomUUID(),
                usuarioId,
                blocoPlanoId,
                StatusExecucao.EM_ANDAMENTO,
                agora,
                null,
                null,
                agora,
                agora
        ));

        assertThatThrownBy(() -> execucaoRepository.saveAndFlush(new Execucao(
                UUID.randomUUID(),
                usuarioId,
                blocoPlanoId,
                StatusExecucao.ABORTADO,
                agora,
                null,
                agora,
                agora,
                agora
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }
}
