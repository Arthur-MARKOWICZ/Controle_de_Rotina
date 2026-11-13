package com.controlerotina.historico;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.controlerotina.xp.entity.TipoXpEvento;

class HistoricoAgregadorTest {

    @Test
    void deveAgregarMetricasDoPeriodo() {
        PeriodoBRT.Intervalo dia = PeriodoBRT.intervaloDia(LocalDate.of(2026, 10, 27));

        HistoricoAgregador.Metricas metricas = HistoricoAgregador.agregar(
                dia,
                List.of(
                        new HistoricoAgregador.EventoXp(
                                TipoXpEvento.BLOCO_CONCLUIDO,
                                Instant.parse("2026-10-27T12:00:00Z"),
                                90
                        ),
                        new HistoricoAgregador.EventoXp(
                                TipoXpEvento.TAREFA_FINALIZADA,
                                Instant.parse("2026-10-27T13:00:00Z"),
                                9
                        ),
                        new HistoricoAgregador.EventoXp(
                                TipoXpEvento.BLOCO_CONCLUIDO,
                                Instant.parse("2026-10-26T12:00:00Z"),
                                50
                        )
                ),
                List.of(
                        new HistoricoAgregador.BlocoConcluido(Instant.parse("2026-10-27T12:00:00Z"), 60),
                        new HistoricoAgregador.BlocoConcluido(Instant.parse("2026-10-27T15:00:00Z"), 45),
                        new HistoricoAgregador.BlocoConcluido(Instant.parse("2026-10-28T03:00:00Z"), 30)
                )
        );

        assertThat(metricas.tarefasFinalizadas()).isEqualTo(1);
        assertThat(metricas.blocosConcluidos()).isEqualTo(2);
        assertThat(metricas.minutosTrabalhados()).isEqualTo(105);
        assertThat(metricas.xpGanho()).isEqualTo(99);
    }

    @Test
    void periodoVazioDeveRetornarZeros() {
        PeriodoBRT.Intervalo dia = PeriodoBRT.intervaloDia(LocalDate.of(2026, 10, 27));

        HistoricoAgregador.Metricas metricas = HistoricoAgregador.agregar(dia, List.of(), List.of());

        assertThat(metricas).isEqualTo(HistoricoAgregador.Metricas.zero());
    }

    @Test
    void eventoNoInicioInclusivoEntraENoFimExclusivoNaoEntra() {
        PeriodoBRT.Intervalo dia = PeriodoBRT.intervaloDia(LocalDate.of(2026, 10, 27));
        Instant inicio = dia.inicioInclusivo();
        Instant fim = dia.fimExclusivo();

        HistoricoAgregador.Metricas metricas = HistoricoAgregador.agregar(
                dia,
                List.of(
                        new HistoricoAgregador.EventoXp(TipoXpEvento.BLOCO_CONCLUIDO, inicio, 10),
                        new HistoricoAgregador.EventoXp(TipoXpEvento.BLOCO_CONCLUIDO, fim, 90)
                ),
                List.of(
                        new HistoricoAgregador.BlocoConcluido(inicio, 15),
                        new HistoricoAgregador.BlocoConcluido(fim, 60)
                )
        );

        assertThat(metricas.xpGanho()).isEqualTo(10);
        assertThat(metricas.blocosConcluidos()).isEqualTo(1);
        assertThat(metricas.minutosTrabalhados()).isEqualTo(15);
        assertThat(metricas.tarefasFinalizadas()).isZero();
    }

    @Test
    void soBlocoConcluidoNaoContaTarefaFinalizada() {
        PeriodoBRT.Intervalo dia = PeriodoBRT.intervaloDia(LocalDate.of(2026, 10, 27));

        HistoricoAgregador.Metricas metricas = HistoricoAgregador.agregar(
                dia,
                List.of(new HistoricoAgregador.EventoXp(
                        TipoXpEvento.BLOCO_CONCLUIDO,
                        Instant.parse("2026-10-27T12:00:00Z"),
                        40)),
                List.of()
        );

        assertThat(metricas.tarefasFinalizadas()).isZero();
        assertThat(metricas.xpGanho()).isEqualTo(40);
        assertThat(metricas.blocosConcluidos()).isZero();
    }

    @Test
    void viradaDeAnoBrtNaoMisturaDias() {
        PeriodoBRT.Intervalo dia31 = PeriodoBRT.intervaloDia(LocalDate.of(2026, 12, 31));
        Instant noDia31 = Instant.parse("2027-01-01T02:30:00Z");
        Instant dia1 = Instant.parse("2027-01-01T03:00:00Z");

        HistoricoAgregador.Metricas metricas = HistoricoAgregador.agregar(
                dia31,
                List.of(
                        new HistoricoAgregador.EventoXp(TipoXpEvento.TAREFA_FINALIZADA, noDia31, 9),
                        new HistoricoAgregador.EventoXp(TipoXpEvento.TAREFA_FINALIZADA, dia1, 9)
                ),
                List.of()
        );

        assertThat(metricas.tarefasFinalizadas()).isEqualTo(1);
        assertThat(metricas.xpGanho()).isEqualTo(9);
    }

