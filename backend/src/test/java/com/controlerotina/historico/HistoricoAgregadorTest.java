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
}
