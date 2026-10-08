package com.controlerotina.capacidade.controller;

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

import com.controlerotina.capacidade.dto.AtualizarCapacidadeSemanalRequest;
import com.controlerotina.capacidade.dto.CapacidadeSemanalResponse;
import com.controlerotina.capacidade.dto.CriarCapacidadeSemanalItemRequest;
import com.controlerotina.capacidade.service.CapacidadeSemanalService;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/capacidades/semanal")
public class CapacidadeSemanalController {

    private final CapacidadeSemanalService capacidadeSemanalService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public CapacidadeSemanalController(
            CapacidadeSemanalService capacidadeSemanalService,
            ObjectMapper objectMapper,
            Validator validator
    ) {
        this.capacidadeSemanalService = capacidadeSemanalService;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public List<CapacidadeSemanalResponse> criar(
            @AuthenticationPrincipal UUID usuarioId,
            @RequestBody JsonNode body
    ) {
        return capacidadeSemanalService.criar(usuarioId, parseItens(body));
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

    private List<CriarCapacidadeSemanalItemRequest> parseItens(JsonNode body) {
        if (body == null || body.isNull()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Body obrigatório.");
        }

        List<CriarCapacidadeSemanalItemRequest> itens = new ArrayList<>();
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

    private CriarCapacidadeSemanalItemRequest converterEValidar(JsonNode node) {
        CriarCapacidadeSemanalItemRequest item;
        try {
            item = objectMapper.convertValue(node, CriarCapacidadeSemanalItemRequest.class);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item de capacidade inválido.");
        }

        var violations = validator.validate(item);
        if (!violations.isEmpty()) {
            ConstraintViolation<CriarCapacidadeSemanalItemRequest> first = violations.iterator().next();
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, first.getMessage());
        }
        return item;
    }
}
