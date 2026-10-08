package com.controlerotina.capacidade.controller;

import java.time.LocalDate;
import java.util.ArrayList;
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
import org.springframework.web.server.ResponseStatusException;

import com.controlerotina.capacidade.dto.AtualizarCapacidadeOverrideRequest;
import com.controlerotina.capacidade.dto.CapacidadeOverrideResponse;
import com.controlerotina.capacidade.dto.CriarCapacidadeOverrideItemRequest;
import com.controlerotina.capacidade.service.CapacidadeOverrideService;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/capacidades/overrides")
public class CapacidadeOverrideController {

    private final CapacidadeOverrideService capacidadeOverrideService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public CapacidadeOverrideController(
            CapacidadeOverrideService capacidadeOverrideService,
            ObjectMapper objectMapper,
            Validator validator
    ) {
        this.capacidadeOverrideService = capacidadeOverrideService;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public List<CapacidadeOverrideResponse> criar(
            @AuthenticationPrincipal UUID usuarioId,
            @RequestBody JsonNode body
    ) {
        return capacidadeOverrideService.criar(usuarioId, parseItens(body));
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

    private List<CriarCapacidadeOverrideItemRequest> parseItens(JsonNode body) {
        if (body == null || body.isNull()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Body obrigatório.");
        }

        List<CriarCapacidadeOverrideItemRequest> itens = new ArrayList<>();
        if (body.isArray()) {
            for (JsonNode node : body) {
                itens.add(converterEValidar(node));
            }
        } else if (body.isObject()) {
            itens.add(converterEValidar(body));
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Body inválido.");
        }
        return itens;
    }

    private CriarCapacidadeOverrideItemRequest converterEValidar(JsonNode node) {
        CriarCapacidadeOverrideItemRequest item;
        try {
            item = objectMapper.convertValue(node, CriarCapacidadeOverrideItemRequest.class);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item de capacidade inválido.");
        }

        var violations = validator.validate(item);
        if (!violations.isEmpty()) {
            ConstraintViolation<CriarCapacidadeOverrideItemRequest> first = violations.iterator().next();
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, first.getMessage());
        }
        return item;
    }
}
