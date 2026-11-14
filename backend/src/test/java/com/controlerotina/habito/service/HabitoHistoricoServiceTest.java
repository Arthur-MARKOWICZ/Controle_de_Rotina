package com.controlerotina.habito.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

import com.controlerotina.execucao.entity.StatusExecucao;
import com.controlerotina.execucao.repository.CumprimentoHabitoLinha;
import com.controlerotina.execucao.repository.ExecucaoRepository;
import com.controlerotina.habito.dto.HabitoHistoricoDiaResponse;
import com.controlerotina.habito.dto.OrigemTempo;
import com.controlerotina.habito.entity.Habito;
import com.controlerotina.habito.repository.HabitoRepository;
import com.controlerotina.shared.exception.HabitoNaoEncontradoException;
import com.controlerotina.shared.exception.IntervaloCapacidadeInvalidoException;

@ExtendWith(MockitoExtension.class)
class HabitoHistoricoServiceTest {

    private static final UUID USUARIO = UUID.randomUUID();
    private static final UUID HABITO = UUID.randomUUID();
    private static final LocalDate DIA = LocalDate.of(2026, 11, 10);
    private static final Instant DEZ_HORAS = Instant.parse("2026-11-10T13:00:00Z");

    @Mock
    HabitoRepository habitoRepository;

    @Mock
    ExecucaoRepository execucaoRepository;

    HabitoHistoricoService service;

    @BeforeEach
    void setUp() {
        // 00:30 de 15/11 em BRT ainda é 15/11; em UTC já seria 15/11 03:30.
        Clock clock = Clock.fixed(Instant.parse("2026-11-15T03:30:00Z"), ZoneOffset.UTC);
        service = new HabitoHistoricoService(habitoRepository, execucaoRepository, clock);
    }

    @Test
    void deveMarcarInsuficienteQuandoTimerFicaAbaixoDaMeta() {
        List<HabitoHistoricoDiaResponse> dias = HabitoHistoricoService.agrupar(List.of(
                timer(DIA, 30, 25)));

        assertThat(dias).containsExactly(
                new HabitoHistoricoDiaResponse(DIA, 25, 30, false, OrigemTempo.TIMER));
    }

    @Test
    void deveMarcarSuficienteQuandoMinutosInformadosBatemAMeta() {
        List<HabitoHistoricoDiaResponse> dias = HabitoHistoricoService.agrupar(List.of(
                informado(DIA, 30, 30)));

        assertThat(dias).containsExactly(
                new HabitoHistoricoDiaResponse(DIA, 30, 30, true, OrigemTempo.INFORMADO));
    }

    @Test
    void deveSomarExecucoesDoMesmoDiaComOrigemMistaEMetaMaior() {
        List<HabitoHistoricoDiaResponse> dias = HabitoHistoricoService.agrupar(List.of(
                timer(DIA, 30, 15),
                informado(DIA, 45, 20)));

        assertThat(dias).containsExactly(
                new HabitoHistoricoDiaResponse(DIA, 35, 45, false, OrigemTempo.MISTO));
    }

    @Test
    void deveManterMetaDoDiaMesmoQueDuracaoAtualSejaOutra() {
        List<HabitoHistoricoDiaResponse> dias = HabitoHistoricoService.agrupar(List.of(
                informado(DIA, 45, 45),
                informado(DIA.minusWeeks(1), 30, 30)));

        assertThat(dias).extracting(HabitoHistoricoDiaResponse::metaMinutos).containsExactly(45, 30);
        assertThat(dias).extracting(HabitoHistoricoDiaResponse::data).containsExactly(DIA, DIA.minusWeeks(1));
    }

    @Test
    void concluirDiretoAntigoSemMinutosDeveAparecerComZeroMinutosPeloTimer() {
        CumprimentoHabitoLinha antigo = new CumprimentoHabitoLinha(DIA, 30, DEZ_HORAS, DEZ_HORAS, null);

        assertThat(HabitoHistoricoService.agrupar(List.of(antigo))).containsExactly(
                new HabitoHistoricoDiaResponse(DIA, 0, 30, false, OrigemTempo.TIMER));
    }

    @Test
    void deveRetornarListaVaziaQuandoNaoHaCumprimentos() {
        when(habitoRepository.findByIdAndUsuarioId(HABITO, USUARIO)).thenReturn(Optional.of(mock(Habito.class)));
        when(execucaoRepository.listarCumprimentosHabitoNoIntervalo(any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        assertThat(service.listar(USUARIO, HABITO, DIA, DIA)).isEmpty();
    }

    @Test
    void deveUsarUltimos84DiasEmBrtQuandoPeriodoNaoInformado() {
        when(habitoRepository.findByIdAndUsuarioId(HABITO, USUARIO)).thenReturn(Optional.of(mock(Habito.class)));

        service.listar(USUARIO, HABITO, null, null);

        LocalDate hojeBrt = LocalDate.of(2026, 11, 15);
        verify(execucaoRepository).listarCumprimentosHabitoNoIntervalo(
                USUARIO, HABITO, hojeBrt.minusDays(83), hojeBrt, StatusExecucao.CONCLUIDO);
    }

    @Test
    void deveArredondarSegundosDoTimerParaMinutos() {
        CumprimentoHabitoLinha linha = new CumprimentoHabitoLinha(
                DIA, 30, DEZ_HORAS, DEZ_HORAS.plusSeconds(29 * 60 + 40), null);

        assertThat(HabitoHistoricoService.minutosDedicados(linha)).isEqualTo(30);
    }

    @Test
    void deveLancarIntervaloInvalidoSemConsultarQuandoSoUmaDataVem() {
        assertThatThrownBy(() -> service.listar(USUARIO, HABITO, DIA, null))
                .isInstanceOf(IntervaloCapacidadeInvalidoException.class);
        verifyNoInteractions(habitoRepository, execucaoRepository);
    }

    @Test
    void deveLancarIntervaloInvalidoQuandoPassaDe366Dias() {
        assertThatThrownBy(() -> service.listar(USUARIO, HABITO, DIA.minusDays(366), DIA))
                .isInstanceOf(IntervaloCapacidadeInvalidoException.class);
        verifyNoInteractions(execucaoRepository);
    }

    @Test
    void deveLancarHabitoNaoEncontradoSemConsultarExecucoes() {
        when(habitoRepository.findByIdAndUsuarioId(HABITO, USUARIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listar(USUARIO, HABITO, DIA, DIA))
                .isInstanceOf(HabitoNaoEncontradoException.class);
        verify(execucaoRepository, never()).listarCumprimentosHabitoNoIntervalo(
                any(), eq(HABITO), any(), any(), any());
    }

    private static CumprimentoHabitoLinha timer(LocalDate data, int meta, int minutos) {
        return new CumprimentoHabitoLinha(data, meta, DEZ_HORAS, DEZ_HORAS.plusSeconds(minutos * 60L), null);
    }

    private static CumprimentoHabitoLinha informado(LocalDate data, int meta, int minutos) {
        return new CumprimentoHabitoLinha(data, meta, DEZ_HORAS, DEZ_HORAS, minutos);
    }
}
