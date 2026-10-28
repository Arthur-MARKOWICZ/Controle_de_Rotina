package com.controlerotina.xp.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.controlerotina.usuario.entity.Usuario;

class XpProgressoResponseTest {

    @Test
    void t1_deveMapearProgressoDeUsuarioNovoComXpZeroENivel1() {
        Usuario usuario = new Usuario(
                UUID.randomUUID(),
                "novo@email.com",
                "hash",
                "Novo",
                Instant.parse("2026-10-27T12:00:00Z"),
                Instant.parse("2026-10-27T12:00:00Z")
        );

        XpProgressoResponse progresso = XpProgressoResponse.from(usuario);

        assertThat(progresso.xpTotal()).isZero();
        assertThat(progresso.nivel()).isEqualTo(1);
        assertThat(progresso.xpNoNivel()).isZero();
        assertThat(progresso.xpParaProximoNivel()).isEqualTo(10);
    }
}
