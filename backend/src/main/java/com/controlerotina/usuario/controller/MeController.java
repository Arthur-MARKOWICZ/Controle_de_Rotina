package com.controlerotina.usuario.controller;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.controlerotina.usuario.dto.AtualizarMeRequest;
import com.controlerotina.usuario.dto.UsuarioResponse;
import com.controlerotina.usuario.service.UsuarioService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/me")
public class MeController {

    private final UsuarioService usuarioService;

    public MeController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public UsuarioResponse me(@AuthenticationPrincipal UUID usuarioId) {
        return usuarioService.buscarMe(usuarioId);
    }

    @PutMapping
    public UsuarioResponse atualizar(
            @AuthenticationPrincipal UUID usuarioId,
            @Valid @RequestBody AtualizarMeRequest request
    ) {
        return usuarioService.atualizarMe(usuarioId, request);
    }
}
