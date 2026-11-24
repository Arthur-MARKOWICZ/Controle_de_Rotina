package com.controlerotina.tarefa.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class TarefaTest {

    private static final Instant CRIADA = Instant.parse("2026-11-01T12:00:00Z");
    private static final Instant FINALIZADA = Instant.parse("2026-11-05T12:00:00Z");
    private static final Instant DEPOIS = Instant.parse("2026-11-06T12:00:00Z");
    private static final Instant REFINALIZADA = Instant.parse("2026-11-09T12:00:00Z");

    @Test
    void deveNascerSemDataDeFinalizacao() {
        assertThat(nova().getFinalizadaEm()).isNull();
    }

    @Test
    void deveGravarOInstanteAoFinalizar() {
        Tarefa tarefa = nova();

        tarefa.atualizar(null, null, null, null, StatusTarefa.FINALIZADA, null, FINALIZADA);

        assertThat(tarefa.getStatus()).isEqualTo(StatusTarefa.FINALIZADA);
        assertThat(tarefa.getFinalizadaEm()).isEqualTo(FINALIZADA);
    }

    @Test
    void naoDeveMudarADataQuandoFinalizadaRecebeFinalizadaDeNovo() {
        Tarefa tarefa = nova();
        tarefa.atualizar(null, null, null, null, StatusTarefa.FINALIZADA, null, FINALIZADA);

        tarefa.atualizar(null, null, null, null, StatusTarefa.FINALIZADA, null, DEPOIS);

        assertThat(tarefa.getFinalizadaEm()).isEqualTo(FINALIZADA);
        assertThat(tarefa.getUpdatedAt()).isEqualTo(DEPOIS);
    }

    @Test
    void deveLimparADataAoReabrir() {
        Tarefa tarefa = nova();
        tarefa.atualizar(null, null, null, null, StatusTarefa.FINALIZADA, null, FINALIZADA);

        tarefa.atualizar(null, null, null, null, StatusTarefa.PENDENTE, null, DEPOIS);

        assertThat(tarefa.getFinalizadaEm()).isNull();
    }

    @Test
    void deveLimparADataAoCancelarTarefaFinalizada() {
        Tarefa tarefa = nova();
        tarefa.atualizar(null, null, null, null, StatusTarefa.FINALIZADA, null, FINALIZADA);

        tarefa.atualizar(null, null, null, null, StatusTarefa.CANCELADA, null, DEPOIS);

        assertThat(tarefa.getFinalizadaEm()).isNull();
    }

    @Test
    void deveGravarOInstanteNovoAoFinalizarDeNovoDepoisDeReabrir() {
        Tarefa tarefa = nova();
        tarefa.atualizar(null, null, null, null, StatusTarefa.FINALIZADA, null, FINALIZADA);
        tarefa.atualizar(null, null, null, null, StatusTarefa.PENDENTE, null, DEPOIS);

        tarefa.atualizar(null, null, null, null, StatusTarefa.FINALIZADA, null, REFINALIZADA);

        assertThat(tarefa.getFinalizadaEm()).isEqualTo(REFINALIZADA);
    }

    @Test
    void naoDeveMexerNaDataQuandoStatusNaoVem() {
        Tarefa tarefa = nova();
        tarefa.atualizar(null, null, null, null, StatusTarefa.FINALIZADA, null, FINALIZADA);

        tarefa.atualizar("Novo título", null, null, null, null, null, DEPOIS);

        assertThat(tarefa.getTitulo()).isEqualTo("Novo título");
        assertThat(tarefa.getFinalizadaEm()).isEqualTo(FINALIZADA);
    }

    @Test
    void naoDeveCriarDataQuandoOStatusAbertoMudaEntreAbertos() {
        Tarefa tarefa = nova();

        tarefa.atualizar(null, null, null, null, StatusTarefa.EM_PROGRESSO, null, DEPOIS);

        assertThat(tarefa.getFinalizadaEm()).isNull();
    }

    private static Tarefa nova() {
        return new Tarefa(UUID.randomUUID(), UUID.randomUUID(), "Tarefa", "", Prioridade.MEDIA,
                null, StatusTarefa.PENDENTE, 60, CRIADA, CRIADA);
    }
}
