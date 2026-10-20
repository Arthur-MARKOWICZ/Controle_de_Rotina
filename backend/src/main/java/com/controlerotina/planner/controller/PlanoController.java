package com.controlerotina.planner.controller;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.controlerotina.planner.dto.PlanoResponse;
import com.controlerotina.planner.service.PlanoConsultaService;

@RestController
@RequestMapping("/planos")
public class PlanoController {

    private final PlanoConsultaService planoConsultaService;

    public PlanoController(PlanoConsultaService planoConsultaService) {
        this.planoConsultaService = planoConsultaService;
    }

    @GetMapping
    public ResponseEntity<PlanoResponse> buscar(
            @AuthenticationPrincipal UUID usuarioId,
            @RequestParam LocalDate data,
            @RequestParam(required = false) Integer versao
    ) {
        return planoConsultaService.buscarPorData(usuarioId, data, versao)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlanoResponse> buscarPorId(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID id
    ) {
        return planoConsultaService.buscarPorId(usuarioId, id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
