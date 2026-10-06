package com.controlerotina.usuario.dto;

import com.controlerotina.usuario.validation.SenhaForte;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CadastroUsuarioRequest(
        @NotBlank
        @Size(max = 100)
        String nome,

        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @NotBlank
        @SenhaForte
        String senha
) {
    public CadastroUsuarioRequest {
        if (nome != null) {
            nome = nome.trim();
        }
        if (email != null) {
            email = email.trim();
        }
    }
}
