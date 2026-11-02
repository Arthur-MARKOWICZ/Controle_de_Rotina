package com.controlerotina.planner.motor;

import java.util.UUID;

public record BlocoAlocado(
        UUID tarefaId,
        UUID habitoId,
        int duracaoMinutos,
        int ordem,
        boolean truncado
) {
    public BlocoAlocado {
        boolean temTarefa = tarefaId != null;
        boolean temHabito = habitoId != null;
        if (temTarefa == temHabito) {
            throw new IllegalArgumentException("Bloco deve ter exatamente uma origem: tarefa ou hábito");
        }
    }

    /** Bloco de tarefa (API usada pelos callers existentes). */
    public BlocoAlocado(UUID tarefaId, int duracaoMinutos, int ordem, boolean truncado) {
        this(tarefaId, null, duracaoMinutos, ordem, truncado);
    }

    public static BlocoAlocado deHabito(UUID habitoId, int duracaoMinutos, int ordem) {
        return new BlocoAlocado(null, habitoId, duracaoMinutos, ordem, false);
    }

    public boolean isHabito() {
        return habitoId != null;
    }
}
