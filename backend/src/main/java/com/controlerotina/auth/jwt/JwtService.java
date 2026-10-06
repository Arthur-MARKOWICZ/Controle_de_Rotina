package com.controlerotina.auth.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

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

    public JwtService(SecurityProperties securityProperties, Clock clock) {
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
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(verifier)) {
                return Optional.empty();
            }

            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date expiration = claims.getExpirationTime();
            if (expiration == null || expiration.toInstant().isBefore(clock.instant())) {
                return Optional.empty();
            }

            String subject = claims.getSubject();
            if (subject == null || subject.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(UUID.fromString(subject));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
