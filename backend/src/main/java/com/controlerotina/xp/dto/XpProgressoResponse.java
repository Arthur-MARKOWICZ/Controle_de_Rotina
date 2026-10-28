package com.controlerotina.xp.dto;

import com.controlerotina.usuario.entity.Usuario;
import com.controlerotina.xp.XpMotor;

public record XpProgressoResponse(
        int xpTotal,
        int nivel,
        int xpNoNivel,
        int xpParaProximoNivel
) {
    public static XpProgressoResponse from(Usuario usuario) {
        return fromXpTotal(usuario.getXpTotal());
    }

    public static XpProgressoResponse fromXpTotal(int xpTotal) {
        XpMotor.ProgressoXp progresso = XpMotor.progresso(xpTotal);
        return new XpProgressoResponse(
                xpTotal,
                progresso.nivel(),
                progresso.xpNoNivel(),
                progresso.xpParaProximoNivel()
        );
    }
}
