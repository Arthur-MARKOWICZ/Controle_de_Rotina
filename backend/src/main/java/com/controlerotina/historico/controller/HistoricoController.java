package com.controlerotina.historico.controller;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.controlerotina.historico.dto.HistoricoResumoResponse;
import com.controlerotina.historico.service.HistoricoService;

@RestController
@RequestMapping("/historico")
public class HistoricoController {

    private final HistoricoService historicoService;

    public HistoricoController(HistoricoService historicoService) {
        this.historicoService = historicoService;
    }

    @GetMapping("/dia")
    public HistoricoResumoResponse dia(
            @AuthenticationPrincipal UUID usuarioId,
            @RequestParam(required = false) LocalDate data
    ) {
        return historicoService.resumirDia(usuarioId, data);
    }

    @GetMapping("/semana")
    public HistoricoResumoResponse semana(
            @AuthenticationPrincipal UUID usuarioId,
            @RequestParam(required = false) LocalDate semanaInicio
    ) {
        return historicoService.resumirSemana(usuarioId, semanaInicio);
    }
}
