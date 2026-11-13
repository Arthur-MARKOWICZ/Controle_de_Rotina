package com.controlerotina.habito.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.controlerotina.capacidade.entity.DiaSemana;
import com.controlerotina.execucao.dto.ResultadoConclusao;
import com.controlerotina.execucao.dto.ResultadoInicio;
import com.controlerotina.execucao.entity.Execucao;
import com.controlerotina.execucao.entity.StatusExecucao;
import com.controlerotina.execucao.repository.ExecucaoRepository;
import com.controlerotina.execucao.service.ExecucaoService;
import com.controlerotina.habito.entity.Habito;
import com.controlerotina.habito.entity.StatusHabito;
import com.controlerotina.habito.repository.HabitoRepository;
import com.controlerotina.historico.PeriodoBRT;
import com.controlerotina.planner.entity.BlocoPlano;
import com.controlerotina.planner.entity.Plano;
import com.controlerotina.planner.entity.StatusPlano;
import com.controlerotina.planner.repository.BlocoPlanoRepository;
import com.controlerotina.planner.repository.PlanoRepository;
import com.controlerotina.shared.exception.HabitoNaoEncontradoException;
import com.controlerotina.xp.service.XpConcessaoService;

@ExtendWith(MockitoExtension.class)
class HabitoExecucaoAvulsaServiceTest {

    private static final Instant AGORA = Instant.parse("2026-10-20T15:00:00Z");
    private static final LocalDate TERCA = LocalDate.of(2026, 10, 20);
    private static final UUID USUARIO = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID HABITO_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Mock
    HabitoSlotAvulsoService slotAvulsoService;
    @Mock
    ExecucaoService execucaoService;
    @Mock
    PlanoRepository planoRepository;
    @Mock
    BlocoPlanoRepository blocoPlanoRepository;
    @Mock
    HabitoRepository habitoRepository;
    @Mock
    ExecucaoRepository execucaoRepository;

    private HabitoExecucaoAvulsaService service;

    @BeforeEach
    void setUp() {
        service = new HabitoExecucaoAvulsaService(
                slotAvulsoService,
                execucaoService,
                planoRepository,
                blocoPlanoRepository,
                habitoRepository,
                execucaoRepository,
                Clock.fixed(AGORA, PeriodoBRT.ZONE)
        );
    }

    @Test
    void deveIniciarNoBlocoDoPlanoAprovadoAberto() {
        Plano plano = planoAprovado();
        BlocoPlano bloco = bloco(plano.getId());
        when(planoRepository.findFirstByUsuarioIdAndDataAndStatusOrderByVersaoDesc(
                USUARIO, TERCA, StatusPlano.APROVADO)).thenReturn(Optional.of(plano));
        when(blocoPlanoRepository.findByPlanoIdAndHabitoId(plano.getId(), HABITO_ID)).thenReturn(Optional.of(bloco));
        when(execucaoRepository.findByBlocoPlanoId(bloco.getId())).thenReturn(Optional.empty());
        ResultadoInicio inicio = new ResultadoInicio(execucao(bloco.getId(), StatusExecucao.EM_ANDAMENTO), true);
        when(execucaoService.iniciar(USUARIO, bloco.getId())).thenReturn(inicio);

        ResultadoInicio resultado = service.iniciar(USUARIO, HABITO_ID, TERCA);

        assertThat(resultado).isSameAs(inicio);
        verify(slotAvulsoService, never()).materializar(any(), any(), any());
    }

