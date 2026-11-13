package com.controlerotina.calendario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.controlerotina.capacidade.dto.CapacidadeResolvidaResponse;
import com.controlerotina.capacidade.entity.OrigemCapacidade;
import com.controlerotina.capacidade.service.CapacidadeResolvidaService;
import com.controlerotina.habito.service.HabitoDoDiaService;
import com.controlerotina.planner.entity.StatusPlano;
import com.controlerotina.planner.repository.BlocoPlanoRepository;
import com.controlerotina.planner.repository.PlanoRepository;
import com.controlerotina.shared.exception.IntervaloCapacidadeInvalidoException;
import com.controlerotina.tarefa.repository.TarefaRepository;

@ExtendWith(MockitoExtension.class)
class CalendarioServiceTest {

    private static final UUID USUARIO = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    CapacidadeResolvidaService capacidadeResolvidaService;
    @Mock
    PlanoRepository planoRepository;
    @Mock
    BlocoPlanoRepository blocoPlanoRepository;
    @Mock
    TarefaRepository tarefaRepository;
    @Mock
    HabitoDoDiaService habitoDoDiaService;

    private CalendarioService service;

    @BeforeEach
    void setUp() {
        service = new CalendarioService(
                capacidadeResolvidaService,
                planoRepository,
                blocoPlanoRepository,
                tarefaRepository,
                habitoDoDiaService
        );
    }

    @Test
    void deveRetornarDiasVaziosQuandoUsuarioNaoTemDados() {
        LocalDate de = LocalDate.of(2026, 10, 26);
        LocalDate ate = LocalDate.of(2026, 10, 27);
        stubConsultaVazia(de, ate);

        var response = service.consultar(USUARIO, de, ate);

        assertThat(response.dias()).hasSize(2);
        assertThat(response.dias()).allMatch(d -> d.plano() == null);
        assertThat(response.dias()).allMatch(d -> d.capacidadeMinutos() == 0);
    }

    @Test
    void deveRejeitarPeriodoInvertidoSemConsultarRepositorios() {
        assertThatThrownBy(() -> service.consultar(
                USUARIO, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 10, 1)))
                .isInstanceOf(IntervaloCapacidadeInvalidoException.class);
        verify(capacidadeResolvidaService, never()).resolver(any(), any(), any());
        verify(planoRepository, never()).findByUsuarioIdAndStatusAndDataBetweenOrderByDataAscVersaoDesc(
                any(), any(), any(), any());
    }

    @Test
    void deveRejeitarPeriodoAcimaDe31Dias() {
        assertThatThrownBy(() -> service.consultar(
                USUARIO, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1)))
                .isInstanceOf(IntervaloCapacidadeInvalidoException.class);
        verify(capacidadeResolvidaService, never()).resolver(any(), any(), any());
    }

    @Test
    void deveRejeitarDatasNulas() {
        assertThatThrownBy(() -> service.consultar(USUARIO, null, LocalDate.of(2026, 10, 1)))
                .isInstanceOf(IntervaloCapacidadeInvalidoException.class);
        assertThatThrownBy(() -> service.consultar(USUARIO, LocalDate.of(2026, 10, 1), null))
                .isInstanceOf(IntervaloCapacidadeInvalidoException.class);
    }

    @Test
    void deveAceitarPeriodoDe31Dias() {
        LocalDate de = LocalDate.of(2026, 10, 1);
        LocalDate ate = LocalDate.of(2026, 10, 31);
        stubConsultaVazia(de, ate);

        assertThat(service.consultar(USUARIO, de, ate).dias()).hasSize(31);
    }

    @Test
    void deveResolverUmUnicoDia() {
        LocalDate dia = LocalDate.of(2026, 10, 27);
        stubConsultaVazia(dia, dia);

        var response = service.consultar(USUARIO, dia, dia);

        assertThat(response.periodo().inicio()).isEqualTo(dia);
        assertThat(response.periodo().fim()).isEqualTo(dia);
        assertThat(response.dias()).hasSize(1);
    }

    @Test
    void deveAplicarCapacidadeResolvidaNoDia() {
        LocalDate dia = LocalDate.of(2026, 10, 27);
        when(capacidadeResolvidaService.resolver(USUARIO, dia, dia))
                .thenReturn(List.of(new CapacidadeResolvidaResponse(dia, 4, OrigemCapacidade.SEMANAL)));
        when(planoRepository.findByUsuarioIdAndStatusAndDataBetweenOrderByDataAscVersaoDesc(
                USUARIO, StatusPlano.APROVADO, dia, dia)).thenReturn(List.of());
        when(tarefaRepository.findAtivasComPrazoNoPeriodo(any(), any(), any(), any())).thenReturn(List.of());
        when(habitoDoDiaService.listarNoIntervalo(USUARIO, dia, dia)).thenReturn(Map.of());

        var response = service.consultar(USUARIO, dia, dia);

        assertThat(response.dias().get(0).capacidadeMinutos()).isEqualTo(240);
        assertThat(response.dias().get(0).origemCapacidade()).isEqualTo(OrigemCapacidade.SEMANAL);
    }

    private void stubConsultaVazia(LocalDate de, LocalDate ate) {
        when(capacidadeResolvidaService.resolver(USUARIO, de, ate)).thenReturn(List.of());
        when(planoRepository.findByUsuarioIdAndStatusAndDataBetweenOrderByDataAscVersaoDesc(
                USUARIO, StatusPlano.APROVADO, de, ate)).thenReturn(List.of());
        when(tarefaRepository.findAtivasComPrazoNoPeriodo(any(), any(), any(), any())).thenReturn(List.of());
        when(habitoDoDiaService.listarNoIntervalo(USUARIO, de, ate)).thenReturn(Map.of());
    }
}
