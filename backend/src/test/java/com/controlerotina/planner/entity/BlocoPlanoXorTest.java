package com.controlerotina.planner.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class BlocoPlanoXorTest {

    private static final Instant AGORA = Instant.parse("2026-10-20T12:00:00Z");

    @Test
    void t5_aceitaSomenteTarefaOuSomenteHabito() {
        UUID planoId = UUID.randomUUID();
        BlocoPlano deTarefa = new BlocoPlano(
                UUID.randomUUID(), planoId, UUID.randomUUID(), null, 30, 1, AGORA, AGORA);
        BlocoPlano deHabito = new BlocoPlano(
                UUID.randomUUID(), planoId, null, UUID.randomUUID(), 30, 1, AGORA, AGORA);

        assertThat(deTarefa.getTarefaId()).isNotNull();
        assertThat(deTarefa.getHabitoId()).isNull();
        assertThat(deHabito.getHabitoId()).isNotNull();
        assertThat(deHabito.getTarefaId()).isNull();
    }

    @Test
    void t5_rejeitaAmbosNullOuAmbosSet() {
        UUID planoId = UUID.randomUUID();
        assertThatThrownBy(() -> new BlocoPlano(
                UUID.randomUUID(), planoId, null, null, 30, 1, AGORA, AGORA))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new BlocoPlano(
                UUID.randomUUID(), planoId, UUID.randomUUID(), UUID.randomUUID(), 30, 1, AGORA, AGORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveRejeitarAmbosNullEmMetodoDedicado() {
        assertThatThrownBy(() -> new BlocoPlano(
                UUID.randomUUID(), UUID.randomUUID(), null, null, 15, 1, AGORA, AGORA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tarefa ou hábito");
    }

    @Test
    void deveRejeitarAmbosPreenchidosEmMetodoDedicado() {
        assertThatThrownBy(() -> new BlocoPlano(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 45, 2, AGORA, AGORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void construtorDeTarefaDeveZerarHabito() {
        UUID tarefaId = UUID.randomUUID();
        BlocoPlano bloco = new BlocoPlano(
                UUID.randomUUID(), UUID.randomUUID(), tarefaId, 60, 3, AGORA, AGORA);

        assertThat(bloco.getTarefaId()).isEqualTo(tarefaId);
        assertThat(bloco.getHabitoId()).isNull();
        assertThat(bloco.getDuracaoMinutos()).isEqualTo(60);
        assertThat(bloco.getOrdem()).isEqualTo(3);
    }

    @Test
    void deveAtualizarOrdemEDuracao() {
        BlocoPlano bloco = new BlocoPlano(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 30, 1, AGORA, AGORA);
        Instant depois = AGORA.plusSeconds(60);

        bloco.atualizarOrdem(4, depois);
        bloco.atualizarDuracao(90, depois);

        assertThat(bloco.getOrdem()).isEqualTo(4);
        assertThat(bloco.getDuracaoMinutos()).isEqualTo(90);
        assertThat(bloco.getUpdatedAt()).isEqualTo(depois);
    }

    @Test
    void deveAceitarHabitoSemTarefa() {
        UUID habitoId = UUID.randomUUID();
        BlocoPlano bloco = new BlocoPlano(
                UUID.randomUUID(), UUID.randomUUID(), null, habitoId, 20, 1, AGORA, AGORA);

        assertThat(bloco.getHabitoId()).isEqualTo(habitoId);
        assertThat(bloco.getTarefaId()).isNull();
    }
}
