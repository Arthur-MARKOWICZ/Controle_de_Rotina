package com.controlerotina.usuario.dto;

import java.util.UUID;

import com.controlerotina.usuario.entity.Usuario;
import com.controlerotina.xp.dto.XpProgressoResponse;

public record UsuarioResponse(
        UUID id,
        String nome,
        String email,
        int xpTotal,
        int nivel,
        int xpNoNivel,
        int xpParaProximoNivel
) {

    public static UsuarioResponse from(Usuario usuario) {
        XpProgressoResponse progresso = XpProgressoResponse.from(usuario);
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                progresso.xpTotal(),
                progresso.nivel(),
                progresso.xpNoNivel(),
                progresso.xpParaProximoNivel()
        );
    }
}
