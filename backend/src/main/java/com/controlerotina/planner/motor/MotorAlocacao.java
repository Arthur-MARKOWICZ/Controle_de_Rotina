package com.controlerotina.planner.motor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.controlerotina.tarefa.entity.Tarefa;

public final class MotorAlocacao {

    public static final String MENSAGEM_SEM_TEMPO = "Sem tempo neste dia. Nada planejado.";
    public static final String MENSAGEM_PARCIAL =
            "Capacidade insuficiente: ao menos uma tarefa foi alocada parcialmente.";

    private MotorAlocacao() {
    }

    public static ResultadoAlocacao alocar(List<Tarefa> elegiveis, int capacidadeMinutos, LocalDate diaD) {
        if (capacidadeMinutos <= 0) {
            return new ResultadoAlocacao(List.of(), false, MENSAGEM_SEM_TEMPO);
        }

        List<Tarefa> ordenadas = elegiveis.stream()
                .sorted(comparadorPorScore(diaD))
                .toList();

        List<BlocoAlocado> blocos = new ArrayList<>();
        int restante = capacidadeMinutos;
        boolean temConflito = false;
        int ordem = 1;

        for (Tarefa tarefa : ordenadas) {
            if (restante <= 0) {
                break;
            }

            int duracaoTotal = tarefa.getDuracaoHoras() * 60;
            if (duracaoTotal <= restante) {
                blocos.add(new BlocoAlocado(tarefa.getId(), duracaoTotal, ordem++, false));
                restante -= duracaoTotal;
                continue;
            }

            blocos.add(new BlocoAlocado(tarefa.getId(), restante, ordem++, true));
            temConflito = true;
            restante = 0;
        }

        String mensagem = temConflito ? MENSAGEM_PARCIAL : null;
        return new ResultadoAlocacao(List.copyOf(blocos), temConflito, mensagem);
    }

    static Comparator<Tarefa> comparadorPorScore(LocalDate diaD) {
        return Comparator
                .comparingInt((Tarefa t) -> ScoreV1.calcular(t, diaD)).reversed()
                .thenComparing(Tarefa::getPrazo)
                .thenComparing(Tarefa::getCreatedAt);
    }

    public static int capacidadeMinutos(Integer horas) {
        if (horas == null || horas <= 0) {
            return 0;
        }
        return horas * 60;
    }
}
