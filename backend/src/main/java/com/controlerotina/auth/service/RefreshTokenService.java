package com.controlerotina.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.controlerotina.auth.entity.RefreshToken;
import com.controlerotina.auth.repository.RefreshTokenRepository;
import com.controlerotina.config.SecurityProperties;
import com.controlerotina.shared.exception.CredenciaisInvalidasException;

@Service
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecurityProperties securityProperties;
    private final Clock clock;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            SecurityProperties securityProperties,
            Clock clock
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.securityProperties = securityProperties;
        this.clock = clock;
    }

    @Transactional
    public String emitir(UUID usuarioId) {
        Instant agora = clock.instant();
        String token = gerarTokenOpaco();
        RefreshToken refreshToken = new RefreshToken(
                UUID.randomUUID(),
                usuarioId,
                hash(token),
                agora.plus(securityProperties.jwt().refreshTokenTtl()),
                agora
        );
        refreshTokenRepository.save(refreshToken);
        return token;
    }

    @Transactional(readOnly = true)
    public RefreshToken exigirAtivo(String tokenBruto) {
        if (tokenBruto == null || tokenBruto.isBlank()) {
            throw new CredenciaisInvalidasException();
        }

        RefreshToken token = refreshTokenRepository.findByTokenHash(hash(tokenBruto))
                .orElseThrow(CredenciaisInvalidasException::new);

        if (!token.isAtivo(clock.instant())) {
            throw new CredenciaisInvalidasException();
        }
        return token;
    }

    @Transactional
    public void revogar(String tokenBruto) {
        if (tokenBruto == null || tokenBruto.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hash(tokenBruto))
                .ifPresent(token -> {
                    if (token.getRevokedAt() == null) {
                        token.revogar(clock.instant());
                    }
                });
    }

    @Transactional
    public Rotacao rotacionar(String tokenBruto) {
        RefreshToken atual = exigirAtivo(tokenBruto);
        atual.revogar(clock.instant());
        String novoToken = emitir(atual.getUsuarioId());
        return new Rotacao(atual.getUsuarioId(), novoToken);
    }

    public record Rotacao(UUID usuarioId, String novoToken) {
    }

    private static String gerarTokenOpaco() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
