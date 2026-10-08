package com.controlerotina.capacidade.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.controlerotina.capacidade.dto.CapacidadeResolvidaResponse;
import com.controlerotina.capacidade.service.CapacidadeResolvidaService;

@RestController
@RequestMapping("/capacidades/resolvida")
public class CapacidadeResolvidaController {

    private final CapacidadeResolvidaService capacidadeResolvidaService;

    public CapacidadeResolvidaController(CapacidadeResolvidaService capacidadeResolvidaService) {
        this.capacidadeResolvidaService = capacidadeResolvidaService;
    }

    @GetMapping
    public List<CapacidadeResolvidaResponse> resolver(
            @AuthenticationPrincipal UUID usuarioId,
            @RequestParam(required = false) LocalDate dataDe,
            @RequestParam(required = false) LocalDate dataAte
    ) {
        return capacidadeResolvidaService.resolver(usuarioId, dataDe, dataAte);
    }
}
