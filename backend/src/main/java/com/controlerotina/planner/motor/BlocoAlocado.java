package com.controlerotina.planner.motor;

import java.util.UUID;

public record BlocoAlocado(
        UUID tarefaId,
        int duracaoMinutos,
        int ordem,
        boolean truncado
) {
}
