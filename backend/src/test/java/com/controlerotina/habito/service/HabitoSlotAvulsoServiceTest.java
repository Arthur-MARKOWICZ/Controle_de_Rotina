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
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.controlerotina.capacidade.entity.DiaSemana;
import com.controlerotina.execucao.repository.ExecucaoRepository;
import com.controlerotina.habito.entity.Habito;
import com.controlerotina.habito.entity.StatusHabito;
import com.controlerotina.habito.repository.HabitoRepository;
import com.controlerotina.planner.entity.BlocoPlano;
import com.controlerotina.planner.entity.Plano;
import com.controlerotina.planner.entity.StatusPlano;
import com.controlerotina.planner.repository.BlocoPlanoRepository;
import com.controlerotina.planner.repository.PlanoRepository;
import com.controlerotina.shared.exception.HabitoNaoElegivelException;
import com.controlerotina.shared.exception.HabitoNaoEncontradoException;

@ExtendWith(MockitoExtension.class)
class HabitoSlotAvulsoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-10-20T15:00:00Z");
    private static final LocalDate TERCA = LocalDate.of(2026, 10, 20);
    private static final UUID USUARIO = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID HABITO_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Mock
    HabitoRepository habitoRepository;
    @Mock
    PlanoRepository planoRepository;
    @Mock
    BlocoPlanoRepository blocoPlanoRepository;
    @Mock
    ExecucaoRepository execucaoRepository;

    HabitoSlotAvulsoService service;

    @BeforeEach
    void setUp() {
        service = new HabitoSlotAvulsoService(
                habitoRepository,
                planoRepository,
                blocoPlanoRepository,
                execucaoRepository,
                Clock.fixed(AGORA, ZoneOffset.UTC)
        );
    }

    @Test
    void t1_deveCriarPlanoHabitoAvulsoEBlocoNaPrimeiraMaterializacao() {
        Habito habito = habitoAtivo(Set.of(DiaSemana.TERCA));
        when(habitoRepository.findByIdAndUsuarioId(HABITO_ID, USUARIO)).thenReturn(Optional.of(habito));
        when(planoRepository.findByUsuarioIdAndDataAndStatus(USUARIO, TERCA, StatusPlano.HABITO_AVULSO))
                .thenReturn(Optional.empty());
        when(planoRepository.findMaxVersaoByUsuarioIdAndData(USUARIO, TERCA)).thenReturn(0);
        when(planoRepository.save(any(Plano.class))).thenAnswer(inv -> inv.getArgument(0));
        when(blocoPlanoRepository.findMaxOrdemByPlanoId(any())).thenReturn(0);
        when(blocoPlanoRepository.findByPlanoIdAndHabitoId(any(), eq(HABITO_ID)))
                .thenReturn(Optional.empty());
        when(blocoPlanoRepository.save(any(BlocoPlano.class))).thenAnswer(inv -> inv.getArgument(0));
        when(execucaoRepository.findByBlocoPlanoId(any())).thenReturn(Optional.empty());

        HabitoSlotAvulsoService.SlotAvulso slot = service.materializar(USUARIO, HABITO_ID, TERCA);

        assertThat(slot.plano().getStatus()).isEqualTo(StatusPlano.HABITO_AVULSO);
        assertThat(slot.plano().getVersao()).isEqualTo(1);
        assertThat(slot.bloco().getHabitoId()).isEqualTo(HABITO_ID);
        assertThat(slot.bloco().getDuracaoMinutos()).isEqualTo(30);
        assertThat(slot.execucao()).isNull();
        verify(planoRepository).save(any(Plano.class));
        verify(blocoPlanoRepository).save(any(BlocoPlano.class));
    }

    @Test
    void t1_deveReusarPlanoEBlocoNaSegundaMaterializacao() {
        Habito habito = habitoAtivo(Set.of(DiaSemana.TERCA));
        Plano plano = planoAvulso(UUID.randomUUID());
        BlocoPlano bloco = new BlocoPlano(
                UUID.randomUUID(), plano.getId(), null, HABITO_ID, 30, 1, AGORA, AGORA);

        when(habitoRepository.findByIdAndUsuarioId(HABITO_ID, USUARIO)).thenReturn(Optional.of(habito));
        when(planoRepository.findByUsuarioIdAndDataAndStatus(USUARIO, TERCA, StatusPlano.HABITO_AVULSO))
                .thenReturn(Optional.of(plano));
        when(blocoPlanoRepository.findByPlanoIdAndHabitoId(plano.getId(), HABITO_ID))
                .thenReturn(Optional.of(bloco));
        when(execucaoRepository.findByBlocoPlanoId(bloco.getId())).thenReturn(Optional.empty());

        HabitoSlotAvulsoService.SlotAvulso slot = service.materializar(USUARIO, HABITO_ID, TERCA);

        assertThat(slot.plano().getId()).isEqualTo(plano.getId());
        assertThat(slot.bloco().getId()).isEqualTo(bloco.getId());
        verify(planoRepository, never()).save(any());
        verify(blocoPlanoRepository, never()).save(any());
    }

    @Test
    void t2_deveRejeitarWeekdayInvalido() {
        Habito habito = habitoAtivo(Set.of(DiaSemana.SEGUNDA));
        when(habitoRepository.findByIdAndUsuarioId(HABITO_ID, USUARIO)).thenReturn(Optional.of(habito));

        assertThatThrownBy(() -> service.materializar(USUARIO, HABITO_ID, TERCA))
                .isInstanceOf(HabitoNaoElegivelException.class);
        verify(planoRepository, never()).save(any());
    }

    @Test
    void t2_deveRejeitarHabitoPausado() {
        Habito habito = new Habito(
                HABITO_ID, USUARIO, "Correr", null, 30,
                Set.of(DiaSemana.TERCA), null, StatusHabito.PAUSADO, 0, 0, AGORA, AGORA);
        when(habitoRepository.findByIdAndUsuarioId(HABITO_ID, USUARIO)).thenReturn(Optional.of(habito));

        assertThatThrownBy(() -> service.materializar(USUARIO, HABITO_ID, TERCA))
                .isInstanceOf(HabitoNaoElegivelException.class);
    }

    @Test
    void t2_deveRejeitarHabitoDeletado() {
        Habito habito = habitoAtivo(Set.of(DiaSemana.TERCA));
        habito.softDelete(AGORA);
        when(habitoRepository.findByIdAndUsuarioId(HABITO_ID, USUARIO)).thenReturn(Optional.of(habito));

        assertThatThrownBy(() -> service.materializar(USUARIO, HABITO_ID, TERCA))
                .isInstanceOf(HabitoNaoEncontradoException.class)
                .satisfies(ex -> {
                    HabitoNaoEncontradoException erro = (HabitoNaoEncontradoException) ex;
                    assertThat(erro.getStatusCode().value()).isEqualTo(404);
                    assertThat(erro.getBody().getTitle()).isEqualTo("Hábito não encontrado");
                });
        verify(planoRepository, never()).save(any());
    }

    @Test
    void deveRejeitarHabitoDeOutroUsuarioSemCriarPlano() {
        when(habitoRepository.findByIdAndUsuarioId(HABITO_ID, USUARIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.materializar(USUARIO, HABITO_ID, TERCA))
                .isInstanceOf(HabitoNaoEncontradoException.class)
                .satisfies(ex -> {
                    HabitoNaoEncontradoException erro = (HabitoNaoEncontradoException) ex;
                    assertThat(erro.getStatusCode().value()).isEqualTo(404);
                    assertThat(erro.getBody().getTitle()).isEqualTo("Hábito não encontrado");
                });
        verify(planoRepository, never()).save(any());
        verify(blocoPlanoRepository, never()).save(any());
    }

    @Test
    void weekdayInvalidoDeveLancar409ComTitle() {
        Habito habito = habitoAtivo(Set.of(DiaSemana.SEGUNDA));
        when(habitoRepository.findByIdAndUsuarioId(HABITO_ID, USUARIO)).thenReturn(Optional.of(habito));

        assertThatThrownBy(() -> service.materializar(USUARIO, HABITO_ID, TERCA))
                .isInstanceOf(HabitoNaoElegivelException.class)
                .satisfies(ex -> {
                    HabitoNaoElegivelException erro = (HabitoNaoElegivelException) ex;
                    assertThat(erro.getStatusCode().value()).isEqualTo(409);
                    assertThat(erro.getBody().getTitle()).isEqualTo("Hábito não elegível");
                });
        verify(blocoPlanoRepository, never()).save(any());
    }

    @Test
    void deveRejeitarHabitoDeOutroUsuario() {
        when(habitoRepository.findByIdAndUsuarioId(HABITO_ID, USUARIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.materializar(USUARIO, HABITO_ID, TERCA))
                .isInstanceOf(HabitoNaoEncontradoException.class);
        verify(planoRepository, never()).save(any());
    }

    @Test
    void deveUsarClockQuandoDataNula() {
        Habito habito = habitoAtivo(Set.of(DiaSemana.TERCA));
        when(habitoRepository.findByIdAndUsuarioId(HABITO_ID, USUARIO)).thenReturn(Optional.of(habito));
        when(planoRepository.findByUsuarioIdAndDataAndStatus(USUARIO, TERCA, StatusPlano.HABITO_AVULSO))
                .thenReturn(Optional.empty());
        when(planoRepository.findMaxVersaoByUsuarioIdAndData(USUARIO, TERCA)).thenReturn(0);
        when(planoRepository.save(any(Plano.class))).thenAnswer(inv -> inv.getArgument(0));
        when(blocoPlanoRepository.findMaxOrdemByPlanoId(any())).thenReturn(0);
        when(blocoPlanoRepository.findByPlanoIdAndHabitoId(any(), eq(HABITO_ID)))
                .thenReturn(Optional.empty());
        when(blocoPlanoRepository.save(any(BlocoPlano.class))).thenAnswer(inv -> inv.getArgument(0));
        when(execucaoRepository.findByBlocoPlanoId(any())).thenReturn(Optional.empty());

        HabitoSlotAvulsoService.SlotAvulso slot = service.materializar(USUARIO, HABITO_ID, null);

        assertThat(slot.plano().getData()).isEqualTo(TERCA);
        assertThat(slot.habito().getId()).isEqualTo(HABITO_ID);
    }

    private Habito habitoAtivo(Set<DiaSemana> dias) {
        return new Habito(
                HABITO_ID, USUARIO, "Correr", null, 30,
                dias, null, StatusHabito.ATIVO, 0, 0, AGORA, AGORA);
    }

    private Plano planoAvulso(UUID id) {
        return new Plano(
                id, USUARIO, TERCA, 1, StatusPlano.HABITO_AVULSO,
                false, null, null, AGORA, AGORA, AGORA);
    }
}
