package com.controlerotina.projeto.controller;

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

import com.controlerotina.capacidade.entity.DiaSemana;
import com.controlerotina.projeto.dto.AtualizarReservaProjetoRequest;
import com.controlerotina.projeto.dto.CriarReservaProjetoRequest;
import com.controlerotina.projeto.dto.ReservaProjetoResponse;
import com.controlerotina.projeto.service.ReservaProjetoService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/projetos")
public class ReservaProjetoController {

    private final ReservaProjetoService reservaProjetoService;

    public ReservaProjetoController(ReservaProjetoService reservaProjetoService) {
        this.reservaProjetoService = reservaProjetoService;
    }

    @PostMapping("/{projetoId}/reservas")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservaProjetoResponse criar(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID projetoId,
            @Valid @RequestBody CriarReservaProjetoRequest request
    ) {
        return reservaProjetoService.criar(usuarioId, projetoId, request);
    }

    @GetMapping("/{projetoId}/reservas")
    public List<ReservaProjetoResponse> listarDoProjeto(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID projetoId
    ) {
        return reservaProjetoService.listarDoProjeto(usuarioId, projetoId);
    }

    @GetMapping("/reservas")
    public List<ReservaProjetoResponse> listarDoUsuario(
            @AuthenticationPrincipal UUID usuarioId,
            @RequestParam(required = false) DiaSemana diaSemana,
            @RequestParam(required = false) UUID projetoId
    ) {
        return reservaProjetoService.listarDoUsuario(usuarioId, diaSemana, projetoId);
    }

    @PatchMapping("/{projetoId}/reservas/{reservaId}")
    public ReservaProjetoResponse atualizar(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID projetoId,
            @PathVariable UUID reservaId,
            @Valid @RequestBody AtualizarReservaProjetoRequest request
    ) {
        return reservaProjetoService.atualizar(usuarioId, projetoId, reservaId, request);
    }

    @DeleteMapping("/{projetoId}/reservas/{reservaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID projetoId,
            @PathVariable UUID reservaId
    ) {
        reservaProjetoService.excluir(usuarioId, projetoId, reservaId);
    }
}
