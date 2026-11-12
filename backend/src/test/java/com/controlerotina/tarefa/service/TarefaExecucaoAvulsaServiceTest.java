package com.controlerotina.tarefa.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.controlerotina.execucao.dto.ResultadoConclusao;
import com.controlerotina.execucao.dto.ResultadoInicio;
import com.controlerotina.execucao.entity.Execucao;
import com.controlerotina.execucao.entity.StatusExecucao;
import com.controlerotina.execucao.repository.ExecucaoRepository;
import com.controlerotina.pendencia.service.EsperaService;
import com.controlerotina.execucao.service.ExecucaoService;
import com.controlerotina.planner.entity.BlocoPlano;
import com.controlerotina.planner.entity.Plano;
import com.controlerotina.planner.entity.StatusPlano;
import com.controlerotina.planner.repository.BlocoPlanoRepository;
import com.controlerotina.planner.repository.PlanoRepository;
import com.controlerotina.shared.exception.DuracaoSessaoInvalidaException;
import com.controlerotina.shared.exception.TarefaNaoElegivelException;
import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;
import com.controlerotina.tarefa.repository.TarefaRepository;
import com.controlerotina.xp.service.XpConcessaoService;

@ExtendWith(MockitoExtension.class)
class TarefaExecucaoAvulsaServiceTest {

    private static final Instant AGORA = Instant.parse("2026-10-20T15:00:00Z");
    private static final LocalDate TERCA = LocalDate.of(2026, 10, 20);
    private static final UUID USUARIO = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID TAREFA_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Mock
    TarefaSlotAvulsoService slotAvulsoService;
    @Mock
    ExecucaoService execucaoService;
    @Mock
    PlanoRepository planoRepository;
    @Mock
    BlocoPlanoRepository blocoPlanoRepository;
    @Mock
    TarefaRepository tarefaRepository;
    @Mock
    ExecucaoRepository execucaoRepository;

    @Mock
    EsperaService esperaService;

    TarefaExecucaoAvulsaService service;

    @BeforeEach
    void setUp() {
        service = new TarefaExecucaoAvulsaService(
                slotAvulsoService,
                execucaoService,
                planoRepository,
                blocoPlanoRepository,
                tarefaRepository,
                execucaoRepository,
                esperaService,
                Clock.fixed(AGORA, ZoneOffset.UTC)
        );
    }

    @Test
    void iniciarPrefereBlocoAbertoDoAprovadoSemDuracao() {
        Plano aprovado = plano(StatusPlano.APROVADO);
        BlocoPlano bloco = new BlocoPlano(
                UUID.randomUUID(), aprovado.getId(), TAREFA_ID, 40, 1, AGORA, AGORA);
        ResultadoInicio esperado = new ResultadoInicio(
                Execucao.iniciarNova(UUID.randomUUID(), USUARIO, bloco.getId(), AGORA), true);

        when(tarefaRepository.findByIdAndUsuarioId(TAREFA_ID, USUARIO))
                .thenReturn(Optional.of(tarefa(120)));
        when(planoRepository.findFirstByUsuarioIdAndDataAndStatusOrderByVersaoDesc(
                USUARIO, TERCA, StatusPlano.APROVADO)).thenReturn(Optional.of(aprovado));
        when(blocoPlanoRepository.findByPlanoIdAndTarefaIdOrderByOrdemAsc(aprovado.getId(), TAREFA_ID))
                .thenReturn(List.of(bloco));
        when(execucaoRepository.findByBlocoPlanoId(bloco.getId())).thenReturn(Optional.empty());
        when(execucaoService.iniciar(USUARIO, bloco.getId())).thenReturn(esperado);

        ResultadoInicio resultado = service.iniciar(USUARIO, TAREFA_ID, null, TERCA, null);

        assertThat(resultado).isSameAs(esperado);
        verify(slotAvulsoService, never()).materializar(any(), any(), any(), anyInt());
    }

    @Test
    void iniciarAvulsoValidaDuracaoEMaterializa() {
        Tarefa tarefa = tarefa(120);
        BlocoPlano bloco = new BlocoPlano(
                UUID.randomUUID(), UUID.randomUUID(), TAREFA_ID, 30, 1, AGORA, AGORA);
        Plano avulso = plano(StatusPlano.TAREFA_AVULSA);
        ResultadoInicio esperado = new ResultadoInicio(
                Execucao.iniciarNova(UUID.randomUUID(), USUARIO, bloco.getId(), AGORA), true);

        when(planoRepository.findFirstByUsuarioIdAndDataAndStatusOrderByVersaoDesc(
                USUARIO, TERCA, StatusPlano.APROVADO)).thenReturn(Optional.empty());
        when(tarefaRepository.findByIdAndUsuarioId(TAREFA_ID, USUARIO)).thenReturn(Optional.of(tarefa));
        when(slotAvulsoService.restanteMinutos(USUARIO, tarefa)).thenReturn(90);
        when(slotAvulsoService.materializar(USUARIO, TAREFA_ID, TERCA, 30))
                .thenReturn(new TarefaSlotAvulsoService.SlotAvulso(tarefa, avulso, bloco, null));
        when(execucaoService.iniciar(USUARIO, bloco.getId())).thenReturn(esperado);

        ResultadoInicio resultado = service.iniciar(USUARIO, TAREFA_ID, 30, TERCA, null);

        assertThat(resultado).isSameAs(esperado);
        verify(slotAvulsoService).materializar(USUARIO, TAREFA_ID, TERCA, 30);
    }

