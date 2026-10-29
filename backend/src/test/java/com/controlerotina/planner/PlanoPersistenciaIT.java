package com.controlerotina.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
class PlanoPersistenciaIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

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
    private UUID tarefaId;
    private UUID outraTarefaId;

    @BeforeEach
    void limparEPreparar() {
        jdbcTemplate.update("delete from bloco_plano");
        jdbcTemplate.update("delete from plano");
        jdbcTemplate.update("delete from tarefa");
        jdbcTemplate.update("delete from refresh_token");
        jdbcTemplate.update("delete from capacidade_override");
        jdbcTemplate.update("delete from capacidade_semanal");
        jdbcTemplate.update("delete from xp_evento");
        jdbcTemplate.update("delete from usuario");

        Instant agora = Instant.parse("2026-10-20T12:00:00Z");
        usuarioId = UUID.randomUUID();
        usuarioRepository.save(new Usuario(
                usuarioId,
                "planner-" + usuarioId + "@email.com",
                "$2a$10$hash",
                "Usuario Planner",
                agora,
                agora
        ));

        tarefaId = inserirTarefa("Tarefa A");
        outraTarefaId = inserirTarefa("Tarefa B");
    }

    @Test
    void devePersistirERecuperarPlanoComStatusRascunho() {
        LocalDate data = LocalDate.of(2026, 10, 20);
        Instant agora = Instant.parse("2026-10-20T09:00:00Z");
        UUID planoId = UUID.randomUUID();

        planoRepository.save(new Plano(
                planoId,
                usuarioId,
                data,
                1,
                StatusPlano.RASCUNHO,
                false,
                "Sem tempo neste dia",
                null,
                agora,
                agora,
                agora
        ));

        Plano encontrado = planoRepository.findByIdAndUsuarioId(planoId, usuarioId).orElseThrow();

        assertThat(encontrado.getUsuarioId()).isEqualTo(usuarioId);
        assertThat(encontrado.getData()).isEqualTo(data);
        assertThat(encontrado.getVersao()).isEqualTo(1);
        assertThat(encontrado.getStatus()).isEqualTo(StatusPlano.RASCUNHO);
        assertThat(encontrado.isTemConflito()).isFalse();
        assertThat(encontrado.getMensagem()).isEqualTo("Sem tempo neste dia");
        assertThat(planoRepository.findMaxVersaoByUsuarioIdAndData(usuarioId, data)).isEqualTo(1);
        assertThat(planoRepository.findFirstByUsuarioIdAndDataOrderByVersaoDesc(usuarioId, data))
                .map(Plano::getId)
                .contains(planoId);
    }

    @Test
    void devePersistirBlocosOrdenadosDoPlano() {
        LocalDate data = LocalDate.of(2026, 10, 20);
        Instant agora = Instant.parse("2026-10-20T09:00:00Z");
        UUID planoId = UUID.randomUUID();

        planoRepository.save(new Plano(
                planoId,
                usuarioId,
                data,
                1,
                StatusPlano.RASCUNHO,
                true,
                null,
                null,
                agora,
                agora,
                agora
        ));

        blocoPlanoRepository.save(new BlocoPlano(
                UUID.randomUUID(), planoId, tarefaId, 60, 1, agora, agora
        ));
        blocoPlanoRepository.save(new BlocoPlano(
                UUID.randomUUID(), planoId, outraTarefaId, 90, 2, agora, agora
        ));

        List<BlocoPlano> blocos = blocoPlanoRepository.findByPlanoIdOrderByOrdemAsc(planoId);

        assertThat(blocos).hasSize(2);
        assertThat(blocos.get(0).getTarefaId()).isEqualTo(tarefaId);
        assertThat(blocos.get(0).getDuracaoMinutos()).isEqualTo(60);
        assertThat(blocos.get(0).getOrdem()).isEqualTo(1);
        assertThat(blocos.get(1).getTarefaId()).isEqualTo(outraTarefaId);
        assertThat(blocos.get(1).getOrdem()).isEqualTo(2);
        assertThat(blocoPlanoRepository.existsByPlanoIdAndTarefaId(planoId, tarefaId)).isTrue();
    }

    @Test
    void deveRejeitarVersaoDuplicadaParaMesmoUsuarioEData() {
        LocalDate data = LocalDate.of(2026, 10, 20);
        Instant agora = Instant.parse("2026-10-20T09:00:00Z");

        planoRepository.saveAndFlush(new Plano(
                UUID.randomUUID(),
                usuarioId,
                data,
                1,
                StatusPlano.RASCUNHO,
                false,
                null,
                null,
                agora,
                agora,
                agora
        ));

        assertThatThrownBy(() -> planoRepository.saveAndFlush(new Plano(
                UUID.randomUUID(),
                usuarioId,
                data,
                1,
                StatusPlano.APROVADO,
                false,
                null,
                null,
                agora,
                agora,
                agora
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deveRejeitarMesmaTarefaDuasVezesNoMesmoPlano() {
        LocalDate data = LocalDate.of(2026, 10, 20);
        Instant agora = Instant.parse("2026-10-20T09:00:00Z");
        UUID planoId = UUID.randomUUID();

        planoRepository.saveAndFlush(new Plano(
                planoId,
                usuarioId,
                data,
                1,
                StatusPlano.RASCUNHO,
                false,
                null,
                null,
                agora,
                agora,
                agora
        ));
        blocoPlanoRepository.saveAndFlush(new BlocoPlano(
                UUID.randomUUID(), planoId, tarefaId, 60, 1, agora, agora
        ));

        assertThatThrownBy(() -> blocoPlanoRepository.saveAndFlush(new BlocoPlano(
                UUID.randomUUID(), planoId, tarefaId, 30, 2, agora, agora
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID inserirTarefa(String titulo) {
        UUID id = UUID.randomUUID();
        Instant agora = Instant.parse("2026-10-20T12:00:00Z");
        tarefaRepository.save(new Tarefa(
                id,
                usuarioId,
                titulo,
                "descricao",
                Prioridade.MEDIA,
                agora,
                StatusTarefa.PENDENTE,
                60,
                agora,
                agora
        ));
        return id;
    }
}
