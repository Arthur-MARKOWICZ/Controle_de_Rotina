package com.controlerotina.execucao.repository;

import java.time.Instant;
import java.time.LocalDate;

/** Uma execução {@code CONCLUIDO} de hábito, com a data do plano e a duração do bloco naquele dia. */
public record CumprimentoHabitoLinha(
        LocalDate data,
        int duracaoBlocoMinutos,
        Instant iniciadoEm,
        Instant concluidoEm,
        Integer minutosInformados
) {
}
