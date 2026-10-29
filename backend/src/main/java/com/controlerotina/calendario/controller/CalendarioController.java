package com.controlerotina.calendario.controller;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.controlerotina.calendario.dto.CalendarioResponse;
import com.controlerotina.calendario.service.CalendarioService;

@RestController
@RequestMapping("/calendario")
public class CalendarioController {

    private final CalendarioService calendarioService;

    public CalendarioController(CalendarioService calendarioService) {
        this.calendarioService = calendarioService;
    }

    @GetMapping
    public CalendarioResponse consultar(
            @AuthenticationPrincipal UUID usuarioId,
            @RequestParam LocalDate dataDe,
            @RequestParam LocalDate dataAte
    ) {
        return calendarioService.consultar(usuarioId, dataDe, dataAte);
    }
}
