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
    ExecucaoRepository execucaoRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private UUID usuarioId;

    @BeforeEach
    void limparEPreparar() {
        jdbcTemplate.update("delete from execucao");
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
        Tarefa elegivel = salvarTarefa("Elegivel", Prioridade.ALTA, StatusTarefa.PENDENTE, 60, null);

        Plano plano = plannerGeracaoService.gerar(usuarioId, DIA_D);

        assertThat(plano.getVersao()).isEqualTo(1);
        assertThat(plano.getStatus()).isEqualTo(StatusPlano.RASCUNHO);
        // Sem capacidade na janela: nada alocado e saldo fora do prazo → conflito (#23)
        assertThat(plano.isTemConflito()).isTrue();
        assertThat(plano.getMensagem()).contains("Elegivel").contains("60");
        assertThat(blocoPlanoRepository.findByPlanoIdOrderByOrdemAsc(plano.getId())).isEmpty();
        assertThat(tarefaRepository.findById(elegivel.getId())).isPresent();
    }

    @Test
    void deveGerarPlanoVazioQuandoCapacidadeZero() {
        salvarOverride(0);
        salvarTarefa("Elegivel", Prioridade.ALTA, StatusTarefa.PENDENTE, 60, null);

        Plano plano = plannerGeracaoService.gerar(usuarioId, DIA_D);

        assertThat(plano.isTemConflito()).isTrue();
        assertThat(plano.getMensagem()).contains("Elegivel").contains("60");
        assertThat(blocoPlanoRepository.findByPlanoIdOrderByOrdemAsc(plano.getId())).isEmpty();
    }

    @Test
    void devePersistirNovaVersaoComBlocosOrdenadosPorScore() {
        salvarOverride(3);
        Tarefa baixa = salvarTarefa("Baixa", Prioridade.BAIXA, StatusTarefa.PENDENTE, 60, AGORA);
        Tarefa alta = salvarTarefa("Alta", Prioridade.ALTA, StatusTarefa.PENDENTE, 60, AGORA.plusSeconds(10));

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
    void t4_planoGeradoComTemConflitoEMensagemQuandoNaoCabeAtePrazo() {
        salvarOverride(1);
        Tarefa grande = salvarTarefa("Grande", Prioridade.ALTA, StatusTarefa.PENDENTE, 180, AGORA);

        Plano plano = plannerGeracaoService.gerar(usuarioId, DIA_D);

        List<BlocoPlano> blocos = blocoPlanoRepository.findByPlanoIdOrderByOrdemAsc(plano.getId());
        assertThat(blocos).hasSize(1);
        assertThat(blocos.get(0).getTarefaId()).isEqualTo(grande.getId());
        assertThat(blocos.get(0).getDuracaoMinutos()).isEqualTo(60);
        assertThat(plano.isTemConflito()).isTrue();
        assertThat(plano.getMensagem())
                .contains("Grande")
                .contains("120")
                .contains("25/10/2026");
    }

    @Test
    void deveGerarRascunhosEmVariosDiasComMesmoPropostaId() {
        for (int i = 0; i < 3; i++) {
            capacidadeOverrideRepository.save(new CapacidadeOverride(
                    UUID.randomUUID(),
                    usuarioId,
                    DIA_D.plusDays(i),
                    1,
                    true,
                    AGORA,
                    AGORA
            ));
        }
        Tarefa tarefa = salvarTarefa("Janela", Prioridade.ALTA, StatusTarefa.PENDENTE, 180, AGORA);

        Plano primeiro = plannerGeracaoService.gerar(usuarioId, DIA_D);

        assertThat(primeiro.getPropostaId()).isNotNull();
        assertThat(primeiro.getStatus()).isEqualTo(StatusPlano.RASCUNHO);

        List<Plano> daProposta = planoRepository.findAll().stream()
                .filter(p -> primeiro.getPropostaId().equals(p.getPropostaId()))
                .toList();
        assertThat(daProposta).hasSizeGreaterThanOrEqualTo(3);
        assertThat(daProposta).allMatch(p -> p.getStatus() == StatusPlano.RASCUNHO);
        assertThat(daProposta).extracting(Plano::getData)
                .contains(DIA_D, DIA_D.plusDays(1), DIA_D.plusDays(2));

        int totalMinutos = daProposta.stream()
                .flatMap(p -> blocoPlanoRepository.findByPlanoIdOrderByOrdemAsc(p.getId()).stream())
                .filter(b -> b.getTarefaId().equals(tarefa.getId()))
                .mapToInt(BlocoPlano::getDuracaoMinutos)
                .sum();
        assertThat(totalMinutos).isEqualTo(180);
    }

    @Test
    void deveIgnorarTarefasNaoElegiveis() {
        salvarOverride(4);
        Tarefa pendente = salvarTarefa("Pendente", Prioridade.MEDIA, StatusTarefa.PENDENTE, 60, AGORA);
        salvarTarefa("Finalizada", Prioridade.ALTA, StatusTarefa.FINALIZADA, 60, AGORA);
        salvarTarefa("Cancelada", Prioridade.ALTA, StatusTarefa.CANCELADA, 60, AGORA);
        Tarefa deletada = salvarTarefa("Deletada", Prioridade.ALTA, StatusTarefa.PENDENTE, 60, AGORA);
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
        salvarTarefa("Tarefa", Prioridade.MEDIA, StatusTarefa.PENDENTE, 60, AGORA);

        Plano v1 = plannerGeracaoService.gerar(usuarioId, DIA_D);
        Plano v2 = plannerGeracaoService.gerar(usuarioId, DIA_D);

        assertThat(v1.getVersao()).isEqualTo(1);
        assertThat(v2.getVersao()).isEqualTo(2);
        assertThat(planoRepository.findById(v1.getId())).isPresent();
        assertThat(blocoPlanoRepository.findByPlanoIdOrderByOrdemAsc(v1.getId())).hasSize(1);
        assertThat(blocoPlanoRepository.findByPlanoIdOrderByOrdemAsc(v2.getId())).hasSize(1);
    }

    @Test
    void deveGerarRascunhoComSaldoAposBlocoParcialConcluido() {
        salvarOverride(4);
        Tarefa tarefa = salvarTarefa("Parcial", Prioridade.ALTA, StatusTarefa.EM_PROGRESSO, 180, AGORA);
        salvarBlocoConcluido(tarefa.getId(), 60);

        Plano plano = plannerGeracaoService.gerar(usuarioId, DIA_D);

        List<BlocoPlano> blocos = blocoPlanoRepository.findByPlanoIdOrderByOrdemAsc(plano.getId());
        assertThat(blocos).hasSize(2);
        assertThat(blocos).allMatch(b -> b.getTarefaId().equals(tarefa.getId()));
        assertThat(blocos.get(0).getDuracaoMinutos()).isEqualTo(90);
        assertThat(blocos.get(1).getDuracaoMinutos()).isEqualTo(30);
    }

    private void salvarBlocoConcluido(UUID tarefaId, int duracaoMinutos) {
        Plano planoAnterior = planoRepository.save(new Plano(
                UUID.randomUUID(),
                usuarioId,
                DIA_D.minusDays(1),
                1,
                StatusPlano.APROVADO,
                false,
                null,
                null,
                AGORA,
                AGORA,
                AGORA
        ));
        BlocoPlano bloco = blocoPlanoRepository.save(new BlocoPlano(
                UUID.randomUUID(),
                planoAnterior.getId(),
                tarefaId,
                duracaoMinutos,
                1,
                AGORA,
                AGORA
        ));
        execucaoRepository.save(new Execucao(
                UUID.randomUUID(),
                usuarioId,
                bloco.getId(),
                StatusExecucao.CONCLUIDO,
                AGORA.minusSeconds(3600),
                AGORA,
                null,
                AGORA,
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

    private Tarefa salvarTarefa(
            String titulo,
            Prioridade prioridade,
            StatusTarefa status,
            int duracaoMinutos,
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
                duracaoMinutos,
                criado,
                criado
        ));
    }
}
