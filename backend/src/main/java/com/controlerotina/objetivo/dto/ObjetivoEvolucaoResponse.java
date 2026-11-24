package com.controlerotina.objetivo.dto;

import java.util.List;

/**
 * Minutos concluídos acumulados por dia, na mesma base do progresso.
 * {@code antesDoHistorico} reúne o que não tem data (tarefas finalizadas antes do registro de finalização).
 */
public record ObjetivoEvolucaoResponse(
        int antesDoHistorico,
        List<PontoEvolucaoResponse> pontos
) {
}
