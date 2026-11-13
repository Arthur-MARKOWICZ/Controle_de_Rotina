package com.controlerotina.historico.service;

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
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.controlerotina.execucao.entity.StatusExecucao;
import com.controlerotina.execucao.repository.ExecucaoRepository;
import com.controlerotina.historico.PeriodoBRT;
import com.controlerotina.historico.SemanaInicioInvalidoException;
import com.controlerotina.projeto.repository.ProjetoRepository;
import com.controlerotina.tag.repository.TagRepository;
import com.controlerotina.tag.repository.TarefaTagRepository;
import com.controlerotina.xp.entity.TipoXpEvento;
import com.controlerotina.xp.repository.XpEventoRepository;

@ExtendWith(MockitoExtension.class)
class HistoricoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-10-27T15:00:00Z");
    private static final UUID USUARIO = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Mock
    XpEventoRepository xpEventoRepository;
    @Mock
    ExecucaoRepository execucaoRepository;
    @Mock
    TarefaTagRepository tarefaTagRepository;
    @Mock
    TagRepository tagRepository;
    @Mock
    ProjetoRepository projetoRepository;

    private HistoricoService service;

    @BeforeEach
    void setUp() {
        service = new HistoricoService(
                xpEventoRepository,
                execucaoRepository,
                tarefaTagRepository,
                tagRepository,
                projetoRepository,
                Clock.fixed(AGORA, ZoneOffset.UTC)
        );
    }

    @Test
    void deveResumirDiaComMetricasDoUsuario() {
        LocalDate dia = LocalDate.of(2026, 10, 27);
        PeriodoBRT.Intervalo intervalo = PeriodoBRT.intervaloDia(dia);
        when(xpEventoRepository.contarPorTipoNoPeriodo(
                USUARIO, TipoXpEvento.TAREFA_FINALIZADA, intervalo.inicioInclusivo(), intervalo.fimExclusivo()))
                .thenReturn(2L);
        when(xpEventoRepository.somarQuantidadeNoPeriodo(
                USUARIO, intervalo.inicioInclusivo(), intervalo.fimExclusivo()))
                .thenReturn(99L);
        when(execucaoRepository.contarConcluidasNoPeriodo(
                USUARIO, StatusExecucao.CONCLUIDO, intervalo.inicioInclusivo(), intervalo.fimExclusivo()))
                .thenReturn(3L);
        when(execucaoRepository.somarDuracaoMinutosConcluidasNoPeriodo(
                USUARIO, StatusExecucao.CONCLUIDO, intervalo.inicioInclusivo(), intervalo.fimExclusivo()))
                .thenReturn(120L);

        var resumo = service.resumirDia(USUARIO, dia);

        assertThat(resumo.periodo().escopo()).isEqualTo("DIA");
        assertThat(resumo.tarefasFinalizadas()).isEqualTo(2);
        assertThat(resumo.xpGanho()).isEqualTo(99);
        assertThat(resumo.blocosConcluidos()).isEqualTo(3);
        assertThat(resumo.minutosTrabalhados()).isEqualTo(120);
    }

    @Test
    void usuarioSemDadosDeveDevolverZerosSemConsultarTags() {
        LocalDate dia = LocalDate.of(2026, 10, 27);
        when(xpEventoRepository.contarPorTipoNoPeriodo(any(), any(), any(), any())).thenReturn(0L);
        when(xpEventoRepository.somarQuantidadeNoPeriodo(any(), any(), any())).thenReturn(0L);
        when(execucaoRepository.contarConcluidasNoPeriodo(any(), any(), any(), any())).thenReturn(0L);
        when(execucaoRepository.somarDuracaoMinutosConcluidasNoPeriodo(any(), any(), any(), any())).thenReturn(0L);

        var resumo = service.resumirDia(USUARIO, dia);

        assertThat(resumo.tarefasFinalizadas()).isZero();
        assertThat(resumo.xpGanho()).isZero();
        verify(tagRepository, never()).findAllById(any());
        verify(projetoRepository, never()).findAllById(any());
    }

    @Test
    void dataNulaUsaHojeDoClockFixoEmBrt() {
        LocalDate hojeBrt = LocalDate.of(2026, 10, 27);
        PeriodoBRT.Intervalo intervalo = PeriodoBRT.intervaloDia(hojeBrt);
        when(xpEventoRepository.contarPorTipoNoPeriodo(any(), any(), any(), any())).thenReturn(0L);
        when(xpEventoRepository.somarQuantidadeNoPeriodo(any(), any(), any())).thenReturn(0L);
        when(execucaoRepository.contarConcluidasNoPeriodo(any(), any(), any(), any())).thenReturn(0L);
        when(execucaoRepository.somarDuracaoMinutosConcluidasNoPeriodo(any(), any(), any(), any())).thenReturn(0L);

        var resumo = service.resumirDia(USUARIO, null);

        assertThat(resumo.periodo().inicio()).isEqualTo(hojeBrt);
        verify(xpEventoRepository).somarQuantidadeNoPeriodo(
                eq(USUARIO), eq(intervalo.inicioInclusivo()), eq(intervalo.fimExclusivo()));
    }

    @Test
    void semanaQueNaoComecaNaSegundaDeveLancarSemAlterarEstado() {
        LocalDate terca = LocalDate.of(2026, 10, 27);

        assertThatThrownBy(() -> service.resumirSemana(USUARIO, terca))
                .isInstanceOf(SemanaInicioInvalidoException.class)
                .satisfies(ex -> {
                    SemanaInicioInvalidoException erro = (SemanaInicioInvalidoException) ex;
                    assertThat(erro.getStatusCode().value()).isEqualTo(400);
                    assertThat(erro.getBody().getTitle()).isEqualTo("semanaInicio inválido");
                });
        verify(xpEventoRepository, never()).somarQuantidadeNoPeriodo(any(), any(), any());
        verify(execucaoRepository, never()).contarConcluidasNoPeriodo(any(), any(), any(), any());
    }

    @Test
    void semanaSegundaDeveConsultarIntervaloDe7Dias() {
        LocalDate segunda = LocalDate.of(2026, 10, 26);
        PeriodoBRT.Intervalo intervalo = PeriodoBRT.intervaloSemana(segunda);
        when(xpEventoRepository.contarPorTipoNoPeriodo(any(), any(), any(), any())).thenReturn(1L);
        when(xpEventoRepository.somarQuantidadeNoPeriodo(any(), any(), any())).thenReturn(10L);
        when(execucaoRepository.contarConcluidasNoPeriodo(any(), any(), any(), any())).thenReturn(1L);
        when(execucaoRepository.somarDuracaoMinutosConcluidasNoPeriodo(any(), any(), any(), any())).thenReturn(30L);

        var resumo = service.resumirSemana(USUARIO, segunda);

        assertThat(resumo.periodo().escopo()).isEqualTo("SEMANA");
        assertThat(resumo.periodo().inicio()).isEqualTo(segunda);
        assertThat(resumo.periodo().fim()).isEqualTo(LocalDate.of(2026, 11, 1));
        verify(execucaoRepository).somarDuracaoMinutosConcluidasNoPeriodo(
                USUARIO, StatusExecucao.CONCLUIDO, intervalo.inicioInclusivo(), intervalo.fimExclusivo());
    }

    @Test
    void outroUsuarioNaoReaproveitaContagens() {
        UUID outro = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        LocalDate dia = LocalDate.of(2026, 10, 27);
        when(xpEventoRepository.contarPorTipoNoPeriodo(any(), any(), any(), any())).thenReturn(0L);
        when(xpEventoRepository.somarQuantidadeNoPeriodo(any(), any(), any())).thenReturn(0L);
        when(execucaoRepository.contarConcluidasNoPeriodo(any(), any(), any(), any())).thenReturn(0L);
        when(execucaoRepository.somarDuracaoMinutosConcluidasNoPeriodo(any(), any(), any(), any())).thenReturn(0L);

        service.resumirDia(outro, dia);

        verify(xpEventoRepository).contarPorTipoNoPeriodo(
                eq(outro), eq(TipoXpEvento.TAREFA_FINALIZADA), any(), any());
        verify(xpEventoRepository, never()).contarPorTipoNoPeriodo(
                eq(USUARIO), any(), any(), any());
    }

    @Test
    void viradaDeMesUsaFronteiraBrtDoClock() {
        Instant quaseNovembro = Instant.parse("2026-11-01T02:59:59Z");
        HistoricoService noLimite = new HistoricoService(
                xpEventoRepository,
                execucaoRepository,
                tarefaTagRepository,
                tagRepository,
                projetoRepository,
                Clock.fixed(quaseNovembro, ZoneOffset.UTC)
        );
        PeriodoBRT.Intervalo outubro31 = PeriodoBRT.intervaloDia(LocalDate.of(2026, 10, 31));
        when(xpEventoRepository.contarPorTipoNoPeriodo(any(), any(), any(), any())).thenReturn(0L);
        when(xpEventoRepository.somarQuantidadeNoPeriodo(any(), any(), any())).thenReturn(0L);
        when(execucaoRepository.contarConcluidasNoPeriodo(any(), any(), any(), any())).thenReturn(0L);
        when(execucaoRepository.somarDuracaoMinutosConcluidasNoPeriodo(any(), any(), any(), any())).thenReturn(0L);

        var resumo = noLimite.resumirDia(USUARIO, null);

        assertThat(resumo.periodo().inicio()).isEqualTo(LocalDate.of(2026, 10, 31));
        verify(xpEventoRepository).somarQuantidadeNoPeriodo(
                eq(USUARIO), eq(outubro31.inicioInclusivo()), eq(outubro31.fimExclusivo()));
    }
    @Test
    void semanaNulaNoDomingoAs2330BrtDeveUsarASemanaQueComecouNaSegundaAnterior() {
        HistoricoService domingoNoite = comRelogio(Instant.parse("2026-11-16T02:30:00Z"));
        PeriodoBRT.Intervalo semana = PeriodoBRT.intervaloSemana(LocalDate.of(2026, 11, 9));
        zerarMetricas();

        var resumo = domingoNoite.resumirSemana(USUARIO, null);

        assertThat(resumo.periodo().inicio()).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(resumo.periodo().fim()).isEqualTo(LocalDate.of(2026, 11, 15));
        verify(xpEventoRepository).somarQuantidadeNoPeriodo(
                eq(USUARIO), eq(semana.inicioInclusivo()), eq(semana.fimExclusivo()));
    }

    @Test
    void semanaNulaNaSegundaAs0005BrtDeveVirarParaANovaSemana() {
        HistoricoService segundaCedo = comRelogio(Instant.parse("2026-11-16T03:05:00Z"));
        zerarMetricas();

        var resumo = segundaCedo.resumirSemana(USUARIO, null);

        assertThat(resumo.periodo().inicio()).isEqualTo(LocalDate.of(2026, 11, 16));
        assertThat(resumo.periodo().fim()).isEqualTo(LocalDate.of(2026, 11, 22));
    }

    private HistoricoService comRelogio(Instant agora) {
        return new HistoricoService(
                xpEventoRepository,
                execucaoRepository,
                tarefaTagRepository,
                tagRepository,
                projetoRepository,
                Clock.fixed(agora, ZoneOffset.UTC)
        );
    }

    private void zerarMetricas() {
        when(xpEventoRepository.contarPorTipoNoPeriodo(any(), any(), any(), any())).thenReturn(0L);
        when(xpEventoRepository.somarQuantidadeNoPeriodo(any(), any(), any())).thenReturn(0L);
        when(execucaoRepository.contarConcluidasNoPeriodo(any(), any(), any(), any())).thenReturn(0L);
        when(execucaoRepository.somarDuracaoMinutosConcluidasNoPeriodo(any(), any(), any(), any())).thenReturn(0L);
    }
}
