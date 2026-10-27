package com.controlerotina.execucao.dto;

import com.controlerotina.execucao.entity.Execucao;

public record ResultadoInicio(
        Execucao execucao,
        boolean criada
) {
}
