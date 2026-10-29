package com.controlerotina.capacidade.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.controlerotina.capacidade.dto.AtualizarCapacidadeOverrideRequest;
import com.controlerotina.capacidade.dto.CapacidadeOverrideResponse;
import com.controlerotina.capacidade.dto.CriarCapacidadeOverrideRequest;
import com.controlerotina.capacidade.service.CapacidadeOverrideService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/capacidades/overrides")
public class CapacidadeOverrideController {

    private final CapacidadeOverrideService capacidadeOverrideService;

    public CapacidadeOverrideController(CapacidadeOverrideService capacidadeOverrideService) {
        this.capacidadeOverrideService = capacidadeOverrideService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public List<CapacidadeOverrideResponse> criar(
            @AuthenticationPrincipal UUID usuarioId,
            @Valid @RequestBody CriarCapacidadeOverrideRequest request
    ) {
        return capacidadeOverrideService.criar(usuarioId, request.itens());
    }

    @GetMapping
    public List<CapacidadeOverrideResponse> listar(
            @AuthenticationPrincipal UUID usuarioId,
            @RequestParam(defaultValue = "false") boolean incluirDeletadas,
            @RequestParam(defaultValue = "false") boolean somenteDeletadas,
            @RequestParam(required = false) LocalDate dataDe,
            @RequestParam(required = false) LocalDate dataAte
    ) {
        return capacidadeOverrideService.listar(
                usuarioId, incluirDeletadas, somenteDeletadas, dataDe, dataAte);
    }

    @GetMapping("/{id}")
    public CapacidadeOverrideResponse buscar(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID id
    ) {
        return capacidadeOverrideService.buscarPorId(usuarioId, id);
    }

    @PatchMapping("/{id}")
    public CapacidadeOverrideResponse atualizar(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID id,
            @Valid @RequestBody AtualizarCapacidadeOverrideRequest request
    ) {
        return capacidadeOverrideService.atualizar(usuarioId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID id
    ) {
        capacidadeOverrideService.excluir(usuarioId, id);
    }

    @PostMapping("/{id}/restore")
    public CapacidadeOverrideResponse restaurar(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID id
    ) {
        return capacidadeOverrideService.restaurar(usuarioId, id);
    }
}
