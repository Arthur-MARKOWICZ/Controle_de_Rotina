package com.controlerotina.xp;

import java.time.Duration;
import java.time.Instant;

import com.controlerotina.tarefa.entity.Prioridade;

public final class XpMotor {

    private static final double FRACAO_CREDITO_CHEIO = 0.8;

    private XpMotor() {
    }

    public static double peso(Prioridade prioridade) {
        return switch (prioridade) {
            case BAIXA -> 1.0;
            case MEDIA -> 1.5;
            case ALTA -> 2.0;
        };
    }

    public static int minutosEfetivos(int duracaoBlocoMinutos, Instant iniciadoEm, Instant concluidoEm) {
        if (duracaoBlocoMinutos <= 0 || iniciadoEm == null || concluidoEm == null) {
            return 0;
        }
        long millis = Duration.between(iniciadoEm, concluidoEm).toMillis();
        if (millis <= 0) {
            return 0;
        }
        int decorridoMin = (int) Math.round(millis / 60_000.0);
        if (decorridoMin >= duracaoBlocoMinutos * FRACAO_CREDITO_CHEIO) {
            return duracaoBlocoMinutos;
        }
        return Math.min(duracaoBlocoMinutos, decorridoMin);
    }

    public static int xpBloco(int minutosEfetivos, Prioridade prioridade) {
        return (int) Math.round(minutosEfetivos * peso(prioridade));
    }

    public static int xpBloco(
            int duracaoBlocoMinutos,
            Instant iniciadoEm,
            Instant concluidoEm,
            Prioridade prioridade
    ) {
        return xpBloco(minutosEfetivos(duracaoBlocoMinutos, iniciadoEm, concluidoEm), prioridade);
    }

    public static int bonusTarefa(int somaMinutosEfetivos, Prioridade prioridade) {
        return (int) Math.round(somaMinutosEfetivos * peso(prioridade) * 0.1);
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
