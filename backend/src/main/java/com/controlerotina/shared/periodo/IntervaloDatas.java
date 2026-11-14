package com.controlerotina.shared.periodo;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.controlerotina.shared.exception.IntervaloCapacidadeInvalidoException;

/** Validação comum de intervalos {@code dataDe..dataAte} (inclusivos) em consultas por período. */
public final class IntervaloDatas {

    private IntervaloDatas() {
    }

    /**
     * @throws IntervaloCapacidadeInvalidoException (400 "Intervalo inválido") se alguma data faltar,
     *         se {@code dataAte} for anterior a {@code dataDe} ou se o intervalo passar de {@code maximoDias}.
     */
    public static void validar(LocalDate dataDe, LocalDate dataAte, int maximoDias) {
        if (dataDe == null || dataAte == null) {
            throw new IntervaloCapacidadeInvalidoException("dataDe e dataAte são obrigatórios.");
        }
        if (dataAte.isBefore(dataDe)) {
            throw new IntervaloCapacidadeInvalidoException("dataAte deve ser maior ou igual a dataDe.");
        }
        if (ChronoUnit.DAYS.between(dataDe, dataAte) + 1 > maximoDias) {
            throw new IntervaloCapacidadeInvalidoException(
                    "Intervalo máximo permitido é de " + maximoDias + " dias."
            );
        }
    }
}
