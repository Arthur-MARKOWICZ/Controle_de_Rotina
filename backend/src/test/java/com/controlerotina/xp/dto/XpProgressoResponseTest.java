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

    @Test
    void deveMapearXpTotalExatoDoCustoDoNivel() {
        XpProgressoResponse progresso = XpProgressoResponse.fromXpTotal(10);
        assertThat(progresso.nivel()).isEqualTo(2);
        assertThat(progresso.xpNoNivel()).isZero();
        assertThat(progresso.xpParaProximoNivel()).isEqualTo(20);
    }

    @Test
    void deveMapearXpComRestoNoNivel() {
        XpProgressoResponse progresso = XpProgressoResponse.fromXpTotal(11);
        assertThat(progresso.nivel()).isEqualTo(2);
        assertThat(progresso.xpNoNivel()).isEqualTo(1);
        assertThat(progresso.xpParaProximoNivel()).isEqualTo(20);
    }

    @Test
    void deveMapearXpNegativoComoNivel1() {
        XpProgressoResponse progresso = XpProgressoResponse.fromXpTotal(-5);
        assertThat(progresso.xpTotal()).isEqualTo(-5);
        assertThat(progresso.nivel()).isEqualTo(1);
        assertThat(progresso.xpNoNivel()).isEqualTo(-5);
    }

    @Test
    void fromDeveUsarXpTotalDoUsuario() {
        Usuario usuario = new Usuario(
                UUID.randomUUID(), "a@b.c", "h", "A",
                Instant.parse("2026-10-27T12:00:00Z"),
                Instant.parse("2026-10-27T12:00:00Z")
        );
        usuario.atualizarProgressoXp(25, 2, Instant.parse("2026-10-27T12:00:00Z"));

        XpProgressoResponse progresso = XpProgressoResponse.from(usuario);

        assertThat(progresso.xpTotal()).isEqualTo(25);
        assertThat(progresso.nivel()).isEqualTo(2);
        assertThat(progresso.xpNoNivel()).isEqualTo(15);
    }

    @Test
    void fromXpTotalZeroNaoDeveSubirNivel() {
        XpProgressoResponse progresso = XpProgressoResponse.fromXpTotal(0);
        assertThat(progresso.nivel()).isEqualTo(1);
        assertThat(progresso.xpNoNivel()).isZero();
    }

    @Test
    void fromXpTotalGrandeDeveAvancarVariosNiveis() {
        XpProgressoResponse progresso = XpProgressoResponse.fromXpTotal(10 + 20 + 30);
        assertThat(progresso.nivel()).isEqualTo(4);
        assertThat(progresso.xpNoNivel()).isZero();
        assertThat(progresso.xpParaProximoNivel()).isEqualTo(40);
    }
}
