package com.controlerotina.projeto.controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

import com.controlerotina.projeto.dto.AtualizarProjetoRequest;
import com.controlerotina.projeto.dto.CriarProjetoRequest;
import com.controlerotina.projeto.dto.ProjetoResponse;
import com.controlerotina.projeto.entity.StatusProjeto;
import com.controlerotina.projeto.service.ProjetoService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/projetos")
public class ProjetoController {

    private static final int SIZE_MAX = 100;
    private static final int SIZE_DEFAULT = 20;

    private final ProjetoService projetoService;

    public ProjetoController(ProjetoService projetoService) {
        this.projetoService = projetoService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjetoResponse criar(
            @AuthenticationPrincipal UUID usuarioId,
            @Valid @RequestBody CriarProjetoRequest request
    ) {
        return projetoService.criar(usuarioId, request);
    }

    @GetMapping
    public Page<ProjetoResponse> listar(
            @AuthenticationPrincipal UUID usuarioId,
            @RequestParam(required = false) StatusProjeto status,
            Pageable pageable
    ) {
        return projetoService.listar(usuarioId, status, normalizarPageable(pageable));
    }

    @GetMapping("/{id}")
    public ProjetoResponse buscar(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID id
    ) {
        return projetoService.buscarPorId(usuarioId, id);
    }

    @PatchMapping("/{id}")
    public ProjetoResponse atualizar(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID id,
            @Valid @RequestBody AtualizarProjetoRequest request
    ) {
        return projetoService.atualizar(usuarioId, id, request);
    }

    @PostMapping("/{id}/arquivar")
    public ProjetoResponse arquivar(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID id
    ) {
        return projetoService.arquivar(usuarioId, id);
    }

    @PostMapping("/{id}/reativar")
    public ProjetoResponse reativar(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID id
    ) {
        return projetoService.reativar(usuarioId, id);
    }

    private static Pageable normalizarPageable(Pageable pageable) {
        int size = pageable.getPageSize() <= 0 ? SIZE_DEFAULT : Math.min(pageable.getPageSize(), SIZE_MAX);
        int page = Math.max(pageable.getPageNumber(), 0);
        Sort sort = pageable.getSort().isSorted()
                ? pageable.getSort()
                : Sort.by(Sort.Order.asc("nome"));
        return PageRequest.of(page, size, sort);
    }
}
