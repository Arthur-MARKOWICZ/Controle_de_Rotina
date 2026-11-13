package com.controlerotina.habito.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
import com.controlerotina.historico.PeriodoBRT;
import com.controlerotina.planner.entity.StatusPlano;
import com.controlerotina.planner.repository.BlocoPlanoRepository;
import com.controlerotina.planner.repository.PlanoRepository;

@ExtendWith(MockitoExtension.class)
class HabitoDoDiaServiceTest {

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
    @Mock
    HabitoStreakService habitoStreakService;

    private HabitoDoDiaService service;

    @BeforeEach
    void setUp() {
        service = new HabitoDoDiaService(
                habitoRepository,
                planoRepository,
                blocoPlanoRepository,
                execucaoRepository,
                habitoStreakService,
                Clock.fixed(AGORA, PeriodoBRT.ZONE)
        );
    }

    @Test
    void deveListarHabitoDoWeekday() {
        stubVazio();
        when(habitoRepository.findByUsuarioIdAndStatusAndDeletedAtIsNull(USUARIO, StatusHabito.ATIVO))
                .thenReturn(List.of(habito(Set.of(DiaSemana.TERCA))));

        var itens = service.listarDoDia(USUARIO, TERCA);

        assertThat(itens).hasSize(1);
        assertThat(itens.get(0).id()).isEqualTo(HABITO_ID);
        assertThat(itens.get(0).cumprido()).isFalse();
        assertThat(itens.get(0).noPlanoAprovado()).isFalse();
    }

    @Test
    void deveRetornarListaVaziaQuandoUsuarioNaoTemHabitos() {
        stubVazio();
        when(habitoRepository.findByUsuarioIdAndStatusAndDeletedAtIsNull(USUARIO, StatusHabito.ATIVO))
                .thenReturn(List.of());

        assertThat(service.listarDoDia(USUARIO, TERCA)).isEmpty();
    }

    @Test
    void naoDeveListarHabitoDeOutroWeekday() {
        stubVazio();
        when(habitoRepository.findByUsuarioIdAndStatusAndDeletedAtIsNull(USUARIO, StatusHabito.ATIVO))
                .thenReturn(List.of(habito(Set.of(DiaSemana.SEGUNDA))));

        assertThat(service.listarDoDia(USUARIO, TERCA)).isEmpty();
    }

    @Test
    void deveUsarClockFixedQuandoDataNula() {
        stubVazio();
        when(habitoRepository.findByUsuarioIdAndStatusAndDeletedAtIsNull(USUARIO, StatusHabito.ATIVO))
                .thenReturn(List.of(habito(Set.of(DiaSemana.TERCA))));

        var itens = service.listarDoDia(USUARIO, null);

        assertThat(itens).hasSize(1);
    }

    @Test
    void deveListarIntervaloComDiaVazio() {
        LocalDate segunda = LocalDate.of(2026, 10, 19);
        stubVazio(segunda, TERCA);
        when(habitoRepository.findByUsuarioIdAndStatusAndDeletedAtIsNull(USUARIO, StatusHabito.ATIVO))
                .thenReturn(List.of(habito(Set.of(DiaSemana.TERCA))));

        var porDia = service.listarNoIntervalo(USUARIO, segunda, TERCA);

        assertThat(porDia.get(segunda)).isEmpty();
        assertThat(porDia.get(TERCA)).hasSize(1);
    }

    @Test
    void naoDeveListarHabitoPausadoPorqueConsultaSoAtivos() {
        stubVazio();
        when(habitoRepository.findByUsuarioIdAndStatusAndDeletedAtIsNull(USUARIO, StatusHabito.ATIVO))
                .thenReturn(List.of());

        assertThat(service.listarDoDia(USUARIO, TERCA)).isEmpty();
    }

    @Test
    void deveListarUmUnicoDiaSemPlanoNemExecucao() {
        stubVazio();
        when(habitoRepository.findByUsuarioIdAndStatusAndDeletedAtIsNull(USUARIO, StatusHabito.ATIVO))
                .thenReturn(List.of(habito(Set.of(DiaSemana.TERCA))));

        var porDia = service.listarNoIntervalo(USUARIO, TERCA, TERCA);

        assertThat(porDia).containsOnlyKeys(TERCA);
        assertThat(porDia.get(TERCA).get(0).execucaoAvulsa()).isNull();
        assertThat(porDia.get(TERCA).get(0).execucaoNoPlano()).isNull();
    }

    private void stubVazio() {
        stubVazio(TERCA, TERCA);
    }

    private void stubVazio(LocalDate de, LocalDate ate) {
        when(habitoStreakService.aplicarQuebraPreguicosaEmLote(any())).thenReturn(List.of());
        when(planoRepository.findByUsuarioIdAndStatusAndDataBetweenOrderByDataAscVersaoDesc(
                USUARIO, StatusPlano.APROVADO, de, ate)).thenReturn(List.of());
        when(planoRepository.findByUsuarioIdAndStatusAndDataBetweenOrderByDataAscVersaoDesc(
                USUARIO, StatusPlano.HABITO_AVULSO, de, ate)).thenReturn(List.of());
    }

    private static Habito habito(Set<DiaSemana> dias) {
        return new Habito(
                HABITO_ID, USUARIO, "Correr", null, 30,
                dias, null, StatusHabito.ATIVO, 0, 0, AGORA, AGORA);
    }
}
