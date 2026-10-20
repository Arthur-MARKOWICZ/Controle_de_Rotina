package com.controlerotina.planner.motor;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;

class MotorAlocacaoTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate DIA_D = LocalDate.of(2026, 10, 20);

    @Test
    void deveRetornarPlanoVazioQuandoCapacidadeZero() {
        Tarefa tarefa = tarefa("A", Prioridade.ALTA, StatusTarefa.PENDENTE, prazoEmDias(1), 1, Instant.parse("2026-10-01T10:00:00Z"));

        ResultadoAlocacao resultado = MotorAlocacao.alocar(List.of(tarefa), 0, DIA_D);

        assertThat(resultado.blocos()).isEmpty();
        assertThat(resultado.temConflito()).isFalse();
        assertThat(resultado.mensagem()).isEqualTo(MotorAlocacao.MENSAGEM_SEM_TEMPO);
    }

    @Test
    void deveAlocarTarefasQueCabemPorOrdemDeScore() {
        Instant criadoCedo = Instant.parse("2026-10-01T10:00:00Z");
        Instant criadoTarde = Instant.parse("2026-10-02T10:00:00Z");
        Tarefa baixa = tarefa("baixa", Prioridade.BAIXA, StatusTarefa.PENDENTE, prazoEmDias(10), 1, criadoCedo);
        Tarefa alta = tarefa("alta", Prioridade.ALTA, StatusTarefa.PENDENTE, prazoEmDias(10), 1, criadoTarde);

        ResultadoAlocacao resultado = MotorAlocacao.alocar(List.of(baixa, alta), 120, DIA_D);

        assertThat(resultado.blocos()).hasSize(2);
        assertThat(resultado.blocos().get(0).tarefaId()).isEqualTo(alta.getId());
        assertThat(resultado.blocos().get(0).duracaoMinutos()).isEqualTo(60);
        assertThat(resultado.blocos().get(0).ordem()).isEqualTo(1);
        assertThat(resultado.blocos().get(1).tarefaId()).isEqualTo(baixa.getId());
        assertThat(resultado.temConflito()).isFalse();
        assertThat(resultado.mensagem()).isNull();
    }

    @Test
    void deveTruncarQuandoTarefaNaoCabeInteira() {
        Tarefa grande = tarefa("grande", Prioridade.ALTA, StatusTarefa.PENDENTE, prazoEmDias(1), 3, Instant.parse("2026-10-01T10:00:00Z"));

        ResultadoAlocacao resultado = MotorAlocacao.alocar(List.of(grande), 90, DIA_D);

        assertThat(resultado.blocos()).hasSize(1);
        assertThat(resultado.blocos().get(0).duracaoMinutos()).isEqualTo(90);
        assertThat(resultado.blocos().get(0).truncado()).isTrue();
        assertThat(resultado.temConflito()).isTrue();
        assertThat(resultado.mensagem()).isEqualTo(MotorAlocacao.MENSAGEM_PARCIAL);
    }

    @Test
    void devePararAposEsgotarCapacidadeComCorte() {
        Instant criado = Instant.parse("2026-10-01T10:00:00Z");
        Tarefa primeira = tarefa("1", Prioridade.ALTA, StatusTarefa.PENDENTE, prazoEmDias(1), 1, criado);
        Tarefa segunda = tarefa("2", Prioridade.MEDIA, StatusTarefa.PENDENTE, prazoEmDias(1), 2, criado.plusSeconds(1));

        ResultadoAlocacao resultado = MotorAlocacao.alocar(List.of(primeira, segunda), 90, DIA_D);

        assertThat(resultado.blocos()).hasSize(2);
        assertThat(resultado.blocos().get(0).duracaoMinutos()).isEqualTo(60);
        assertThat(resultado.blocos().get(0).truncado()).isFalse();
        assertThat(resultado.blocos().get(1).duracaoMinutos()).isEqualTo(30);
        assertThat(resultado.blocos().get(1).truncado()).isTrue();
        assertThat(resultado.temConflito()).isTrue();
    }

    @Test
    void deveDesempatarPorPrazoMaisCedoDepoisCreatedAt() {
        Instant criadoCedo = Instant.parse("2026-10-01T10:00:00Z");
        Instant criadoTarde = Instant.parse("2026-10-02T10:00:00Z");
        Instant prazoCedo = prazoEmDias(2);
        Instant prazoTarde = prazoEmDias(3);

        Tarefa mesmaScorePrazoTarde = tarefa("tardia", Prioridade.MEDIA, StatusTarefa.PENDENTE, prazoTarde, 1, criadoCedo);
        Tarefa mesmaScorePrazoCedo = tarefa("cedo", Prioridade.MEDIA, StatusTarefa.PENDENTE, prazoCedo, 1, criadoTarde);

        ResultadoAlocacao porPrazo = MotorAlocacao.alocar(
                List.of(mesmaScorePrazoTarde, mesmaScorePrazoCedo), 120, DIA_D);

        assertThat(porPrazo.blocos().get(0).tarefaId()).isEqualTo(mesmaScorePrazoCedo.getId());

        Tarefa empateA = tarefa("a", Prioridade.MEDIA, StatusTarefa.PENDENTE, prazoCedo, 1, criadoCedo);
        Tarefa empateB = tarefa("b", Prioridade.MEDIA, StatusTarefa.PENDENTE, prazoCedo, 1, criadoTarde);

        ResultadoAlocacao porCreated = MotorAlocacao.alocar(List.of(empateB, empateA), 120, DIA_D);

        assertThat(porCreated.blocos().get(0).tarefaId()).isEqualTo(empateA.getId());
    }

    @Test
    void deveConverterHorasNulasOuZeroEmCapacidadeZero() {
        assertThat(MotorAlocacao.capacidadeMinutos(null)).isZero();
        assertThat(MotorAlocacao.capacidadeMinutos(0)).isZero();
        assertThat(MotorAlocacao.capacidadeMinutos(2)).isEqualTo(120);
    }

    private static Instant prazoEmDias(int diasAposD) {
        return DIA_D.plusDays(diasAposD).atTime(12, 0).atZone(ZONE).toInstant();
    }

    private static Tarefa tarefa(
            String titulo,
            Prioridade prioridade,
            StatusTarefa status,
            Instant prazo,
            int duracaoHoras,
            Instant createdAt
    ) {
        return new Tarefa(
                UUID.randomUUID(),
                UUID.randomUUID(),
                titulo,
                "descricao",
                prioridade,
                prazo,
                status,
                duracaoHoras,
                createdAt,
                createdAt
        );
    }
}
