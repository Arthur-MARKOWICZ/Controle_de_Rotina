package com.controlerotina.historico.dto;

import java.math.BigDecimal;

import com.controlerotina.historico.CalibracaoCalculadora;

/** {@code razao} e {@code desvioMedioMinutos} vêm nulos quando {@code suficiente} é falso. */
public record MetricasCalibracaoResponse(
        int amostras,
        boolean suficiente,
        BigDecimal razao,
        BigDecimal desvioMedioMinutos
) {
    public static MetricasCalibracaoResponse of(CalibracaoCalculadora.Metricas m) {
        return new MetricasCalibracaoResponse(m.amostras(), m.suficiente(), m.razao(), m.desvioMedioMinutos());
    }
}
