package com.controlerotina.usuario.dto;

import com.controlerotina.usuario.validation.SenhaForte;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AtualizarMeRequest(
        @NotBlank
        String senhaAtual,

        @Size(max = 100)
        String nome,

        @Email
        @Size(max = 255)
        String email,

        @SenhaForte
        String novaSenha
) {
    public AtualizarMeRequest {
        if (nome != null) {
            nome = nome.trim();
            if (nome.isEmpty()) {
                nome = null;
            }
        }
        if (email != null) {
            email = email.trim();
            if (email.isEmpty()) {
                email = null;
            }
        }
    }
}
