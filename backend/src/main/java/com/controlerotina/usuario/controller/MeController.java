package com.controlerotina.usuario.controller;

import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.controlerotina.auth.cookie.RefreshCookieFactory;
import com.controlerotina.auth.service.AuthService;
import com.controlerotina.usuario.dto.AtualizarMeRequest;
import com.controlerotina.usuario.dto.ExcluirContaRequest;
import com.controlerotina.usuario.dto.ExportResponse;
import com.controlerotina.usuario.dto.UsuarioResponse;
import com.controlerotina.usuario.service.ExportService;
import com.controlerotina.usuario.service.UsuarioService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/me")
public class MeController {

    private final UsuarioService usuarioService;
    private final ExportService exportService;
    private final AuthService authService;
    private final RefreshCookieFactory refreshCookieFactory;

    public MeController(
            UsuarioService usuarioService,
            ExportService exportService,
            AuthService authService,
            RefreshCookieFactory refreshCookieFactory
    ) {
        this.usuarioService = usuarioService;
        this.exportService = exportService;
        this.authService = authService;
        this.refreshCookieFactory = refreshCookieFactory;
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

    /** Export LGPD: todos os dados do titular, como arquivo JSON para baixar. */
    @GetMapping("/export")
    public ResponseEntity<ExportResponse> exportar(@AuthenticationPrincipal UUID usuarioId) {
        ExportResponse export = exportService.exportar(usuarioId);
        String arquivo = "controle-rotina-export-" + exportService.dataDoArquivo() + ".json";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(arquivo).build().toString())
                .body(export);
    }

    /**
     * Exclui a conta (LGPD). A exclusão já foi confirmada no commit quando o token é revogado,
     * então uma falha no meio nunca deixa o usuário deslogado com a conta ainda existindo.
     */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> excluirConta(
            @AuthenticationPrincipal UUID usuarioId,
            @Valid @RequestBody ExcluirContaRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization
    ) {
        usuarioService.excluirConta(usuarioId, request);
        authService.revogarAccessToken(authorization.substring("Bearer ".length()).trim());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.limpar().toString())
                .build();
    }
}
