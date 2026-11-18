package com.controlerotina.objetivo.service;

import java.time.Instant;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.controlerotina.habito.entity.Habito;
import com.controlerotina.objetivo.dto.OrigemVinculoObjetivo;
import com.controlerotina.projeto.entity.Projeto;
import com.controlerotina.tarefa.entity.Tarefa;

public record ObjetivoConjunto(
        List<TarefaRaiz> tarefasRaiz,
        List<HabitoVinculado> habitos,
        List<Projeto> projetos
) {
    public ObjetivoConjunto {
        tarefasRaiz = tarefasRaiz == null ? List.of() : List.copyOf(tarefasRaiz);
        habitos = habitos == null ? List.of() : List.copyOf(habitos);
        projetos = projetos == null ? List.of() : List.copyOf(projetos);
    }

    public static ObjetivoConjunto vazio() {
        return new ObjetivoConjunto(List.of(), List.of(), List.of());
    }

    public record TarefaRaiz(Tarefa tarefa, OrigemVinculoObjetivo origem, @Nullable String projetoNome) {
    }

    public record HabitoVinculado(Habito habito, Instant vinculoCreatedAt) {
    }
}
