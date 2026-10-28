package com.controlerotina.execucao.controller;

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
import org.springframework.web.bind.annotation.RestController;

import com.controlerotina.execucao.dto.ExecucaoResponse;
import com.controlerotina.execucao.dto.ExecucoesDoDiaResponse;
import com.controlerotina.execucao.dto.IniciarExecucaoRequest;
import com.controlerotina.execucao.dto.ResultadoConclusao;
import com.controlerotina.execucao.dto.ResultadoInicio;
import com.controlerotina.execucao.service.ExecucaoService;
import com.controlerotina.xp.dto.XpConclusaoResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/execucoes")
public class ExecucaoController {

    private final ExecucaoService execucaoService;

    public ExecucaoController(ExecucaoService execucaoService) {
        this.execucaoService = execucaoService;
    }

    @GetMapping
    public ExecucoesDoDiaResponse listar(
            @AuthenticationPrincipal UUID usuarioId,
            @RequestParam(required = false) LocalDate data
    ) {
        return execucaoService.listar(usuarioId, data);
    }

    @PostMapping("/iniciar")
    public ResponseEntity<ExecucaoResponse> iniciar(
            @AuthenticationPrincipal UUID usuarioId,
            @Valid @RequestBody IniciarExecucaoRequest request
    ) {
        ResultadoInicio resultado = execucaoService.iniciar(usuarioId, request.blocoId());
        HttpStatus status = resultado.criada() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ExecucaoResponse.from(resultado.execucao()));
    }

    @PostMapping("/{id}/concluir")
    public ExecucaoResponse concluir(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID id
    ) {
        ResultadoConclusao resultado = execucaoService.concluir(usuarioId, id);
        return ExecucaoResponse.from(resultado.execucao(), XpConclusaoResponse.from(resultado.xp()));
    }

    @PostMapping("/{id}/abortar")
    public ExecucaoResponse abortar(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID id
    ) {
        return ExecucaoResponse.from(execucaoService.abortar(usuarioId, id));
    }
}
