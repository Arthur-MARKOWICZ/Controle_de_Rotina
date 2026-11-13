package com.controlerotina.historico.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.controlerotina.historico.CalibracaoCalculadora;

public record RecorteTagCalibracaoResponse(
        UUID tagId,
        String nome,
        String cor,
        int amostras,
        boolean suficiente,
        BigDecimal razao,
        BigDecimal desvioMedioMinutos
) {
    public static RecorteTagCalibracaoResponse of(CalibracaoCalculadora.RecorteTag r) {
        var m = r.metricas();
        return new RecorteTagCalibracaoResponse(
                r.tag().id(), r.tag().nome(), r.tag().cor(),
                m.amostras(), m.suficiente(), m.razao(), m.desvioMedioMinutos());
    }
}
