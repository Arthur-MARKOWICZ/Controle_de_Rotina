package com.controlerotina.xp;

import com.controlerotina.tarefa.entity.Prioridade;

public final class XpMotor {

    private XpMotor() {
    }

    public static double peso(Prioridade prioridade) {
        return switch (prioridade) {
            case BAIXA -> 1.0;
            case MEDIA -> 1.5;
            case ALTA -> 2.0;
        };
    }

    public static int xpBloco(int duracaoMinutos, Prioridade prioridade) {
        return (int) Math.round(duracaoMinutos * peso(prioridade));
    }

    public static int bonusTarefa(int duracaoHoras, Prioridade prioridade) {
        return (int) Math.round(duracaoHoras * 60 * peso(prioridade) * 0.1);
    }

    public static int xpParaSubir(int nivel) {
        return 10 * nivel;
    }

    public static ProgressoXp progresso(int xpTotal) {
        int nivel = 1;
        int restante = xpTotal;
        while (restante >= xpParaSubir(nivel)) {
            restante -= xpParaSubir(nivel);
            nivel++;
        }
        return new ProgressoXp(nivel, restante, xpParaSubir(nivel));
    }

    public record ProgressoXp(int nivel, int xpNoNivel, int xpParaProximoNivel) {
    }
}
