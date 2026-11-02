package com.controlerotina.planner.motor;

import java.util.List;

public record ResultadoAlocacao(
        List<BlocoAlocado> blocos,
        boolean temConflito,
        String mensagem,
        List<ItemNaoAlocado> itensNaoAlocados
) {
    public ResultadoAlocacao(List<BlocoAlocado> blocos, boolean temConflito, String mensagem) {
        this(blocos, temConflito, mensagem, List.of());
    }

    public ResultadoAlocacao {
        blocos = blocos == null ? List.of() : List.copyOf(blocos);
        itensNaoAlocados = itensNaoAlocados == null ? List.of() : List.copyOf(itensNaoAlocados);
    }
}
