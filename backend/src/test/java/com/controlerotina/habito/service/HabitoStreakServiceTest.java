package com.controlerotina.habito.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.controlerotina.capacidade.entity.DiaSemana;
import com.controlerotina.execucao.entity.StatusExecucao;
import com.controlerotina.execucao.repository.ExecucaoRepository;
import com.controlerotina.habito.entity.Habito;
import com.controlerotina.habito.entity.StatusHabito;

@ExtendWith(MockitoExtension.class)
class HabitoStreakServiceTest {

    private static final Instant AGORA = Instant.parse("2026-10-20T15:00:00Z");
    private static final LocalDate TERCA = LocalDate.of(2026, 10, 20);
    private static final LocalDate SEGUNDA = LocalDate.of(2026, 10, 19);

    @Mock
    ExecucaoRepository execucaoRepository;

    HabitoStreakService service;

    @BeforeEach
    void setUp() {
        service = new HabitoStreakService(
                execucaoRepository,
                Clock.fixed(AGORA, ZoneOffset.UTC)
        );
    }

    @Test
    void t5_deveIncrementarStreakQuandoDiaAgendadoAnteriorCumprido() {
        Habito habito = habitoComStreak(3, 5);

        when(execucaoRepository.existsCumprimentoHabitoNaData(
                eq(habito.getId()), eq(SEGUNDA), eq(StatusExecucao.CONCLUIDO)))
                .thenReturn(true);

        service.registrarCumprimento(habito, TERCA, false);

        assertThat(habito.getStreakAtual()).isEqualTo(4);
        assertThat(habito.getMelhorStreak()).isEqualTo(5);
    }

    @Test
    void t5_deveReiniciarStreakQuandoDiaAgendadoAnteriorFaltoso() {
        Habito habito = habitoComStreak(3, 5);

        when(execucaoRepository.existsCumprimentoHabitoNaData(
                eq(habito.getId()), eq(SEGUNDA), eq(StatusExecucao.CONCLUIDO)))
                .thenReturn(false);

        service.registrarCumprimento(habito, TERCA, false);

        assertThat(habito.getStreakAtual()).isEqualTo(1);
        assertThat(habito.getMelhorStreak()).isEqualTo(5);
    }

    @Test
    void t5_deveAtualizarMelhorStreak() {
        Habito habito = habitoComStreak(2, 2);

        when(execucaoRepository.existsCumprimentoHabitoNaData(
                eq(habito.getId()), eq(SEGUNDA), eq(StatusExecucao.CONCLUIDO)))
                .thenReturn(true);

        service.registrarCumprimento(habito, TERCA, false);

        assertThat(habito.getStreakAtual()).isEqualTo(3);
        assertThat(habito.getMelhorStreak()).isEqualTo(3);
    }

    @Test
    void t5_naoAlteraQuandoJaCumpridoNoDia() {
        Habito habito = habitoComStreak(3, 5);

        service.registrarCumprimento(habito, TERCA, true);

        assertThat(habito.getStreakAtual()).isEqualTo(3);
        verify(execucaoRepository, never()).existsCumprimentoHabitoNaData(any(), any(), any());
    }

    @Test
    void t1_quebraPreguicosaNaoZeraReinicioNoMesmoDiaDoCumprimento() {
        Habito habito = habitoComStreak(3, 5);

        when(execucaoRepository.existsCumprimentoHabitoNaData(
                eq(habito.getId()), eq(SEGUNDA), eq(StatusExecucao.CONCLUIDO)))
                .thenReturn(false);

        service.registrarCumprimento(habito, TERCA, false);
        assertThat(habito.getStreakAtual()).isEqualTo(1);

        when(execucaoRepository.existsCumprimentoHabitoNaData(
                eq(habito.getId()), eq(TERCA), eq(StatusExecucao.CONCLUIDO)))
                .thenReturn(true);

        boolean mudou = service.aplicarQuebraPreguicosa(habito);

        assertThat(mudou).isFalse();
        assertThat(habito.getStreakAtual()).isEqualTo(1);
        assertThat(habito.getMelhorStreak()).isEqualTo(5);
    }

    @Test
    void t1_quebraPreguicosaZeraQuandoDiaAnteriorFaltosoEHojeSemCumprimento() {
        Habito habito = habitoComStreak(3, 5);

        when(execucaoRepository.existsCumprimentoHabitoNaData(
                eq(habito.getId()), eq(TERCA), eq(StatusExecucao.CONCLUIDO)))
                .thenReturn(false);
        when(execucaoRepository.existsCumprimentoHabitoNaData(
                eq(habito.getId()), eq(SEGUNDA), eq(StatusExecucao.CONCLUIDO)))
                .thenReturn(false);

        boolean mudou = service.aplicarQuebraPreguicosa(habito);

        assertThat(mudou).isTrue();
        assertThat(habito.getStreakAtual()).isEqualTo(0);
        assertThat(habito.getMelhorStreak()).isEqualTo(5);
    }

    @Test
    void ultimoDiaAgendadoAntesDevePularDiasForaDaLista() {
        LocalDate sexta = LocalDate.of(2026, 10, 23);
        LocalDate anterior = HabitoStreakService.ultimoDiaAgendadoAntes(
                sexta, Set.of(DiaSemana.SEGUNDA, DiaSemana.QUARTA));
        assertThat(anterior).isEqualTo(LocalDate.of(2026, 10, 21));
    }

    private Habito habitoComStreak(int streakAtual, int melhor) {
        return new Habito(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Academia",
                null,
                60,
                Set.of(DiaSemana.SEGUNDA, DiaSemana.TERCA, DiaSemana.QUARTA, DiaSemana.QUINTA, DiaSemana.SEXTA),
                null,
                StatusHabito.ATIVO,
                streakAtual,
                melhor,
                AGORA,
                AGORA
        );
    }
}
