package com.controlerotina.objetivo.dto;

import java.time.LocalDate;

/** Um dia com minutos concluídos e o acumulado até ele (inclusive). Datas civis em BRT. */
public record PontoEvolucaoResponse(
        LocalDate data,
        int minutosNoDia,
        int minutosAcumulados
) {
}