    @Test
    void deveMaterializarSlotQuandoNaoHaPlanoAprovado() {
        when(planoRepository.findFirstByUsuarioIdAndDataAndStatusOrderByVersaoDesc(
                USUARIO, TERCA, StatusPlano.APROVADO)).thenReturn(Optional.empty());
        Habito habito = habito();
        Plano avulso = planoAvulso();
        BlocoPlano bloco = bloco(avulso.getId());
        when(slotAvulsoService.materializar(USUARIO, HABITO_ID, TERCA))
                .thenReturn(new HabitoSlotAvulsoService.SlotAvulso(habito, avulso, bloco, null));
        ResultadoInicio inicio = new ResultadoInicio(execucao(bloco.getId(), StatusExecucao.EM_ANDAMENTO), true);
        when(execucaoService.iniciar(USUARIO, bloco.getId())).thenReturn(inicio);

        assertThat(service.iniciar(USUARIO, HABITO_ID, TERCA)).isSameAs(inicio);
        verify(slotAvulsoService).materializar(USUARIO, HABITO_ID, TERCA);
    }

    @Test
    void deveMaterializarSlotQuandoBlocoDoPlanoJaConcluido() {
        Plano plano = planoAprovado();
        BlocoPlano bloco = bloco(plano.getId());
        when(planoRepository.findFirstByUsuarioIdAndDataAndStatusOrderByVersaoDesc(
                USUARIO, TERCA, StatusPlano.APROVADO)).thenReturn(Optional.of(plano));
        when(blocoPlanoRepository.findByPlanoIdAndHabitoId(plano.getId(), HABITO_ID)).thenReturn(Optional.of(bloco));
        when(execucaoRepository.findByBlocoPlanoId(bloco.getId()))
                .thenReturn(Optional.of(execucao(bloco.getId(), StatusExecucao.CONCLUIDO)));
        Habito habito = habito();
        Plano avulso = planoAvulso();
        BlocoPlano blocoAvulso = bloco(avulso.getId());
        when(slotAvulsoService.materializar(USUARIO, HABITO_ID, TERCA))
                .thenReturn(new HabitoSlotAvulsoService.SlotAvulso(habito, avulso, blocoAvulso, null));
        ResultadoInicio inicio = new ResultadoInicio(execucao(blocoAvulso.getId(), StatusExecucao.EM_ANDAMENTO), true);
        when(execucaoService.iniciar(USUARIO, blocoAvulso.getId())).thenReturn(inicio);

        service.iniciar(USUARIO, HABITO_ID, TERCA);

        verify(slotAvulsoService).materializar(USUARIO, HABITO_ID, TERCA);
        verify(execucaoService, never()).iniciar(USUARIO, bloco.getId());
    }

    @Test
    void deveUsarClockQuandoDataNula() {
        when(planoRepository.findFirstByUsuarioIdAndDataAndStatusOrderByVersaoDesc(
                USUARIO, TERCA, StatusPlano.APROVADO)).thenReturn(Optional.empty());
        Habito habito = habito();
        Plano avulso = planoAvulso();
        BlocoPlano bloco = bloco(avulso.getId());
        when(slotAvulsoService.materializar(eq(USUARIO), eq(HABITO_ID), eq(TERCA)))
                .thenReturn(new HabitoSlotAvulsoService.SlotAvulso(habito, avulso, bloco, null));
        when(execucaoService.iniciar(USUARIO, bloco.getId()))
                .thenReturn(new ResultadoInicio(execucao(bloco.getId(), StatusExecucao.EM_ANDAMENTO), true));

        service.iniciar(USUARIO, HABITO_ID, null);

        verify(slotAvulsoService).materializar(USUARIO, HABITO_ID, TERCA);
    }

