package com.controlerotina.planner.motor;

import java.util.UUID;

public record ItemNaoAlocado(
        TipoOrigemAlocacao tipo,
        UUID id,
        String titulo,
        int duracaoMinutos,
        MotivoNaoAlocado motivo
) {
}