    @Test
    void viradaDeMesBrtUsaFronteiraDoFuso() {
        PeriodoBRT.Intervalo outubro = PeriodoBRT.intervaloDia(LocalDate.of(2026, 10, 31));
        Instant aindaOutubro = Instant.parse("2026-11-01T02:59:59Z");
        Instant novembro = Instant.parse("2026-11-01T03:00:00Z");

        HistoricoAgregador.Metricas metricas = HistoricoAgregador.agregar(
                outubro,
                List.of(new HistoricoAgregador.EventoXp(TipoXpEvento.BLOCO_CONCLUIDO, aindaOutubro, 5)),
                List.of(new HistoricoAgregador.BlocoConcluido(novembro, 30))
        );

        assertThat(metricas.xpGanho()).isEqualTo(5);
        assertThat(metricas.blocosConcluidos()).isZero();
        assertThat(metricas.minutosTrabalhados()).isZero();
    }

    @Test
    void usuarioSemDadosEmSemanaInteiraDevolveZero() {
        PeriodoBRT.Intervalo semana = PeriodoBRT.intervaloSemana(LocalDate.of(2026, 10, 26));

        HistoricoAgregador.Metricas metricas = HistoricoAgregador.agregar(semana, List.of(), List.of());

        assertThat(metricas).isEqualTo(HistoricoAgregador.Metricas.zero());
    }

    @Test
    void naoDeveContarEventoNoLimiteExclusivoDoFim() {
        PeriodoBRT.Intervalo dia = PeriodoBRT.intervaloDia(LocalDate.of(2026, 10, 27));

        HistoricoAgregador.Metricas metricas = HistoricoAgregador.agregar(
                dia,
                List.of(new HistoricoAgregador.EventoXp(
                        TipoXpEvento.TAREFA_FINALIZADA, dia.fimExclusivo(), 9)),
                List.of(new HistoricoAgregador.BlocoConcluido(dia.fimExclusivo(), 30))
        );

        assertThat(metricas).isEqualTo(HistoricoAgregador.Metricas.zero());
    }

    @Test
    void deveIncluirEventoNoInicioInclusivo() {
        PeriodoBRT.Intervalo dia = PeriodoBRT.intervaloDia(LocalDate.of(2026, 10, 27));

        HistoricoAgregador.Metricas metricas = HistoricoAgregador.agregar(
                dia,
                List.of(new HistoricoAgregador.EventoXp(
                        TipoXpEvento.TAREFA_FINALIZADA, dia.inicioInclusivo(), 9)),
                List.of(new HistoricoAgregador.BlocoConcluido(dia.inicioInclusivo(), 15))
        );

        assertThat(metricas.tarefasFinalizadas()).isEqualTo(1);
        assertThat(metricas.blocosConcluidos()).isEqualTo(1);
        assertThat(metricas.minutosTrabalhados()).isEqualTo(15);
        assertThat(metricas.xpGanho()).isEqualTo(9);
    }

    @Test
    void naoDeveContarBlocoConcluidoComoTarefa() {
        PeriodoBRT.Intervalo dia = PeriodoBRT.intervaloDia(LocalDate.of(2026, 10, 27));

        HistoricoAgregador.Metricas metricas = HistoricoAgregador.agregar(
                dia,
                List.of(new HistoricoAgregador.EventoXp(
                        TipoXpEvento.BLOCO_CONCLUIDO, Instant.parse("2026-10-27T12:00:00Z"), 40)),
                List.of()
        );

        assertThat(metricas.tarefasFinalizadas()).isZero();
        assertThat(metricas.xpGanho()).isEqualTo(40);
        assertThat(metricas.blocosConcluidos()).isZero();
    }

    @Test
    void deveSomarVariosBlocosDoPeriodo() {
        PeriodoBRT.Intervalo dia = PeriodoBRT.intervaloDia(LocalDate.of(2026, 10, 27));

        HistoricoAgregador.Metricas metricas = HistoricoAgregador.agregar(
                dia,
                List.of(),
                List.of(
                        new HistoricoAgregador.BlocoConcluido(Instant.parse("2026-10-27T12:00:00Z"), 15),
                        new HistoricoAgregador.BlocoConcluido(Instant.parse("2026-10-27T18:00:00Z"), 45)
                )
        );

        assertThat(metricas.blocosConcluidos()).isEqualTo(2);
        assertThat(metricas.minutosTrabalhados()).isEqualTo(60);
    }

    @Test
    void deveIgnorarEventosAntesDoInicio() {
        PeriodoBRT.Intervalo dia = PeriodoBRT.intervaloDia(LocalDate.of(2026, 10, 27));

        HistoricoAgregador.Metricas metricas = HistoricoAgregador.agregar(
                dia,
                List.of(new HistoricoAgregador.EventoXp(
                        TipoXpEvento.TAREFA_FINALIZADA, dia.inicioInclusivo().minusSeconds(1), 9)),
                List.of()
        );

        assertThat(metricas.tarefasFinalizadas()).isZero();
        assertThat(metricas.xpGanho()).isZero();
    }
}
