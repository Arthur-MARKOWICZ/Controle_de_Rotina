package com.controlerotina.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.controlerotina.auth.dto.LoginRequest;
import com.controlerotina.auth.jwt.JwtService;
import com.controlerotina.shared.exception.CredenciaisInvalidasException;
import com.controlerotina.usuario.entity.Usuario;
import com.controlerotina.usuario.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceLoginTest {

    private static final Instant AGORA = Instant.parse("2026-10-29T12:00:00Z");
    private static final UUID USUARIO_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

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
    void deveLogarQuandoEmailESenhaEstaoCorretos() {
        Usuario usuario = usuario();
        when(usuarioRepository.findByEmail("ana@email.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Senha@123", "hash")).thenReturn(true);
        when(jwtService.emitirAccessToken(USUARIO_ID)).thenReturn("access-jwt");
        when(refreshTokenService.emitir(USUARIO_ID)).thenReturn("refresh-opaco");

        AuthService.SessaoTokens sessao = authService.login(new LoginRequest("ana@email.com", "Senha@123"));

        assertThat(sessao.access().accessToken()).isEqualTo("access-jwt");
        assertThat(sessao.refreshToken()).isEqualTo("refresh-opaco");
        verify(refreshTokenService).emitir(USUARIO_ID);
    }

    @Test
    void deveNormalizarEmailParaMinusculasNoLogin() {
        Usuario usuario = usuario();
        when(usuarioRepository.findByEmail("ana@email.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Senha@123", "hash")).thenReturn(true);
        when(jwtService.emitirAccessToken(USUARIO_ID)).thenReturn("access-jwt");
        when(refreshTokenService.emitir(USUARIO_ID)).thenReturn("refresh-opaco");

        authService.login(new LoginRequest("  Ana@Email.com  ", "Senha@123"));

        verify(usuarioRepository).findByEmail("ana@email.com");
    }

    @Test
    void deveCompararSenhaComHashFicticioQuandoEmailNaoExiste() {
        when(usuarioRepository.findByEmail("ausente@email.com")).thenReturn(Optional.empty());
        when(passwordEncoder.matches(eq("Senha@123"), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("ausente@email.com", "Senha@123")))
                .isInstanceOf(CredenciaisInvalidasException.class)
                .satisfies(ex -> {
                    CredenciaisInvalidasException erro = (CredenciaisInvalidasException) ex;
                    assertThat(erro.getStatusCode().value()).isEqualTo(401);
                    assertThat(erro.getBody().getTitle()).isEqualTo("Não autorizado");
                });

        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder).matches(eq("Senha@123"), hashCaptor.capture());
        assertThat(hashCaptor.getValue()).startsWith("$2");
        assertThat(hashCaptor.getValue()).hasSizeGreaterThan(50);
        verify(refreshTokenService, never()).emitir(any());
        verify(jwtService, never()).emitirAccessToken(any());
    }

    @Test
    void deveRejeitarSenhaErradaSemEmitirTokens() {
        when(usuarioRepository.findByEmail("ana@email.com")).thenReturn(Optional.of(usuario()));
        when(passwordEncoder.matches("errada", "hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("ana@email.com", "errada")))
                .isInstanceOf(CredenciaisInvalidasException.class);

        verify(refreshTokenService, never()).emitir(any());
        verify(jwtService, never()).emitirAccessToken(any());
    }

    @Test
    void deveRejeitarRefreshNuloSemRotacionar() {
        when(refreshTokenService.rotacionar(null)).thenThrow(new CredenciaisInvalidasException());

        assertThatThrownBy(() -> authService.refresh(null))
                .isInstanceOf(CredenciaisInvalidasException.class);

        verify(jwtService, never()).emitirAccessToken(any());
    }

    @Test
    void deveRotacionarRefreshEEmitirNovoAccess() {
        when(refreshTokenService.rotacionar("refresh-atual"))
                .thenReturn(new RefreshTokenService.Rotacao(USUARIO_ID, "refresh-novo"));
        when(jwtService.emitirAccessToken(USUARIO_ID)).thenReturn("access-novo");

        AuthService.SessaoTokens sessao = authService.refresh("refresh-atual");

        assertThat(sessao.access().accessToken()).isEqualTo("access-novo");
        assertThat(sessao.refreshToken()).isEqualTo("refresh-novo");
    }

    @Test
    void logoutDuploNaoDeveRevogarAccessDeNovoQuandoBearerAusente() {
        authService.logout("refresh-ja-revogado", "   ");
        authService.logout("refresh-ja-revogado", null);

        verify(refreshTokenService, org.mockito.Mockito.times(2)).revogar("refresh-ja-revogado");
        verify(jwtService, never()).extrairClaims(any());
        verify(accessTokenRevogacaoService, never()).revogar(any(), any());
    }

    @Test
    void deveRevogarAccessNoLogoutQuandoBearerPresente() {
        Instant expira = AGORA.plusSeconds(900);
        when(jwtService.extrairClaims("access-jwt")).thenReturn(Optional.of(
                new JwtService.AccessClaims(USUARIO_ID, "jti-1", expira)
        ));

        authService.logout("refresh-opaco", "access-jwt");

        verify(refreshTokenService).revogar("refresh-opaco");
        verify(accessTokenRevogacaoService).revogar("jti-1", expira);
    }

    private static Usuario usuario() {
        return new Usuario(USUARIO_ID, "ana@email.com", "hash", "Ana", AGORA, AGORA);
    }
}
