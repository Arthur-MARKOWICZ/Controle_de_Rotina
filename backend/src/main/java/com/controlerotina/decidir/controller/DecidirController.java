package com.controlerotina.decidir.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

import com.controlerotina.decidir.dto.AtualizarDecidirRequest;
import com.controlerotina.decidir.dto.CriarDecidirRequest;
import com.controlerotina.decidir.dto.DecidirResponse;
import com.controlerotina.decidir.dto.PromoverDecidirRequest;
import com.controlerotina.decidir.service.DecidirService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/decidir")
public class DecidirController {

    private static final int SIZE_MAX = 100;
    private static final int SIZE_DEFAULT = 20;

    private final DecidirService decidirService;

    public DecidirController(DecidirService decidirService) {
        this.decidirService = decidirService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DecidirResponse criar(
            @AuthenticationPrincipal UUID usuarioId,
            @Valid @RequestBody CriarDecidirRequest request
    ) {
        return decidirService.criar(usuarioId, request);
    }

    @GetMapping
    public Page<DecidirResponse> listar(
            @AuthenticationPrincipal UUID usuarioId,
            @RequestParam(defaultValue = "false") boolean promovidos,
            @RequestParam(required = false) List<UUID> tagId,
            Pageable pageable
    ) {
        return decidirService.listar(usuarioId, promovidos, tagId, normalizarPageable(pageable));
    }

    @GetMapping("/{id}")
    public DecidirResponse buscar(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID id
    ) {
        return decidirService.buscarPorId(usuarioId, id);
    }

    @PatchMapping("/{id}")
    public DecidirResponse atualizar(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID id,
            @Valid @RequestBody AtualizarDecidirRequest request
    ) {
        return decidirService.atualizar(usuarioId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID id
    ) {
        decidirService.excluir(usuarioId, id);
    }

    @PostMapping("/{id}/promover")
    public DecidirResponse promover(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) PromoverDecidirRequest request
    ) {
        return decidirService.promover(usuarioId, id, request);
    }

    private static Pageable normalizarPageable(Pageable pageable) {
        int size = pageable.getPageSize() <= 0 ? SIZE_DEFAULT : Math.min(pageable.getPageSize(), SIZE_MAX);
        int page = Math.max(pageable.getPageNumber(), 0);
        Sort sort = pageable.getSort().isSorted()
                ? pageable.getSort()
                : Sort.by(
                        Sort.Order.asc("decidirAte").nullsLast(),
                        Sort.Order.desc("createdAt")
                );
        return PageRequest.of(page, size, sort);
    }
}
