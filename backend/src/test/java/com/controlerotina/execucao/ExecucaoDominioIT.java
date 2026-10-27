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
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.controlerotina.execucao.dto.ResultadoInicio;
import com.controlerotina.execucao.entity.Execucao;
import com.controlerotina.execucao.entity.StatusExecucao;
import com.controlerotina.execucao.repository.ExecucaoRepository;
import com.controlerotina.execucao.service.ExecucaoService;
import com.controlerotina.planner.entity.BlocoPlano;
import com.controlerotina.planner.entity.Plano;
import com.controlerotina.planner.entity.StatusPlano;
import com.controlerotina.planner.repository.BlocoPlanoRepository;
import com.controlerotina.planner.repository.PlanoRepository;
import com.controlerotina.shared.exception.ExecucaoJaConcluidaException;
import com.controlerotina.shared.exception.ExecucaoJaEmAndamentoException;
import com.controlerotina.shared.exception.ExecucaoNaoEmAndamentoException;
import com.controlerotina.shared.exception.PlanoNaoExecutavelException;
import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;
import com.controlerotina.tarefa.repository.TarefaRepository;
import com.controlerotina.usuario.entity.Usuario;
import com.controlerotina.usuario.repository.UsuarioRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ExecucaoDominioIT {

    private static final Instant AGORA = Instant.parse("2026-10-27T12:00:00Z");
    private static final LocalDate DIA = LocalDate.of(2026, 10, 27);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    ExecucaoService execucaoService;

    @Autowired
    ExecucaoRepository execucaoRepository;

    @Autowired
    PlanoRepository planoRepository;

    @Autowired
    BlocoPlanoRepository blocoPlanoRepository;

    @Autowired
    TarefaRepository tarefaRepository;

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private UUID usuarioId;
    private UUID tarefaId;
    private UUID planoId;
    private UUID blocoId;

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
                "exec-dom-" + usuarioId + "@email.com",
                "$2a$10$hash",
                "Usuario Dominio",
                AGORA,
                AGORA
        ));

        tarefaId = UUID.randomUUID();
        tarefaRepository.save(new Tarefa(
                tarefaId,
                usuarioId,
                "Tarefa dominio",
                "descricao",
                Prioridade.MEDIA,
                AGORA,
                StatusTarefa.PENDENTE,
                1,
                AGORA,
                AGORA
        ));

        planoId = UUID.randomUUID();
        planoRepository.save(new Plano(
                planoId,
                usuarioId,
                DIA,
                1,
                StatusPlano.APROVADO,
                false,
                null,
                AGORA,
                AGORA,
                AGORA
        ));

        blocoId = UUID.randomUUID();
        blocoPlanoRepository.save(new BlocoPlano(
                blocoId, planoId, tarefaId, 60, 1, AGORA, AGORA
        ));
    }

    @Test
    void t1_iniciarCriaExecucaoRejeitaEmAndamentoEConcluidoEReiniciaAbortado() {
        ResultadoInicio primeiro = execucaoService.iniciar(usuarioId, blocoId);

        assertThat(primeiro.criada()).isTrue();
        assertThat(primeiro.execucao().getStatus()).isEqualTo(StatusExecucao.EM_ANDAMENTO);
        assertThat(primeiro.execucao().getIniciadoEm()).isNotNull();
        assertThat(primeiro.execucao().getAbortadoEm()).isNull();

        assertThatThrownBy(() -> execucaoService.iniciar(usuarioId, blocoId))
                .isInstanceOf(ExecucaoJaEmAndamentoException.class);

        Execucao emAndamento = execucaoRepository.findByBlocoPlanoId(blocoId).orElseThrow();
        emAndamento.concluir(AGORA);
        execucaoRepository.save(emAndamento);

        assertThatThrownBy(() -> execucaoService.iniciar(usuarioId, blocoId))
                .isInstanceOf(ExecucaoJaConcluidaException.class);

        UUID blocoAbortavel = UUID.randomUUID();
        UUID tarefa2 = salvarTarefa("Tarefa abortavel", StatusTarefa.PENDENTE, 1);
        blocoPlanoRepository.save(new BlocoPlano(
                blocoAbortavel, planoId, tarefa2, 30, 2, AGORA, AGORA
        ));
        ResultadoInicio criada = execucaoService.iniciar(usuarioId, blocoAbortavel);
        execucaoService.abortar(usuarioId, criada.execucao().getId());

        ResultadoInicio reinicio = execucaoService.iniciar(usuarioId, blocoAbortavel);
        assertThat(reinicio.criada()).isFalse();
        assertThat(reinicio.execucao().getStatus()).isEqualTo(StatusExecucao.EM_ANDAMENTO);
        assertThat(reinicio.execucao().getAbortadoEm()).isNull();
        assertThat(reinicio.execucao().getId()).isEqualTo(criada.execucao().getId());
    }

    @Test
    void t2_iniciarAtualizaTarefaPendenteOuAtrasadaSemAlterarFinalizadaOuCancelada() {
        execucaoService.iniciar(usuarioId, blocoId);
        assertThat(tarefaRepository.findById(tarefaId).orElseThrow().getStatus())
                .isEqualTo(StatusTarefa.EM_PROGRESSO);

        UUID tarefaAtrasada = salvarTarefa("Atrasada", StatusTarefa.ATRASADA, 1);
        UUID blocoAtrasada = UUID.randomUUID();
        blocoPlanoRepository.save(new BlocoPlano(
                blocoAtrasada, planoId, tarefaAtrasada, 30, 2, AGORA, AGORA
        ));
        execucaoService.iniciar(usuarioId, blocoAtrasada);
        assertThat(tarefaRepository.findById(tarefaAtrasada).orElseThrow().getStatus())
                .isEqualTo(StatusTarefa.EM_PROGRESSO);

        UUID tarefaFinalizada = salvarTarefa("Finalizada", StatusTarefa.FINALIZADA, 1);
        UUID blocoFinalizada = UUID.randomUUID();
        blocoPlanoRepository.save(new BlocoPlano(
                blocoFinalizada, planoId, tarefaFinalizada, 30, 3, AGORA, AGORA
        ));
        execucaoService.iniciar(usuarioId, blocoFinalizada);
        assertThat(tarefaRepository.findById(tarefaFinalizada).orElseThrow().getStatus())
                .isEqualTo(StatusTarefa.FINALIZADA);

        UUID tarefaCancelada = salvarTarefa("Cancelada", StatusTarefa.CANCELADA, 1);
        UUID blocoCancelada = UUID.randomUUID();
        blocoPlanoRepository.save(new BlocoPlano(
                blocoCancelada, planoId, tarefaCancelada, 30, 4, AGORA, AGORA
        ));
        execucaoService.iniciar(usuarioId, blocoCancelada);
        assertThat(tarefaRepository.findById(tarefaCancelada).orElseThrow().getStatus())
                .isEqualTo(StatusTarefa.CANCELADA);

        UUID tarefaJaProgresso = salvarTarefa("Ja progresso", StatusTarefa.EM_PROGRESSO, 1);
        UUID blocoProgresso = UUID.randomUUID();
        blocoPlanoRepository.save(new BlocoPlano(
                blocoProgresso, planoId, tarefaJaProgresso, 30, 5, AGORA, AGORA
        ));
        execucaoService.iniciar(usuarioId, blocoProgresso);
        assertThat(tarefaRepository.findById(tarefaJaProgresso).orElseThrow().getStatus())
                .isEqualTo(StatusTarefa.EM_PROGRESSO);
    }

    @Test
    void t3_concluirSomaDuracoesEFinalizaSomenteQuandoCompleto() {
        Tarefa tarefaLonga = tarefaRepository.findById(tarefaId).orElseThrow();
        tarefaLonga.atualizar(null, null, null, null, null, 2, AGORA);
        tarefaRepository.save(tarefaLonga);

        UUID planoDia2 = UUID.randomUUID();
        planoRepository.save(new Plano(
                planoDia2,
                usuarioId,
                DIA.plusDays(1),
                1,
                StatusPlano.APROVADO,
                false,
                null,
                AGORA,
                AGORA,
                AGORA
        ));
        UUID bloco2 = UUID.randomUUID();
        blocoPlanoRepository.save(new BlocoPlano(
                bloco2, planoDia2, tarefaId, 60, 1, AGORA, AGORA
        ));

        ResultadoInicio inicio1 = execucaoService.iniciar(usuarioId, blocoId);
        Execucao parcial = execucaoService.concluir(usuarioId, inicio1.execucao().getId());

        assertThat(parcial.getStatus()).isEqualTo(StatusExecucao.CONCLUIDO);
        assertThat(parcial.getConcluidoEm()).isNotNull();
        assertThat(tarefaRepository.findById(tarefaId).orElseThrow().getStatus())
                .isEqualTo(StatusTarefa.EM_PROGRESSO);

        ResultadoInicio inicio2 = execucaoService.iniciar(usuarioId, bloco2);
        execucaoService.concluir(usuarioId, inicio2.execucao().getId());

        assertThat(tarefaRepository.findById(tarefaId).orElseThrow().getStatus())
                .isEqualTo(StatusTarefa.FINALIZADA);

        assertThatThrownBy(() -> execucaoService.concluir(usuarioId, inicio1.execucao().getId()))
                .isInstanceOf(ExecucaoNaoEmAndamentoException.class);
    }

    @Test
    void t4_abortarSoDeEmAndamentoENaoAlteraTarefa() {
        ResultadoInicio inicio = execucaoService.iniciar(usuarioId, blocoId);
        assertThat(tarefaRepository.findById(tarefaId).orElseThrow().getStatus())
                .isEqualTo(StatusTarefa.EM_PROGRESSO);

        Execucao abortada = execucaoService.abortar(usuarioId, inicio.execucao().getId());

        assertThat(abortada.getStatus()).isEqualTo(StatusExecucao.ABORTADO);
        assertThat(abortada.getAbortadoEm()).isNotNull();
        assertThat(tarefaRepository.findById(tarefaId).orElseThrow().getStatus())
                .isEqualTo(StatusTarefa.EM_PROGRESSO);

        assertThatThrownBy(() -> execucaoService.abortar(usuarioId, abortada.getId()))
                .isInstanceOf(ExecucaoNaoEmAndamentoException.class);
    }

    @Test
    void t5_rejeitaBlocoDePlanoNaoAprovadoOuNaoCorrente() {
        Plano rascunho = planoRepository.save(new Plano(
                UUID.randomUUID(),
                usuarioId,
                DIA,
                2,
                StatusPlano.RASCUNHO,
                false,
                null,
                AGORA,
                AGORA,
                AGORA
        ));
        UUID blocoRascunho = UUID.randomUUID();
        UUID tarefaRascunho = salvarTarefa("Rascunho", StatusTarefa.PENDENTE, 1);
        blocoPlanoRepository.save(new BlocoPlano(
                blocoRascunho, rascunho.getId(), tarefaRascunho, 30, 1, AGORA, AGORA
        ));

        assertThatThrownBy(() -> execucaoService.iniciar(usuarioId, blocoRascunho))
                .isInstanceOf(PlanoNaoExecutavelException.class);

        assertThatThrownBy(() -> execucaoService.iniciar(usuarioId, blocoId))
                .isInstanceOf(PlanoNaoExecutavelException.class);
    }

    private UUID salvarTarefa(String titulo, StatusTarefa status, int duracaoHoras) {
        UUID id = UUID.randomUUID();
        tarefaRepository.save(new Tarefa(
                id,
                usuarioId,
                titulo,
                "descricao",
                Prioridade.MEDIA,
                AGORA,
                status,
                duracaoHoras,
                AGORA,
                AGORA
        ));
        return id;
    }
}
