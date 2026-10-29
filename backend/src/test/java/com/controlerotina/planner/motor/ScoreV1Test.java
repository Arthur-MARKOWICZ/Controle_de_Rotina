package com.controlerotina.planner.motor;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;

class ScoreV1Test {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate DIA_D = LocalDate.of(2026, 10, 20);

    @ParameterizedTest
    @CsvSource({
            "ALTA, 100",
            "MEDIA, 50",
            "BAIXA, 20"
    })
    void devePontuarPrioridade(Prioridade prioridade, int pontosEsperados) {
        Tarefa tarefa = tarefa(
                prioridade,
                StatusTarefa.PENDENTE,
                Instant.parse("2026-11-01T15:00:00Z"),
                300
        );

        int score = ScoreV1.calcular(tarefa, DIA_D);

        assertThat(score).isEqualTo(pontosEsperados);
    }

    @ParameterizedTest
    @CsvSource({
            "2026-10-19T23:00:00-03:00, 70",
            "2026-10-20T15:00:00-03:00, 60",
            "2026-10-21T10:00:00-03:00, 40",
            "2026-10-22T10:00:00-03:00, 25",
            "2026-10-23T10:00:00-03:00, 25",
            "2026-10-24T10:00:00-03:00, 10",
            "2026-10-27T10:00:00-03:00, 10",
            "2026-10-28T10:00:00-03:00, 0"
    })
    void devePontuarUrgenciaDoPrazo(String prazoIso, int urgenciaEsperada) {
        Tarefa tarefa = tarefa(
                Prioridade.BAIXA,
                StatusTarefa.PENDENTE,
                OffsetDateTime.parse(prazoIso).toInstant(),
                300
        );

        int score = ScoreV1.calcular(tarefa, DIA_D);

        assertThat(score).isEqualTo(20 + urgenciaEsperada);
    }

    @Test
    void deveSomarBonusQuandoAtrasada() {
        Instant prazoAntesDeD = DIA_D.atStartOfDay(ZONE).minusHours(1).toInstant();
        Tarefa atrasada = tarefa(Prioridade.BAIXA, StatusTarefa.ATRASADA, prazoAntesDeD, 300);
        Tarefa pendente = tarefa(Prioridade.BAIXA, StatusTarefa.PENDENTE, prazoAntesDeD, 300);

        assertThat(ScoreV1.calcular(atrasada, DIA_D) - ScoreV1.calcular(pendente, DIA_D))
                .isEqualTo(40);
    }

    @ParameterizedTest
    @CsvSource({
            "60, 15",
            "120, 10",
            "180, 5",
            "240, 5",
            "300, 0"
    })
    void devePontuarDuracaoEmMinutos(int duracaoMinutos, int pontosDuracao) {
        Instant prazoLonge = Instant.parse("2026-11-30T12:00:00Z");
        Tarefa tarefa = tarefa(Prioridade.BAIXA, StatusTarefa.PENDENTE, prazoLonge, duracaoMinutos);

        int score = ScoreV1.calcular(tarefa, DIA_D);

        assertThat(score).isEqualTo(20 + pontosDuracao);
    }

    @Test
    void deveSomarTodosOsFatores() {
        Instant prazoDuranteD = DIA_D.atTime(14, 0).atZone(ZONE).toInstant();
        Tarefa tarefa = tarefa(Prioridade.ALTA, StatusTarefa.ATRASADA, prazoDuranteD, 60);

        int score = ScoreV1.calcular(tarefa, DIA_D);

        assertThat(score).isEqualTo(100 + 60 + 40 + 15);
    }

    @Test
    void semPrazoTemUrgenciaZero() {
        Tarefa semPrazo = tarefa(Prioridade.ALTA, StatusTarefa.PENDENTE, null, 60);
        Tarefa comPrazoLonge = tarefa(
                Prioridade.ALTA,
                StatusTarefa.PENDENTE,
                Instant.parse("2026-11-30T12:00:00Z"),
                60
        );

        assertThat(ScoreV1.calcular(semPrazo, DIA_D)).isEqualTo(100 + 15);
        assertThat(ScoreV1.calcular(semPrazo, DIA_D)).isEqualTo(ScoreV1.calcular(comPrazoLonge, DIA_D));
    }

    private static Tarefa tarefa(
            Prioridade prioridade,
            StatusTarefa status,
            Instant prazo,
            int duracaoMinutos
    ) {
        Instant agora = Instant.parse("2026-10-01T12:00:00Z");
        return new Tarefa(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "titulo",
                "descricao",
                prioridade,
                prazo,
                status,
                duracaoMinutos,
                agora,
                agora
        );
    }
}
