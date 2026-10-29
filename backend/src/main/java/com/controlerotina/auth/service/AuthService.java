package com.controlerotina.auth.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.controlerotina.auth.dto.AccessTokenResponse;
import com.controlerotina.auth.dto.LoginRequest;
import com.controlerotina.auth.jwt.JwtService;
import com.controlerotina.shared.exception.CredenciaisInvalidasException;
import com.controlerotina.usuario.entity.Usuario;
import com.controlerotina.usuario.repository.UsuarioRepository;

@Service
public class AuthService {

    /**
     * BCrypt de um segredo interno fixo. Usado quando o e-mail não existe,
     * para o custo de matches ser semelhante ao de senha errada (anti-enumeração).
     */
    static final String HASH_FICTICIO =
            "$2a$10$pfs9k8MJv0D1udO63XzLHeLw2Hml7uVrHIBSuElLN7dP9oz.GgXwW";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AccessTokenRevogacaoService accessTokenRevogacaoService;

    public AuthService(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            AccessTokenRevogacaoService accessTokenRevogacaoService
    ) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.accessTokenRevogacaoService = accessTokenRevogacaoService;
    }

    @Transactional
    public SessaoTokens login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        Usuario usuario = usuarioRepository.findByEmail(email).orElse(null);
        String hashParaComparar = usuario != null ? usuario.getSenhaHash() : HASH_FICTICIO;

        boolean senhaOk = passwordEncoder.matches(request.senha(), hashParaComparar);
        if (usuario == null || !senhaOk) {
            throw new CredenciaisInvalidasException();
        }

        String accessToken = jwtService.emitirAccessToken(usuario.getId());
        String refreshToken = refreshTokenService.emitir(usuario.getId());
        return new SessaoTokens(new AccessTokenResponse(accessToken), refreshToken);
    }

    @Transactional(noRollbackFor = CredenciaisInvalidasException.class)
    public SessaoTokens refresh(String refreshTokenBruto) {
        RefreshTokenService.Rotacao rotacao = refreshTokenService.rotacionar(refreshTokenBruto);
        String accessToken = jwtService.emitirAccessToken(rotacao.usuarioId());
        return new SessaoTokens(new AccessTokenResponse(accessToken), rotacao.novoToken());
    }

    @Transactional
    public void logout(String refreshTokenBruto, String accessTokenBruto) {
        refreshTokenService.revogar(refreshTokenBruto);
        if (accessTokenBruto == null || accessTokenBruto.isBlank()) {
            return;
        }
        jwtService.extrairClaims(accessTokenBruto).ifPresent(claims ->
                accessTokenRevogacaoService.revogar(claims.jti(), claims.expiresAt())
        );
    }

    public record SessaoTokens(AccessTokenResponse access, String refreshToken) {
    }
}
