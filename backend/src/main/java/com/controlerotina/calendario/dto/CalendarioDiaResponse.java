package com.controlerotina.calendario.dto;

import java.time.LocalDate;
import java.util.List;

import com.controlerotina.calendario.CalendarioAgregador;
import com.controlerotina.capacidade.entity.OrigemCapacidade;
import com.controlerotina.planner.entity.StatusPlano;

public record CalendarioDiaResponse(
        LocalDate data,
        int capacidadeMinutos,
        OrigemCapacidade origemCapacidade,
        int minutosAlocados,
        boolean sobrecarga,
        StatusPlano statusPlano,
        CalendarioPlanoResponse plano,
        List<CalendarioTarefaPrazoResponse> tarefasComPrazo
) {
    public static CalendarioDiaResponse from(CalendarioAgregador.DiaMontado dia) {
        return new CalendarioDiaResponse(
                dia.data(),
                dia.capacidadeMinutos(),
                dia.origemCapacidade(),
                dia.minutosAlocados(),
                dia.sobrecarga(),
                dia.statusPlano(),
                dia.plano() == null ? null : CalendarioPlanoResponse.from(dia.plano()),
                dia.tarefasComPrazo().stream().map(CalendarioTarefaPrazoResponse::from).toList()
        );
    }
}
