package com.controlerotina.planner.motor;

import java.util.List;

public record ResultadoAlocacao(
        List<BlocoAlocado> blocos,
        boolean temConflito,
        String mensagem
) {
}
