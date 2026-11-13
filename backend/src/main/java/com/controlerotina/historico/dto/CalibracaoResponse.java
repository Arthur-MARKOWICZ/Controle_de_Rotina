package com.controlerotina.historico.dto;

import java.time.LocalDate;
import java.util.List;

public record CalibracaoResponse(
        Janela janela,
        int minimoAmostras,
        MetricasCalibracaoResponse geral,
        List<RecorteTagCalibracaoResponse> porTag,
        List<RecorteProjetoCalibracaoResponse> porProjeto
) {
    public record Janela(LocalDate inicio, LocalDate fim) {
    }
}
