package com.controlerotina.planner.controller;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.controlerotina.planner.dto.AdicionarBlocoRequest;
import com.controlerotina.planner.dto.PlanoResponse;
import com.controlerotina.planner.entity.Plano;
import com.controlerotina.planner.service.PlannerGeracaoService;
import com.controlerotina.planner.service.PlanoAjusteService;
import com.controlerotina.planner.service.PlanoConsultaService;
import com.controlerotina.shared.exception.PlanoNaoEncontradoException;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/planos")
public class PlanoController {

    private final PlanoConsultaService planoConsultaService;
    private final PlannerGeracaoService plannerGeracaoService;
    private final PlanoAjusteService planoAjusteService;

    public PlanoController(
            PlanoConsultaService planoConsultaService,
            PlannerGeracaoService plannerGeracaoService,
            PlanoAjusteService planoAjusteService
    ) {
        this.planoConsultaService = planoConsultaService;
        this.plannerGeracaoService = plannerGeracaoService;
        this.planoAjusteService = planoAjusteService;
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

    @PostMapping("/gerar")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanoResponse gerar(
            @AuthenticationPrincipal UUID usuarioId,
            @RequestParam LocalDate data
    ) {
        Plano plano = plannerGeracaoService.gerar(usuarioId, data);
        return planoConsultaService.buscarPorId(usuarioId, plano.getId())
                .orElseThrow(PlanoNaoEncontradoException::new);
    }

    @PostMapping("/{id}/blocos")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanoResponse adicionarBloco(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID id,
            @Valid @RequestBody AdicionarBlocoRequest request
    ) {
        return planoAjusteService.adicionarBloco(usuarioId, id, request.tarefaId());
    }
}
