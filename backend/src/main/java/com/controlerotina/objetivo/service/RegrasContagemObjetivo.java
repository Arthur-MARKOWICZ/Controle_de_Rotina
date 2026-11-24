package com.controlerotina.objetivo.service;

import java.time.Instant;
import java.time.LocalDate;

import com.controlerotina.capacidade.entity.DiaSemana;
import com.controlerotina.habito.entity.Habito;
import com.controlerotina.habito.entity.StatusHabito;
import com.controlerotina.historico.PeriodoBRT;
import com.controlerotina.objetivo.entity.Objetivo;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;

/**
 * Regras de contagem de um objetivo, compartilhadas pelo progresso e pela evolução.
 * Manter num só lugar é o que garante que a curva da evolução termine no mesmo valor do progresso.
 */
final class RegrasContagemObjetivo {

    private RegrasContagemObjetivo() {
    }

    /** Janela (datas civis em BRT, inclusive) em que um hábito vinculado conta. */
    record Janela(LocalDate inicio, LocalDate fim) {
        boolean vazia() {
            return inicio.isAfter(fim);
        }

        boolean contem(LocalDate dia) {
            return !dia.isBefore(inicio) && !dia.isAfter(fim);
        }
    }

    static boolean contaNoProgresso(Tarefa tarefa) {
        return tarefa.getDeletedAt() == null && tarefa.getStatus() != StatusTarefa.CANCELADA;
    }

    static boolean contaNoProgresso(Habito habito) {
        return habito.getDeletedAt() == null && habito.getStatus() == StatusHabito.ATIVO;
    }

    static Janela janela(Objetivo objetivo, Instant vinculoCreatedAt, LocalDate hoje) {
        LocalDate inicio = vinculoCreatedAt.atZone(PeriodoBRT.ZONE).toLocalDate();
        LocalDate fim = hoje;
        if (objetivo.getPrazo() != null && objetivo.getPrazo().isBefore(hoje)) {
            fim = objetivo.getPrazo();
        }
        LocalDate freeze = null;
        if (objetivo.getConcluidoEm() != null) {
            freeze = objetivo.getConcluidoEm().atZone(PeriodoBRT.ZONE).toLocalDate();
        }
        if (objetivo.getArquivadoEm() != null) {
            LocalDate arquivado = objetivo.getArquivadoEm().atZone(PeriodoBRT.ZONE).toLocalDate();
            freeze = freeze == null || arquivado.isBefore(freeze) ? arquivado : freeze;
        }
        if (freeze != null && freeze.isBefore(fim)) {
            fim = freeze;
        }
        return new Janela(inicio, fim);
    }

    static int diasEsperados(Habito habito, Janela janela) {
        if (janela.vazia()) {
            return 0;
        }
        int dias = 0;
        for (LocalDate dia = janela.inicio(); !dia.isAfter(janela.fim()); dia = dia.plusDays(1)) {
            if (habito.getDiasSemana().contains(DiaSemana.de(dia))) {
                dias++;
            }
        }
        return dias;
    }
}
