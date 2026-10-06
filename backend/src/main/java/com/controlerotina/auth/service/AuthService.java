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

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService
    ) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public SessaoTokens login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(CredenciaisInvalidasException::new);

        if (!passwordEncoder.matches(request.senha(), usuario.getSenhaHash())) {
            throw new CredenciaisInvalidasException();
        }

        String accessToken = jwtService.emitirAccessToken(usuario.getId());
        String refreshToken = refreshTokenService.emitir(usuario.getId());
        return new SessaoTokens(new AccessTokenResponse(accessToken), refreshToken);
    }

    @Transactional
    public SessaoTokens refresh(String refreshTokenBruto) {
        RefreshTokenService.Rotacao rotacao = refreshTokenService.rotacionar(refreshTokenBruto);
        String accessToken = jwtService.emitirAccessToken(rotacao.usuarioId());
        return new SessaoTokens(new AccessTokenResponse(accessToken), rotacao.novoToken());
    }

    @Transactional
    public void logout(String refreshTokenBruto) {
        refreshTokenService.revogar(refreshTokenBruto);
    }

    public record SessaoTokens(AccessTokenResponse access, String refreshToken) {
    }
}
