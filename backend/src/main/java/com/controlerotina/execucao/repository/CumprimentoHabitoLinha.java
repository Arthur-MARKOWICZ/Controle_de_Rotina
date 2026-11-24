package com.controlerotina.execucao.repository;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Uma execução {@code CONCLUIDO} de hábito, com a data do plano e a duração do bloco naquele dia. */
public record CumprimentoHabitoLinha(
        UUID habitoId,
        LocalDate data,
        int duracaoBlocoMinutos,
        Instant iniciadoEm,
        Instant concluidoEm,
        Integer minutosInformados
) {
    /** Minutos de uma execução: informados se houver, senão o intervalo bruto do timer (sem a regra de 80%). */
    public int minutosDedicados() {
        if (minutosInformados != null) {
            return minutosInformados;
        }
        if (iniciadoEm == null || concluidoEm == null) {
            return 0;
        }
        long millis = Duration.between(iniciadoEm, concluidoEm).toMillis();
        return millis <= 0 ? 0 : (int) Math.round(millis / 60_000.0);
    }
}
