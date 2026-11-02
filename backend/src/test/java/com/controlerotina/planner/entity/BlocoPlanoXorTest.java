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
}
