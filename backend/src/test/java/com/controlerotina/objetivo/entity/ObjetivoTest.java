package com.controlerotina.objetivo.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ObjetivoTest {

    private static final Instant CRIADO = Instant.parse("2026-11-01T12:00:00Z");
    private static final Instant CONCLUIDO = Instant.parse("2026-11-10T12:00:00Z");
    private static final Instant ARQUIVADO = Instant.parse("2026-11-15T12:00:00Z");
    private static final Instant REABERTO = Instant.parse("2026-11-17T12:00:00Z");

    @Test
    void deveNascerAtivoSemDatasDeEncerramento() {
        Objetivo objetivo = novo();

        assertThat(objetivo.getStatus()).isEqualTo(StatusObjetivo.ATIVO);
        assertThat(objetivo.getConcluidoEm()).isNull();
        assertThat(objetivo.getArquivadoEm()).isNull();
        assertThat(objetivo.getUpdatedAt()).isEqualTo(CRIADO);
    }

    @Test
    void concluirPreencheSomenteConcluidoEm() {
        Objetivo objetivo = novo();

        objetivo.concluir(CONCLUIDO);

        assertThat(objetivo.getStatus()).isEqualTo(StatusObjetivo.CONCLUIDO);
        assertThat(objetivo.getConcluidoEm()).isEqualTo(CONCLUIDO);
        assertThat(objetivo.getArquivadoEm()).isNull();
    }

    @Test
    void arquivarAtivoNaoPreencheConcluidoEm() {
        Objetivo objetivo = novo();

        objetivo.arquivar(ARQUIVADO);

        assertThat(objetivo.getStatus()).isEqualTo(StatusObjetivo.ARQUIVADO);
        assertThat(objetivo.getArquivadoEm()).isEqualTo(ARQUIVADO);
        assertThat(objetivo.getConcluidoEm()).isNull();
    }

    @Test
    void arquivarConcluidoMantemConcluidoEm() {
        Objetivo objetivo = novo();
        objetivo.concluir(CONCLUIDO);

        objetivo.arquivar(ARQUIVADO);

        assertThat(objetivo.getStatus()).isEqualTo(StatusObjetivo.ARQUIVADO);
        assertThat(objetivo.getConcluidoEm()).isEqualTo(CONCLUIDO);
        assertThat(objetivo.getArquivadoEm()).isEqualTo(ARQUIVADO);
    }

    @Test
    void reabrirConcluidoEArquivadoLimpaAsDuasDatas() {
        Objetivo objetivo = novo();
        objetivo.concluir(CONCLUIDO);
        objetivo.arquivar(ARQUIVADO);

        objetivo.reabrir(REABERTO);

        assertThat(objetivo.getStatus()).isEqualTo(StatusObjetivo.ATIVO);
        assertThat(objetivo.getConcluidoEm()).isNull();
        assertThat(objetivo.getArquivadoEm()).isNull();
        assertThat(objetivo.getUpdatedAt()).isEqualTo(REABERTO);
    }

    @Test
    void atualizarComPrazoAusenteMantemPrazo() {
        Objetivo objetivo = novo();

        objetivo.atualizar("Novo", null, false, null, REABERTO);

        assertThat(objetivo.getTitulo()).isEqualTo("Novo");
        assertThat(objetivo.getDescricao()).isEqualTo("descrição");
        assertThat(objetivo.getPrazo()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void atualizarComPrazoNuloEDescricaoEmBrancoLimpaOsDois() {
        Objetivo objetivo = novo();

        objetivo.atualizar(null, "", true, null, REABERTO);

        assertThat(objetivo.getTitulo()).isEqualTo("Perder 5 kg");
        assertThat(objetivo.getDescricao()).isNull();
        assertThat(objetivo.getPrazo()).isNull();
    }

    private static Objetivo novo() {
        return new Objetivo(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Perder 5 kg",
                "descrição",
                LocalDate.of(2026, 12, 31),
                CRIADO
        );
    }
}
