package com.controlerotina.planner;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.controlerotina.capacidade.entity.CapacidadeOverride;
import com.controlerotina.capacidade.repository.CapacidadeOverrideRepository;
import com.controlerotina.planner.entity.Plano;
import com.controlerotina.planner.entity.StatusPlano;
import com.controlerotina.planner.job.PlannerGeracaoDiariaJob;
import com.controlerotina.planner.repository.PlanoRepository;
import com.controlerotina.planner.service.PlannerGeracaoService;
import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;
import com.controlerotina.tarefa.repository.TarefaRepository;
import com.controlerotina.usuario.entity.Usuario;
import com.controlerotina.usuario.repository.UsuarioRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PlannerGeracaoDiariaJobIT {

    private static final LocalDate DIA_D = LocalDate.of(2026, 10, 20);
    private static final Instant AGORA = Instant.parse("2026-10-20T12:00:00Z");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    PlannerGeracaoDiariaJob plannerGeracaoDiariaJob;

    @Autowired
    PlannerGeracaoService plannerGeracaoService;

    @Autowired
    PlanoRepository planoRepository;

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    TarefaRepository tarefaRepository;

    @Autowired
    CapacidadeOverrideRepository capacidadeOverrideRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void limpar() {
        jdbcTemplate.update("delete from bloco_plano");
        jdbcTemplate.update("delete from plano");
        jdbcTemplate.update("delete from tarefa");
        jdbcTemplate.update("delete from refresh_token");
        jdbcTemplate.update("delete from capacidade_override");
        jdbcTemplate.update("delete from capacidade_semanal");
        jdbcTemplate.update("delete from usuario");
    }

    @Test
    void deveGerarRascunhoParaUsuarioSemPlanoAprovado() {
        UUID usuarioId = salvarUsuario("sem-aprovado");
        salvarOverride(usuarioId, 2);
        salvarTarefa(usuarioId, "Tarefa");

        plannerGeracaoDiariaJob.gerarPlanosPara(DIA_D);

        Plano corrente = planoRepository
                .findFirstByUsuarioIdAndDataOrderByVersaoDesc(usuarioId, DIA_D)
                .orElseThrow();
        assertThat(corrente.getStatus()).isEqualTo(StatusPlano.RASCUNHO);
        assertThat(corrente.getVersao()).isEqualTo(1);
    }

    @Test
    void devePularUsuarioComPlanoAprovado() {
        UUID usuarioId = salvarUsuario("com-aprovado");
        salvarOverride(usuarioId, 2);
        salvarTarefa(usuarioId, "Tarefa");
        Plano gerado = plannerGeracaoService.gerar(usuarioId, DIA_D);
        jdbcTemplate.update(
                "update plano set status = ? where id = ?",
                StatusPlano.APROVADO.name(),
                gerado.getId()
        );

        plannerGeracaoDiariaJob.gerarPlanosPara(DIA_D);

        assertThat(planoRepository.findMaxVersaoByUsuarioIdAndData(usuarioId, DIA_D)).isEqualTo(1);
        assertThat(planoRepository.findById(gerado.getId()))
                .get()
                .extracting(Plano::getStatus)
                .isEqualTo(StatusPlano.APROVADO);
    }

    @Test
    void deveIncrementarVersaoQuandoJaExisteRascunho() {
        UUID usuarioId = salvarUsuario("com-rascunho");
        salvarOverride(usuarioId, 2);
        salvarTarefa(usuarioId, "Tarefa");
        Plano v1 = plannerGeracaoService.gerar(usuarioId, DIA_D);

        plannerGeracaoDiariaJob.gerarPlanosPara(DIA_D);

        Plano corrente = planoRepository
                .findFirstByUsuarioIdAndDataOrderByVersaoDesc(usuarioId, DIA_D)
                .orElseThrow();
        assertThat(v1.getVersao()).isEqualTo(1);
        assertThat(corrente.getVersao()).isEqualTo(2);
        assertThat(corrente.getStatus()).isEqualTo(StatusPlano.RASCUNHO);
        assertThat(planoRepository.findById(v1.getId())).isPresent();
    }

    @Test
    void deveProcessarUsuariosEmSequenciaGerandoParaQuemNaoTemAprovado() {
        UUID semAprovado = salvarUsuario("a-sem");
        UUID comAprovado = salvarUsuario("b-com");
        salvarOverride(semAprovado, 2);
        salvarOverride(comAprovado, 2);
        salvarTarefa(semAprovado, "Tarefa A");
        salvarTarefa(comAprovado, "Tarefa B");

        Plano aprovado = plannerGeracaoService.gerar(comAprovado, DIA_D);
        jdbcTemplate.update(
                "update plano set status = ? where id = ?",
                StatusPlano.APROVADO.name(),
                aprovado.getId()
        );

        plannerGeracaoDiariaJob.gerarPlanosPara(DIA_D);

        assertThat(planoRepository.findMaxVersaoByUsuarioIdAndData(semAprovado, DIA_D)).isEqualTo(1);
        assertThat(planoRepository.findFirstByUsuarioIdAndDataOrderByVersaoDesc(semAprovado, DIA_D))
                .get()
                .extracting(Plano::getStatus)
                .isEqualTo(StatusPlano.RASCUNHO);

        assertThat(planoRepository.findMaxVersaoByUsuarioIdAndData(comAprovado, DIA_D)).isEqualTo(1);
        assertThat(planoRepository.findById(aprovado.getId()))
                .get()
                .extracting(Plano::getStatus)
                .isEqualTo(StatusPlano.APROVADO);
    }

    private UUID salvarUsuario(String sufixo) {
        UUID id = UUID.randomUUID();
        usuarioRepository.save(new Usuario(
                id,
                "job-" + sufixo + "-" + id + "@email.com",
                "$2a$10$hash",
                "Usuario Job",
                AGORA,
                AGORA
        ));
        return id;
    }

    private void salvarOverride(UUID usuarioId, int horas) {
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

    private void salvarTarefa(UUID usuarioId, String titulo) {
        Instant prazo = DIA_D.plusDays(5).atTime(12, 0)
                .atZone(java.time.ZoneId.of("America/Sao_Paulo"))
                .toInstant();
        tarefaRepository.save(new Tarefa(
                UUID.randomUUID(),
                usuarioId,
                titulo,
                "descricao",
                Prioridade.MEDIA,
                prazo,
                StatusTarefa.PENDENTE,
                1,
                AGORA,
                AGORA
        ));
    }
}
