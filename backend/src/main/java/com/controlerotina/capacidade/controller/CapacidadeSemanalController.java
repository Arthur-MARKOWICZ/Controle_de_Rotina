package com.controlerotina.capacidade.controller;

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

import com.controlerotina.capacidade.dto.AtualizarCapacidadeSemanalRequest;
import com.controlerotina.capacidade.dto.CapacidadeSemanalResponse;
import com.controlerotina.capacidade.dto.CriarCapacidadeSemanalRequest;
import com.controlerotina.capacidade.service.CapacidadeSemanalService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/capacidades/semanal")
public class CapacidadeSemanalController {

    private final CapacidadeSemanalService capacidadeSemanalService;

    public CapacidadeSemanalController(CapacidadeSemanalService capacidadeSemanalService) {
        this.capacidadeSemanalService = capacidadeSemanalService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public List<CapacidadeSemanalResponse> criar(
            @AuthenticationPrincipal UUID usuarioId,
            @Valid @RequestBody CriarCapacidadeSemanalRequest request
    ) {
        return capacidadeSemanalService.criar(usuarioId, request.itens());
    }

    @GetMapping
    public List<CapacidadeSemanalResponse> listar(
            @AuthenticationPrincipal UUID usuarioId,
            @RequestParam(defaultValue = "false") boolean incluirDeletadas,
            @RequestParam(defaultValue = "false") boolean somenteDeletadas
    ) {
        return capacidadeSemanalService.listar(usuarioId, incluirDeletadas, somenteDeletadas);
    }

    @GetMapping("/{id}")
    public CapacidadeSemanalResponse buscar(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID id
    ) {
        return capacidadeSemanalService.buscarPorId(usuarioId, id);
    }

    @PatchMapping("/{id}")
    public CapacidadeSemanalResponse atualizar(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID id,
            @Valid @RequestBody AtualizarCapacidadeSemanalRequest request
    ) {
        return capacidadeSemanalService.atualizar(usuarioId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID id
    ) {
        capacidadeSemanalService.excluir(usuarioId, id);
    }

    @PostMapping("/{id}/restore")
    public CapacidadeSemanalResponse restaurar(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID id
    ) {
        return capacidadeSemanalService.restaurar(usuarioId, id);
    }
}
