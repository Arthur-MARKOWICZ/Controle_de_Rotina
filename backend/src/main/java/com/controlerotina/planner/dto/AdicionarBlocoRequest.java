package com.controlerotina.planner.dto;

import java.util.UUID;

public record AdicionarBlocoRequest(
        UUID tarefaId,
        UUID habitoId
) {
    public boolean origemValida() {
        return (tarefaId != null) ^ (habitoId != null);
    }
}