    @Test
    void iniciarAvulsoRejeitaDuracaoAcimaDoRestante() {
        Tarefa tarefa = tarefa(60);
        when(planoRepository.findFirstByUsuarioIdAndDataAndStatusOrderByVersaoDesc(
                USUARIO, TERCA, StatusPlano.APROVADO)).thenReturn(Optional.empty());
        when(tarefaRepository.findByIdAndUsuarioId(TAREFA_ID, USUARIO)).thenReturn(Optional.of(tarefa));
        when(slotAvulsoService.restanteMinutos(USUARIO, tarefa)).thenReturn(20);

        assertThatThrownBy(() -> service.iniciar(USUARIO, TAREFA_ID, 60, TERCA, null))
                .isInstanceOf(DuracaoSessaoInvalidaException.class);
        verify(slotAvulsoService, never()).materializar(any(), any(), any(), anyInt());
    }

    @Test
    void iniciarRejeitaPaiComFilhos() {
        Tarefa tarefa = tarefa(120);
        BlocoPlano bloco = new BlocoPlano(
                UUID.randomUUID(), UUID.randomUUID(), TAREFA_ID, 30, 1, AGORA, AGORA);
        Plano avulso = plano(StatusPlano.TAREFA_AVULSA);
        ResultadoInicio esperado = new ResultadoInicio(
                Execucao.iniciarNova(UUID.randomUUID(), USUARIO, bloco.getId(), AGORA), true);

        lenient().when(planoRepository.findFirstByUsuarioIdAndDataAndStatusOrderByVersaoDesc(
                USUARIO, TERCA, StatusPlano.APROVADO)).thenReturn(Optional.empty());
        lenient().when(tarefaRepository.findByIdAndUsuarioId(TAREFA_ID, USUARIO)).thenReturn(Optional.of(tarefa));
        when(tarefaRepository.existsByParentIdAndDeletedAtIsNull(TAREFA_ID)).thenReturn(true);
        lenient().when(slotAvulsoService.restanteMinutos(USUARIO, tarefa)).thenReturn(90);
        lenient().when(slotAvulsoService.materializar(USUARIO, TAREFA_ID, TERCA, 30))
                .thenReturn(new TarefaSlotAvulsoService.SlotAvulso(tarefa, avulso, bloco, null));
        lenient().when(execucaoService.iniciar(USUARIO, bloco.getId())).thenReturn(esperado);

        assertThatThrownBy(() -> service.iniciar(USUARIO, TAREFA_ID, 30, TERCA, null))
                .isInstanceOf(TarefaNaoElegivelException.class);
        verify(slotAvulsoService, never()).materializar(any(), any(), any(), anyInt());
        verify(execucaoService, never()).iniciar(any(), any());
    }

    @Test
    void iniciarAvulsoRejeitaSemRestante() {
        Tarefa tarefa = tarefa(60);
        when(planoRepository.findFirstByUsuarioIdAndDataAndStatusOrderByVersaoDesc(
                USUARIO, TERCA, StatusPlano.APROVADO)).thenReturn(Optional.empty());
        when(tarefaRepository.findByIdAndUsuarioId(TAREFA_ID, USUARIO)).thenReturn(Optional.of(tarefa));
        when(slotAvulsoService.restanteMinutos(USUARIO, tarefa)).thenReturn(0);

        assertThatThrownBy(() -> service.iniciar(USUARIO, TAREFA_ID, 15, TERCA, null))
                .isInstanceOf(TarefaNaoElegivelException.class);
    }

    @Test
    void concluirDiretoNoAprovadoUsaBlocoAberto() {
        Plano aprovado = plano(StatusPlano.APROVADO);
        BlocoPlano bloco = new BlocoPlano(
                UUID.randomUUID(), aprovado.getId(), TAREFA_ID, 40, 1, AGORA, AGORA);
        Tarefa tarefa = tarefa(120);
        ResultadoConclusao esperado = new ResultadoConclusao(
                Execucao.concluirDiretoNova(UUID.randomUUID(), USUARIO, bloco.getId(), AGORA),
                new XpConcessaoService.ResultadoConcessao(10, List.of(), 10, null)
        );

        when(planoRepository.findFirstByUsuarioIdAndDataAndStatusOrderByVersaoDesc(
                USUARIO, TERCA, StatusPlano.APROVADO)).thenReturn(Optional.of(aprovado));
        when(blocoPlanoRepository.findByPlanoIdAndTarefaIdOrderByOrdemAsc(aprovado.getId(), TAREFA_ID))
                .thenReturn(List.of(bloco));
        when(execucaoRepository.findByBlocoPlanoId(bloco.getId())).thenReturn(Optional.empty());
        when(tarefaRepository.findByIdAndUsuarioId(TAREFA_ID, USUARIO)).thenReturn(Optional.of(tarefa));
        when(execucaoService.concluirTarefaDiretoNoBloco(USUARIO, bloco, tarefa)).thenReturn(esperado);

        ResultadoConclusao resultado = service.concluirDireto(USUARIO, TAREFA_ID, null, TERCA, null);

        assertThat(resultado).isSameAs(esperado);
        verify(slotAvulsoService, never()).materializar(any(), any(), any(), anyInt());
    }

