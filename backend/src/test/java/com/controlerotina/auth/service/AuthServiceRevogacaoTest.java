package com.controlerotina.auth.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.controlerotina.auth.jwt.JwtService;
import com.controlerotina.usuario.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceRevogacaoTest {

    private static final Instant EXPIRA = Instant.parse("2026-11-24T12:15:00Z");

    @Mock
    UsuarioRepository usuarioRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    JwtService jwtService;

    @Mock
    RefreshTokenService refreshTokenService;

    @Mock
    AccessTokenRevogacaoService accessTokenRevogacaoService;

    @InjectMocks
    AuthService authService;

    @Test
    void deveRevogarJtiDoAccessTokenValido() {
        when(jwtService.extrairClaims("jwt")).thenReturn(Optional.of(
                new JwtService.AccessClaims(UUID.randomUUID(), "jti-1", EXPIRA)));

        authService.revogarAccessToken("jwt");

        verify(accessTokenRevogacaoService).revogar("jti-1", EXPIRA);
    }

    @Test
    void naoDeveRevogarNadaQuandoTokenNulo() {
        authService.revogarAccessToken(null);

        verify(jwtService, never()).extrairClaims(any());
        verify(accessTokenRevogacaoService, never()).revogar(any(), any());
    }

    @Test
    void naoDeveRevogarNadaQuandoTokenEmBranco() {
        authService.revogarAccessToken("   ");

        verify(accessTokenRevogacaoService, never()).revogar(any(), any());
    }

    @Test
    void naoDeveRevogarNadaQuandoTokenInvalido() {
        when(jwtService.extrairClaims("lixo")).thenReturn(Optional.empty());

        authService.revogarAccessToken("lixo");

        verify(accessTokenRevogacaoService, never()).revogar(any(), any());
    }
}
