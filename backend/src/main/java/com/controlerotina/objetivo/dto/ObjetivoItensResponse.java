package com.controlerotina.objetivo.dto;

import java.util.List;

public record ObjetivoItensResponse(
        List<ObjetivoItemTarefaResponse> tarefas,
        List<ObjetivoItemHabitoResponse> habitos,
        List<ObjetivoItemProjetoResponse> projetos
) {
    public ObjetivoItensResponse {
        tarefas = tarefas == null ? List.of() : List.copyOf(tarefas);
        habitos = habitos == null ? List.of() : List.copyOf(habitos);
        projetos = projetos == null ? List.of() : List.copyOf(projetos);
    }
}
