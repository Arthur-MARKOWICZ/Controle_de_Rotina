package com.controlerotina.auth.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.controlerotina.config.JwtSecretValidator;
import com.controlerotina.config.SecurityProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

@Component
public class JwtService {

    private final SecurityProperties securityProperties;
    private final Clock clock;
    private final byte[] secret;
    private final MACSigner signer;
    private final MACVerifier verifier;

    public JwtService(
            SecurityProperties securityProperties,
            Clock clock,
            JwtSecretValidator jwtSecretValidator
    ) {
        Objects.requireNonNull(jwtSecretValidator, "jwtSecretValidator");
        this.securityProperties = securityProperties;
        this.clock = clock;
        this.secret = securityProperties.jwt().secret().getBytes(StandardCharsets.UTF_8);
        try {
            this.signer = new MACSigner(secret);
            this.verifier = new MACVerifier(secret);
        } catch (JOSEException e) {
            throw new IllegalStateException("Segredo JWT inválido para HS256", e);
        }
    }

    public String emitirAccessToken(UUID usuarioId) {
        Instant agora = clock.instant();
        Instant expira = agora.plus(securityProperties.jwt().accessTokenTtl());

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(usuarioId.toString())
                .issuer(securityProperties.jwt().issuer())
                .audience(securityProperties.jwt().audience())
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(agora))
                .expirationTime(Date.from(expira))
                .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException("Falha ao assinar access token", e);
        }
        return jwt.serialize();
    }

    public Optional<UUID> extrairUsuarioId(String token) {
        return extrairClaims(token).map(AccessClaims::usuarioId);
    }

    public Optional<AccessClaims> extrairClaims(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(verifier)) {
                return Optional.empty();
            }

            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date expiration = claims.getExpirationTime();
            if (expiration == null || !expiration.toInstant().isAfter(clock.instant())) {
                return Optional.empty();
            }

            if (!issuerValido(claims) || !audienceValida(claims)) {
                return Optional.empty();
            }

            String subject = claims.getSubject();
            if (subject == null || subject.isBlank()) {
                return Optional.empty();
            }

            String jti = claims.getJWTID();
            if (jti == null || jti.isBlank()) {
                return Optional.empty();
            }

            return Optional.of(new AccessClaims(
                    UUID.fromString(subject),
                    jti,
                    expiration.toInstant()
            ));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public record AccessClaims(UUID usuarioId, String jti, Instant expiresAt) {
    }

    private boolean issuerValido(JWTClaimsSet claims) {
        String esperado = securityProperties.jwt().issuer();
        String atual = claims.getIssuer();
        return esperado != null && esperado.equals(atual);
    }

    private boolean audienceValida(JWTClaimsSet claims) {
        String esperado = securityProperties.jwt().audience();
        List<String> atual = claims.getAudience();
        return esperado != null && atual != null && atual.contains(esperado);
    }
}