    @Test
    void deveConcluirDiretoNoBlocoDoPlano() {
        Plano plano = planoAprovado();
        BlocoPlano bloco = bloco(plano.getId());
        Habito habito = habito();
        when(planoRepository.findFirstByUsuarioIdAndDataAndStatusOrderByVersaoDesc(
                USUARIO, TERCA, StatusPlano.APROVADO)).thenReturn(Optional.of(plano));
        when(blocoPlanoRepository.findByPlanoIdAndHabitoId(plano.getId(), HABITO_ID)).thenReturn(Optional.of(bloco));
        when(execucaoRepository.findByBlocoPlanoId(bloco.getId())).thenReturn(Optional.empty());
        when(habitoRepository.findByIdAndUsuarioId(HABITO_ID, USUARIO)).thenReturn(Optional.of(habito));
        ResultadoConclusao conclusao = new ResultadoConclusao(
                execucao(bloco.getId(), StatusExecucao.CONCLUIDO),
                new XpConcessaoService.ResultadoConcessao(0, List.of(), 0, null));
        when(execucaoService.concluirHabitoDiretoNoBloco(USUARIO, bloco, plano, habito)).thenReturn(conclusao);

        assertThat(service.concluirDireto(USUARIO, HABITO_ID, TERCA)).isSameAs(conclusao);
        verify(slotAvulsoService, never()).materializar(any(), any(), any());
    }

    @Test
    void deveFalharAoConcluirDiretoQuandoHabitoDoPlanoNaoExiste() {
        Plano plano = planoAprovado();
        BlocoPlano bloco = bloco(plano.getId());
        when(planoRepository.findFirstByUsuarioIdAndDataAndStatusOrderByVersaoDesc(
                USUARIO, TERCA, StatusPlano.APROVADO)).thenReturn(Optional.of(plano));
        when(blocoPlanoRepository.findByPlanoIdAndHabitoId(plano.getId(), HABITO_ID)).thenReturn(Optional.of(bloco));
        when(execucaoRepository.findByBlocoPlanoId(bloco.getId())).thenReturn(Optional.empty());
        when(habitoRepository.findByIdAndUsuarioId(HABITO_ID, USUARIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.concluirDireto(USUARIO, HABITO_ID, TERCA))
                .isInstanceOf(HabitoNaoEncontradoException.class);
    }

    @Test
    void deveConcluirDiretoNoSlotAvulsoQuandoNaoHaPlano() {
        when(planoRepository.findFirstByUsuarioIdAndDataAndStatusOrderByVersaoDesc(
                USUARIO, TERCA, StatusPlano.APROVADO)).thenReturn(Optional.empty());
        Habito habito = habito();
        Plano avulso = planoAvulso();
        BlocoPlano bloco = bloco(avulso.getId());
        when(slotAvulsoService.materializar(USUARIO, HABITO_ID, TERCA))
                .thenReturn(new HabitoSlotAvulsoService.SlotAvulso(habito, avulso, bloco, null));
        ResultadoConclusao conclusao = new ResultadoConclusao(
                execucao(bloco.getId(), StatusExecucao.CONCLUIDO),
                new XpConcessaoService.ResultadoConcessao(0, List.of(), 0, null));
        when(execucaoService.concluirHabitoDiretoNoBloco(USUARIO, bloco, avulso, habito)).thenReturn(conclusao);

        assertThat(service.concluirDireto(USUARIO, HABITO_ID, TERCA)).isSameAs(conclusao);
    }

    private static Habito habito() {
        return new Habito(
                HABITO_ID, USUARIO, "Correr", null, 30,
                Set.of(DiaSemana.TERCA), null, StatusHabito.ATIVO, 0, 0, AGORA, AGORA);
    }

    private static Plano planoAprovado() {
        return new Plano(
                UUID.randomUUID(), USUARIO, TERCA, 1, StatusPlano.APROVADO,
                false, null, null, AGORA, AGORA, AGORA);
    }

    private static Plano planoAvulso() {
        return new Plano(
                UUID.randomUUID(), USUARIO, TERCA, 1, StatusPlano.HABITO_AVULSO,
                false, null, null, AGORA, AGORA, AGORA);
    }

    private static BlocoPlano bloco(UUID planoId) {
        return new BlocoPlano(UUID.randomUUID(), planoId, null, HABITO_ID, 30, 1, AGORA, AGORA);
    }

    private static Execucao execucao(UUID blocoId, StatusExecucao status) {
        return new Execucao(
                UUID.randomUUID(), USUARIO, blocoId, status, AGORA, null, null, AGORA, AGORA);
    }
}
