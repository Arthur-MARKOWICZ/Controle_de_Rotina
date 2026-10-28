package com.controlerotina.xp.dto;

import com.controlerotina.xp.entity.TipoXpEvento;
import com.controlerotina.xp.entity.XpEvento;

public record XpEventoResumoResponse(TipoXpEvento tipo, int quantidade) {

    public static XpEventoResumoResponse from(XpEvento evento) {
        return new XpEventoResumoResponse(evento.getTipo(), evento.getQuantidade());
    }
}
