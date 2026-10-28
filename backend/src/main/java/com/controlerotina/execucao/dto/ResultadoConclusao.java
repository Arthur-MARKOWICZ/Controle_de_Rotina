package com.controlerotina.execucao.dto;

import com.controlerotina.execucao.entity.Execucao;
import com.controlerotina.xp.service.XpConcessaoService.ResultadoConcessao;

public record ResultadoConclusao(Execucao execucao, ResultadoConcessao xp) {
}
