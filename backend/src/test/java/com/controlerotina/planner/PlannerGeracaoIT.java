package com.controlerotina.planner;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
import com.controlerotina.planner.entity.BlocoPlano;
import com.controlerotina.planner.entity.Plano;
import com.controlerotina.planner.entity.StatusPlano;
import com.controlerotina.planner.motor.MotorAlocacao;
import com.controlerotina.planner.repository.BlocoPlanoRepository;
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
class PlannerGeracaoIT {

    private static final LocalDate DIA_D = LocalDate.of(2026, 10, 20);
    private static final Instant AGORA = Instant.parse("2026-10-20T12:00:00Z");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    PlannerGeracaoService plannerGeracaoService;

    @Autowired
    PlanoRepository planoRepository;

    @Autowired
    BlocoPlanoRepository blocoPlanoRepository;

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    TarefaRepository tarefaRepository;

    @Autowired
    CapacidadeOverrideRepository capacidadeOverrideRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private UUID usuarioId;

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

        usuarioId = UUID.randomUUID();
        usuarioRepository.save(new Usuario(
                usuarioId,
                "motor-" + usuarioId + "@email.com",
                "$2a$10$hash",
                "Usuario Motor",
                AGORA,
                AGORA
        ));
    }

    @Test
    void deveGerarPlanoVazioQuandoCapacidadeVazia() {
        Tarefa elegivel = salvarTarefa("Elegivel", Prioridade.ALTA, StatusTarefa.PENDENTE, 1, null);

        Plano plano = plannerGeracaoService.gerar(usuarioId, DIA_D);

        assertThat(plano.getVersao()).isEqualTo(1);
        assertThat(plano.getStatus()).isEqualTo(StatusPlano.RASCUNHO);
        assertThat(plano.isTemConflito()).isFalse();
        assertThat(plano.getMensagem()).isEqualTo(MotorAlocacao.MENSAGEM_SEM_TEMPO);
        assertThat(blocoPlanoRepository.findByPlanoIdOrderByOrdemAsc(plano.getId())).isEmpty();
        assertThat(tarefaRepository.findById(elegivel.getId())).isPresent();
    }

    @Test
    void deveGerarPlanoVazioQuandoCapacidadeZero() {
        salvarOverride(0);
        salvarTarefa("Elegivel", Prioridade.ALTA, StatusTarefa.PENDENTE, 1, null);

        Plano plano = plannerGeracaoService.gerar(usuarioId, DIA_D);

        assertThat(plano.isTemConflito()).isFalse();
        assertThat(plano.getMensagem()).isEqualTo(MotorAlocacao.MENSAGEM_SEM_TEMPO);
        assertThat(blocoPlanoRepository.findByPlanoIdOrderByOrdemAsc(plano.getId())).isEmpty();
    }

    @Test
    void devePersistirNovaVersaoComBlocosOrdenadosPorScore() {
        salvarOverride(3);
        Tarefa baixa = salvarTarefa("Baixa", Prioridade.BAIXA, StatusTarefa.PENDENTE, 1, AGORA);
        Tarefa alta = salvarTarefa("Alta", Prioridade.ALTA, StatusTarefa.PENDENTE, 1, AGORA.plusSeconds(10));

        Plano plano = plannerGeracaoService.gerar(usuarioId, DIA_D);

        List<BlocoPlano> blocos = blocoPlanoRepository.findByPlanoIdOrderByOrdemAsc(plano.getId());
        assertThat(plano.getVersao()).isEqualTo(1);
        assertThat(plano.getStatus()).isEqualTo(StatusPlano.RASCUNHO);
        assertThat(plano.isTemConflito()).isFalse();
        assertThat(plano.getMensagem()).isNull();
        assertThat(blocos).hasSize(2);
        assertThat(blocos.get(0).getTarefaId()).isEqualTo(alta.getId());
        assertThat(blocos.get(0).getDuracaoMinutos()).isEqualTo(60);
        assertThat(blocos.get(0).getOrdem()).isEqualTo(1);
        assertThat(blocos.get(1).getTarefaId()).isEqualTo(baixa.getId());
        assertThat(blocos.get(1).getOrdem()).isEqualTo(2);
    }

    @Test
    void deveMarcarConflitoQuandoTruncaTarefa() {
        salvarOverride(1);
        Tarefa grande = salvarTarefa("Grande", Prioridade.ALTA, StatusTarefa.PENDENTE, 3, AGORA);

        Plano plano = plannerGeracaoService.gerar(usuarioId, DIA_D);

        List<BlocoPlano> blocos = blocoPlanoRepository.findByPlanoIdOrderByOrdemAsc(plano.getId());
        assertThat(plano.isTemConflito()).isTrue();
        assertThat(plano.getMensagem()).isEqualTo(MotorAlocacao.MENSAGEM_PARCIAL);
        assertThat(blocos).hasSize(1);
        assertThat(blocos.get(0).getTarefaId()).isEqualTo(grande.getId());
        assertThat(blocos.get(0).getDuracaoMinutos()).isEqualTo(60);
    }

    @Test
    void deveIgnorarTarefasNaoElegiveis() {
        salvarOverride(4);
        Tarefa pendente = salvarTarefa("Pendente", Prioridade.MEDIA, StatusTarefa.PENDENTE, 1, AGORA);
        salvarTarefa("Finalizada", Prioridade.ALTA, StatusTarefa.FINALIZADA, 1, AGORA);
        salvarTarefa("Cancelada", Prioridade.ALTA, StatusTarefa.CANCELADA, 1, AGORA);
        Tarefa deletada = salvarTarefa("Deletada", Prioridade.ALTA, StatusTarefa.PENDENTE, 1, AGORA);
        deletada.softDelete(AGORA);
        tarefaRepository.save(deletada);

        Plano plano = plannerGeracaoService.gerar(usuarioId, DIA_D);

        List<BlocoPlano> blocos = blocoPlanoRepository.findByPlanoIdOrderByOrdemAsc(plano.getId());
        assertThat(blocos).hasSize(1);
        assertThat(blocos.get(0).getTarefaId()).isEqualTo(pendente.getId());
    }

    @Test
    void deveIncrementarVersaoSemMutarAnterior() {
        salvarOverride(2);
        salvarTarefa("Tarefa", Prioridade.MEDIA, StatusTarefa.PENDENTE, 1, AGORA);

        Plano v1 = plannerGeracaoService.gerar(usuarioId, DIA_D);
        Plano v2 = plannerGeracaoService.gerar(usuarioId, DIA_D);

        assertThat(v1.getVersao()).isEqualTo(1);
        assertThat(v2.getVersao()).isEqualTo(2);
        assertThat(planoRepository.findById(v1.getId())).isPresent();
        assertThat(blocoPlanoRepository.findByPlanoIdOrderByOrdemAsc(v1.getId())).hasSize(1);
        assertThat(blocoPlanoRepository.findByPlanoIdOrderByOrdemAsc(v2.getId())).hasSize(1);
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

    private Tarefa salvarTarefa(
            String titulo,
            Prioridade prioridade,
            StatusTarefa status,
            int duracaoHoras,
            Instant createdAt
    ) {
        Instant criado = createdAt != null ? createdAt : AGORA;
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
                status,
                duracaoHoras,
                criado,
                criado
        ));
    }
}
