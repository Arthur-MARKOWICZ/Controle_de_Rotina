package com.controlerotina.xp.dto;

import java.util.List;

import com.controlerotina.xp.service.XpConcessaoService.ResultadoConcessao;

public record XpConclusaoResponse(
        int ganhoNesteRequest,
        List<XpEventoResumoResponse> eventos,
        XpProgressoResponse progresso
) {
    public static XpConclusaoResponse from(ResultadoConcessao resultado) {
        return new XpConclusaoResponse(
                resultado.ganhoNesteRequest(),
                resultado.eventos().stream().map(XpEventoResumoResponse::from).toList(),
                XpProgressoResponse.fromXpTotal(resultado.xpTotal())
        );
    }
}
