package com.controlerotina.usuario.dto;

import java.util.UUID;

import com.controlerotina.usuario.entity.Usuario;

public record UsuarioResponse(UUID id, String nome, String email) {

    public static UsuarioResponse from(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getNome(), usuario.getEmail());
    }
}
