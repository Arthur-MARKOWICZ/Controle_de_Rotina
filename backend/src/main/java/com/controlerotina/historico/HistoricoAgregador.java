package com.controlerotina.historico;

import java.time.Instant;
import java.util.List;

import com.controlerotina.xp.entity.TipoXpEvento;

public final class HistoricoAgregador {

    private HistoricoAgregador() {
    }

    public static Metricas agregar(
            PeriodoBRT.Intervalo intervalo,
            List<EventoXp> eventos,
            List<BlocoConcluido> blocos
    ) {
        Instant inicio = intervalo.inicioInclusivo();
        Instant fim = intervalo.fimExclusivo();

        long tarefasFinalizadas = eventos.stream()
                .filter(e -> e.tipo() == TipoXpEvento.TAREFA_FINALIZADA)
                .filter(e -> noPeriodo(e.createdAt(), inicio, fim))
                .count();

        long xpGanho = eventos.stream()
                .filter(e -> noPeriodo(e.createdAt(), inicio, fim))
                .mapToLong(EventoXp::quantidade)
                .sum();

        List<BlocoConcluido> noPeriodo = blocos.stream()
                .filter(b -> noPeriodo(b.concluidoEm(), inicio, fim))
                .toList();

        long blocosConcluidos = noPeriodo.size();
        long minutosTrabalhados = noPeriodo.stream().mapToLong(BlocoConcluido::duracaoMinutos).sum();

        return new Metricas(
                (int) tarefasFinalizadas,
                (int) blocosConcluidos,
                (int) minutosTrabalhados,
                (int) xpGanho
        );
    }

    private static boolean noPeriodo(Instant instante, Instant inicio, Instant fim) {
        return !instante.isBefore(inicio) && instante.isBefore(fim);
    }

    public record EventoXp(TipoXpEvento tipo, Instant createdAt, int quantidade) {
    }

    public record BlocoConcluido(Instant concluidoEm, int duracaoMinutos) {
    }

    public record Metricas(
            int tarefasFinalizadas,
            int blocosConcluidos,
            int minutosTrabalhados,
            int xpGanho
    ) {
        public static Metricas zero() {
            return new Metricas(0, 0, 0, 0);
        }
    }
}
