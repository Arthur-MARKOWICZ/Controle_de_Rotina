package com.controlerotina.planner.dto;

import java.util.UUID;

import com.controlerotina.planner.motor.ItemNaoAlocado;
import com.controlerotina.planner.motor.MotivoNaoAlocado;
import com.controlerotina.planner.motor.TipoOrigemAlocacao;

public record ItemNaoAlocadoResponse(
        TipoOrigemAlocacao tipo,
        UUID id,
        String titulo,
        int duracaoMinutos,
        MotivoNaoAlocado motivo
) {
    public static ItemNaoAlocadoResponse from(ItemNaoAlocado item) {
        return new ItemNaoAlocadoResponse(
                item.tipo(),
                item.id(),
                item.titulo(),
                item.duracaoMinutos(),
                item.motivo()
        );
    }
}
