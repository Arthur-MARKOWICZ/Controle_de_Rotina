package com.controlerotina.historico.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.controlerotina.historico.CalibracaoCalculadora;

public record RecorteProjetoCalibracaoResponse(
        UUID projetoId,
        String nome,
        int amostras,
        boolean suficiente,
        BigDecimal razao,
        BigDecimal desvioMedioMinutos
) {
    public static RecorteProjetoCalibracaoResponse of(CalibracaoCalculadora.RecorteProjeto r) {
        var m = r.metricas();
        return new RecorteProjetoCalibracaoResponse(
                r.projeto().id(), r.projeto().nome(),
                m.amostras(), m.suficiente(), m.razao(), m.desvioMedioMinutos());
    }
}
