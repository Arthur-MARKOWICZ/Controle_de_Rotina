package com.controlerotina.historico.dto;

import com.controlerotina.historico.HistoricoAgregador;

public record HistoricoResumoResponse(
        PeriodoHistoricoResponse periodo,
        int tarefasFinalizadas,
        int blocosConcluidos,
        int minutosTrabalhados,
        int xpGanho
) {
    public static HistoricoResumoResponse of(
            PeriodoHistoricoResponse periodo,
            HistoricoAgregador.Metricas metricas
    ) {
        return new HistoricoResumoResponse(
                periodo,
                metricas.tarefasFinalizadas(),
                metricas.blocosConcluidos(),
                metricas.minutosTrabalhados(),
                metricas.xpGanho()
        );
    }
}