    @Test
    void concluirDiretoRejeitaPaiComFilhos() {
        Tarefa tarefa = tarefa(120);
        Plano avulso = plano(StatusPlano.TAREFA_AVULSA);
        BlocoPlano bloco = new BlocoPlano(
                UUID.randomUUID(), avulso.getId(), TAREFA_ID, 30, 1, AGORA, AGORA);
        ResultadoConclusao esperado = new ResultadoConclusao(
                Execucao.concluirDiretoNova(UUID.randomUUID(), USUARIO, bloco.getId(), AGORA),
                new XpConcessaoService.ResultadoConcessao(5, List.of(), 5, null)
        );

        lenient().when(planoRepository.findFirstByUsuarioIdAndDataAndStatusOrderByVersaoDesc(
                USUARIO, TERCA, StatusPlano.APROVADO)).thenReturn(Optional.empty());
        lenient().when(tarefaRepository.findByIdAndUsuarioId(TAREFA_ID, USUARIO)).thenReturn(Optional.of(tarefa));
        when(tarefaRepository.existsByParentIdAndDeletedAtIsNull(TAREFA_ID)).thenReturn(true);
        lenient().when(slotAvulsoService.restanteMinutos(USUARIO, tarefa)).thenReturn(90);
        lenient().when(slotAvulsoService.materializar(USUARIO, TAREFA_ID, TERCA, 30))
                .thenReturn(new TarefaSlotAvulsoService.SlotAvulso(tarefa, avulso, bloco, null));
        lenient().when(execucaoService.concluirTarefaDiretoNoBloco(eq(USUARIO), eq(bloco), eq(tarefa)))
                .thenReturn(esperado);

        assertThatThrownBy(() -> service.concluirDireto(USUARIO, TAREFA_ID, 30, TERCA, null))
                .isInstanceOf(TarefaNaoElegivelException.class);
        verify(slotAvulsoService, never()).materializar(any(), any(), any(), anyInt());
        verify(execucaoService, never()).concluirTarefaDiretoNoBloco(any(), any(), any());
    }

    @Test
    void concluirDiretoAvulsoMaterializa() {
        Tarefa tarefa = tarefa(90);
        Plano avulso = plano(StatusPlano.TAREFA_AVULSA);
        BlocoPlano bloco = new BlocoPlano(
                UUID.randomUUID(), avulso.getId(), TAREFA_ID, 25, 1, AGORA, AGORA);
        ResultadoConclusao esperado = new ResultadoConclusao(
                Execucao.concluirDiretoNova(UUID.randomUUID(), USUARIO, bloco.getId(), AGORA),
                new XpConcessaoService.ResultadoConcessao(5, List.of(), 5, null)
        );

        when(planoRepository.findFirstByUsuarioIdAndDataAndStatusOrderByVersaoDesc(
                USUARIO, TERCA, StatusPlano.APROVADO)).thenReturn(Optional.empty());
        when(tarefaRepository.findByIdAndUsuarioId(TAREFA_ID, USUARIO)).thenReturn(Optional.of(tarefa));
        when(slotAvulsoService.restanteMinutos(USUARIO, tarefa)).thenReturn(90);
        when(slotAvulsoService.materializar(USUARIO, TAREFA_ID, TERCA, 25))
                .thenReturn(new TarefaSlotAvulsoService.SlotAvulso(tarefa, avulso, bloco, null));
        when(execucaoService.concluirTarefaDiretoNoBloco(eq(USUARIO), eq(bloco), eq(tarefa)))
                .thenReturn(esperado);

        ResultadoConclusao resultado = service.concluirDireto(USUARIO, TAREFA_ID, 25, TERCA, null);

        assertThat(resultado).isSameAs(esperado);
    }

    private Tarefa tarefa(int duracao) {
        return new Tarefa(
                TAREFA_ID, USUARIO, "Estudar", "desc", Prioridade.MEDIA,
                AGORA, StatusTarefa.PENDENTE, duracao, AGORA, AGORA);
    }

    private Plano plano(StatusPlano status) {
        return new Plano(
                UUID.randomUUID(), USUARIO, TERCA, 1, status,
                false, null, null, AGORA, AGORA, AGORA);
    }
}
