package com.controlerotina.objetivo.controller;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.controlerotina.objetivo.dto.AtualizarObjetivoRequest;
import com.controlerotina.objetivo.dto.CriarObjetivoRequest;
import com.controlerotina.objetivo.dto.ObjetivoItensResponse;
import com.controlerotina.objetivo.dto.ObjetivoResponse;
import com.controlerotina.objetivo.entity.StatusObjetivo;
import com.controlerotina.objetivo.service.ObjetivoService;
import com.controlerotina.objetivo.service.ObjetivoVinculoService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/objetivos")
public class ObjetivoController {

    private static final int SIZE_MAX = 100;
    private static final int SIZE_DEFAULT = 20;

    private final ObjetivoService objetivoService;
    private final ObjetivoVinculoService objetivoVinculoService;

    public ObjetivoController(ObjetivoService objetivoService, ObjetivoVinculoService objetivoVinculoService) {
        this.objetivoService = objetivoService;
        this.objetivoVinculoService = objetivoVinculoService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ObjetivoResponse criar(
            @AuthenticationPrincipal UUID usuarioId,
            @Valid @RequestBody CriarObjetivoRequest request
    ) {
        return objetivoService.criar(usuarioId, request);
    }

    @GetMapping
    public PagedModel<ObjetivoResponse> listar(
            @AuthenticationPrincipal UUID usuarioId,
            @RequestParam(required = false) StatusObjetivo status,
            Pageable pageable
    ) {
        return new PagedModel<>(objetivoService.listar(usuarioId, status, normalizarPageable(pageable)));
    }

    @GetMapping("/{id}")
    public ObjetivoResponse buscar(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID id
    ) {
        return objetivoService.buscarPorId(usuarioId, id);
    }

    @GetMapping("/{id}/itens")
    public ObjetivoItensResponse listarItens(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID id
    ) {
        return objetivoVinculoService.listarItens(usuarioId, id);
    }

    @PatchMapping("/{id}")
    public ObjetivoResponse atualizar(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID id,
            @Valid @RequestBody AtualizarObjetivoRequest request
    ) {
        return objetivoService.atualizar(usuarioId, id, request);
    }

    @PostMapping("/{id}/concluir")
    public ObjetivoResponse concluir(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID id
    ) {
        return objetivoService.concluir(usuarioId, id);
    }

    @PostMapping("/{id}/arquivar")
    public ObjetivoResponse arquivar(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID id
    ) {
        return objetivoService.arquivar(usuarioId, id);
    }

    @PostMapping("/{id}/reabrir")
    public ObjetivoResponse reabrir(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID id
    ) {
        return objetivoService.reabrir(usuarioId, id);
    }

    private static Pageable normalizarPageable(Pageable pageable) {
        int size = pageable.getPageSize() <= 0 ? SIZE_DEFAULT : Math.min(pageable.getPageSize(), SIZE_MAX);
        int page = Math.max(pageable.getPageNumber(), 0);
        return PageRequest.of(page, size, pageable.getSort());
    }
}
