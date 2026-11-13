package com.controlerotina.tarefa.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.controlerotina.decidir.repository.ItemDecidirRepository;
import com.controlerotina.execucao.repository.ExecucaoRepository;
import com.controlerotina.pendencia.service.EsperaService;
import com.controlerotina.historico.PeriodoBRT;
import com.controlerotina.planner.service.PropostaGeracaoService;
import com.controlerotina.projeto.repository.ProjetoRepository;
import com.controlerotina.tag.service.TagVinculoService;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.repository.TarefaRepository;

@ExtendWith(MockitoExtension.class)
class TarefaServiceProcessarAtrasosTest {

    /** 2026-10-29 15:00 UTC = 12:00 America/Sao_Paulo */
    private static final Instant AGORA = Instant.parse("2026-10-29T15:00:00Z");

    @Mock
    TarefaRepository tarefaRepository;

    @Mock
    ProjetoRepository projetoRepository;

    @Mock
    PropostaGeracaoService propostaGeracaoService;

    @Mock
    ExecucaoRepository execucaoRepository;

    @Mock
    TagVinculoService tagVinculoService;

    @Mock
    ItemDecidirRepository itemDecidirRepository;

    @Mock
    EsperaService esperaService;

    private TarefaService tarefaService;

    @BeforeEach
    void setUp() {
        tarefaService = new TarefaService(
                tarefaRepository,
                projetoRepository,
                propostaGeracaoService,
                execucaoRepository,
                tagVinculoService,
                itemDecidirRepository,
                esperaService,
                Clock.fixed(AGORA, ZoneOffset.UTC)
        );
    }

    @Test
    void deveMarcarPendenteEEmProgressoComPrazoAntesDoInicioDoDia() {
        Instant inicioDoDia = PeriodoBRT.intervaloDia(LocalDate.of(2026, 10, 29)).inicioInclusivo();

        tarefaService.processarAtrasos();

        verify(tarefaRepository).atualizarStatusQuandoPrazoPassou(
                StatusTarefa.PENDENTE,
                StatusTarefa.ATRASADA,
                inicioDoDia,
                AGORA
        );
        verify(tarefaRepository).atualizarStatusQuandoPrazoPassou(
                StatusTarefa.EM_PROGRESSO,
                StatusTarefa.ATRASADA,
                inicioDoDia,
                AGORA
        );
        verifyNoMoreInteractions(tarefaRepository);
    }

    @Test
    void naoDeveReverterAtrasadaParaPendente() {
        tarefaService.processarAtrasos();

        verify(tarefaRepository, never()).atualizarStatusQuandoPrazoPassou(
                eq(StatusTarefa.ATRASADA),
                any(),
                any(),
                any()
        );
        verify(tarefaRepository, never()).atualizarStatusQuandoPrazoPassou(
                any(),
                eq(StatusTarefa.PENDENTE),
                any(),
                any()
        );
    }

    @Test
    void naoDeveAtualizarFinalizadaNemCancelada() {
        tarefaService.processarAtrasos();

        verify(tarefaRepository, never()).atualizarStatusQuandoPrazoPassou(
                eq(StatusTarefa.FINALIZADA),
                any(),
                any(),
                any()
        );
        verify(tarefaRepository, never()).atualizarStatusQuandoPrazoPassou(
                eq(StatusTarefa.CANCELADA),
                any(),
                any(),
                any()
        );
    }

    @Test
    void meiaNoiteBrtUsaInicioDoMesmoDia() {
        Instant meiaNoite = Instant.parse("2026-10-29T03:00:00Z");
        TarefaService noLimite = new TarefaService(
                tarefaRepository,
                projetoRepository,
                propostaGeracaoService,
                execucaoRepository,
                tagVinculoService,
                itemDecidirRepository,
                esperaService,
                Clock.fixed(meiaNoite, ZoneOffset.UTC)
        );

        noLimite.processarAtrasos();

        Instant inicioDoDia = PeriodoBRT.intervaloDia(LocalDate.of(2026, 10, 29)).inicioInclusivo();
        verify(tarefaRepository).atualizarStatusQuandoPrazoPassou(
                StatusTarefa.PENDENTE, StatusTarefa.ATRASADA, inicioDoDia, meiaNoite);
        verify(tarefaRepository).atualizarStatusQuandoPrazoPassou(
                StatusTarefa.EM_PROGRESSO, StatusTarefa.ATRASADA, inicioDoDia, meiaNoite);
    }

    @Test
    void umSegundoAntesDaMeiaNoiteBrtAindaEOdiaAnterior() {
        Instant quaseMeiaNoite = Instant.parse("2026-10-29T02:59:59Z");
        TarefaService antes = new TarefaService(
                tarefaRepository,
                projetoRepository,
                propostaGeracaoService,
                execucaoRepository,
                tagVinculoService,
                itemDecidirRepository,
                esperaService,
                Clock.fixed(quaseMeiaNoite, ZoneOffset.UTC)
        );

        antes.processarAtrasos();

        Instant inicioDia28 = PeriodoBRT.intervaloDia(LocalDate.of(2026, 10, 28)).inicioInclusivo();
        verify(tarefaRepository).atualizarStatusQuandoPrazoPassou(
                StatusTarefa.PENDENTE, StatusTarefa.ATRASADA, inicioDia28, quaseMeiaNoite);
        verify(tarefaRepository, never()).atualizarStatusQuandoPrazoPassou(
                eq(StatusTarefa.PENDENTE),
                eq(StatusTarefa.ATRASADA),
                eq(PeriodoBRT.intervaloDia(LocalDate.of(2026, 10, 29)).inicioInclusivo()),
                any()
        );
    }

    @Test
    void naoDeveTocarOutrosRepositorios() {
        tarefaService.processarAtrasos();

        verifyNoMoreInteractions(projetoRepository, propostaGeracaoService, execucaoRepository);
    }

    @Test
    void devePropagarFalhaDoRepositorio() {
        org.mockito.Mockito.doThrow(new IllegalStateException("falha sql"))
                .when(tarefaRepository)
                .atualizarStatusQuandoPrazoPassou(any(), any(), any(), any());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> tarefaService.processarAtrasos())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("falha sql");
    }
}
