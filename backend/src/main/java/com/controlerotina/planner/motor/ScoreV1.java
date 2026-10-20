package com.controlerotina.planner.motor;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;

public final class ScoreV1 {

    static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private ScoreV1() {
    }

    public static int calcular(Tarefa tarefa, LocalDate diaD) {
        return pontosPrioridade(tarefa.getPrioridade())
                + pontosUrgencia(tarefa, diaD)
                + pontosBonusAtrasada(tarefa.getStatus())
                + pontosDuracao(tarefa.getDuracaoHoras() * 60);
    }

    static int pontosPrioridade(Prioridade prioridade) {
        return switch (prioridade) {
            case ALTA -> 100;
            case MEDIA -> 50;
            case BAIXA -> 20;
        };
    }

    static int pontosUrgencia(Tarefa tarefa, LocalDate diaD) {
        ZonedDateTime inicioD = diaD.atStartOfDay(ZONE);
        var prazo = tarefa.getPrazo().atZone(ZONE);

        if (prazo.isBefore(inicioD)) {
            return 70;
        }
        if (prazo.isBefore(inicioD.plusDays(1))) {
            return 60;
        }
        if (prazo.isBefore(inicioD.plusDays(2))) {
            return 40;
        }
        if (prazo.isBefore(inicioD.plusDays(4))) {
            return 25;
        }
        if (prazo.isBefore(inicioD.plusDays(8))) {
            return 10;
        }
        return 0;
    }

    static int pontosBonusAtrasada(StatusTarefa status) {
        return status == StatusTarefa.ATRASADA ? 40 : 0;
    }

    static int pontosDuracao(int duracaoMinutos) {
        if (duracaoMinutos <= 60) {
            return 15;
        }
        if (duracaoMinutos <= 120) {
            return 10;
        }
        if (duracaoMinutos <= 240) {
            return 5;
        }
        return 0;
    }
}
