package com.controlerotina.tag.controller;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.controlerotina.tag.dto.AtualizarTagRequest;
import com.controlerotina.tag.dto.CriarTagRequest;
import com.controlerotina.tag.dto.TagResponse;
import com.controlerotina.tag.service.TagService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/tags")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping
    public List<TagResponse> listar(@AuthenticationPrincipal UUID usuarioId) {
        return tagService.listar(usuarioId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TagResponse criar(
            @AuthenticationPrincipal UUID usuarioId,
            @Valid @RequestBody CriarTagRequest request
    ) {
        return tagService.criar(usuarioId, request);
    }

    @PatchMapping("/{id}")
    public TagResponse atualizar(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID id,
            @Valid @RequestBody AtualizarTagRequest request
    ) {
        return tagService.atualizar(usuarioId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID id
    ) {
        tagService.excluir(usuarioId, id);
    }
}
