package com.controlerotina.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.controlerotina.config.JwtSecretValidator;
import com.controlerotina.config.SecurityProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

class JwtServiceTest {

    private static final String SECRET = "controle-rotina-test-secret-key-min-32-chars!";
    private static final String ISSUER = "controle-rotina";
    private static final String AUDIENCE = "controle-rotina-api";
    private static final Instant AGORA = Instant.parse("2026-10-29T12:00:00Z");

    private JwtService jwtService;
    private UUID usuarioId;

    @BeforeEach
    void setUp() {
        SecurityProperties properties = new SecurityProperties(
                new SecurityProperties.Jwt(
                        SECRET,
                        Duration.ofMinutes(15),
                        Duration.ofDays(5),
                        ISSUER,
                        AUDIENCE
                ),
                new SecurityProperties.RefreshCookie(
                        "refresh_token",
                        "/auth",
                        true,
                        false,
                        "Lax"
                )
        );
        jwtService = new JwtService(
                properties,
                Clock.fixed(AGORA, ZoneOffset.UTC),
                new JwtSecretValidator(properties)
        );
        usuarioId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    }

    @Test
    void deveEmitirTokenComIssAudESub() throws Exception {
        String token = jwtService.emitirAccessToken(usuarioId);

        SignedJWT jwt = SignedJWT.parse(token);
        JWTClaimsSet claims = jwt.getJWTClaimsSet();
        assertThat(claims.getSubject()).isEqualTo(usuarioId.toString());
        assertThat(claims.getIssuer()).isEqualTo(ISSUER);
        assertThat(claims.getAudience()).containsExactly(AUDIENCE);
        assertThat(jwtService.extrairUsuarioId(token)).contains(usuarioId);
    }

    @Test
    void deveAceitarTokenValido() {
        String token = jwtService.emitirAccessToken(usuarioId);

        assertThat(jwtService.extrairUsuarioId(token)).contains(usuarioId);
    }

    @Test
    void deveRejeitarTokenExpirado() throws Exception {
        String token = assinar(claimsCompletos()
                .expirationTime(Date.from(AGORA.minusSeconds(1)))
                .build());

        assertThat(jwtService.extrairUsuarioId(token)).isEmpty();
    }

    @Test
    void deveRejeitarTokenComAssinaturaTrocada() throws Exception {
        String token = assinarComSegredo(
                claimsCompletos().build(),
                "outro-segredo-jwt-com-mais-de-32-chars!!"
        );

        assertThat(jwtService.extrairUsuarioId(token)).isEmpty();
    }

    @Test
    void deveRejeitarTokenComSubjectVazio() throws Exception {
        String token = assinar(claimsCompletos().subject("").build());

        assertThat(jwtService.extrairUsuarioId(token)).isEmpty();
    }

    @Test
    void deveRejeitarTokenComSubjectMalformado() throws Exception {
        String token = assinar(claimsCompletos().subject("nao-e-uuid").build());

        assertThat(jwtService.extrairUsuarioId(token)).isEmpty();
    }

    @Test
    void deveRejeitarTokenComAssinaturaValidaSemIss() throws Exception {
        String token = assinar(claimsBase().build());

        assertThat(jwtService.extrairUsuarioId(token)).isEmpty();
    }

    @Test
    void deveRejeitarTokenComAssinaturaValidaComIssErrado() throws Exception {
        String token = assinar(claimsBase().issuer("outro-issuer").audience(AUDIENCE).build());

        assertThat(jwtService.extrairUsuarioId(token)).isEmpty();
    }

    @Test
    void deveRejeitarTokenComAssinaturaValidaSemAud() throws Exception {
        String token = assinar(claimsBase().issuer(ISSUER).build());

        assertThat(jwtService.extrairUsuarioId(token)).isEmpty();
    }

    @Test
    void deveRejeitarTokenComAssinaturaValidaComAudErrado() throws Exception {
        String token = assinar(claimsBase().issuer(ISSUER).audience("outro-aud").build());

        assertThat(jwtService.extrairUsuarioId(token)).isEmpty();
    }

    private JWTClaimsSet.Builder claimsBase() {
        return new JWTClaimsSet.Builder()
                .subject(usuarioId.toString())
                .issueTime(Date.from(AGORA))
                .expirationTime(Date.from(AGORA.plus(Duration.ofMinutes(15))));
    }

    private JWTClaimsSet.Builder claimsCompletos() {
        return claimsBase().issuer(ISSUER).audience(AUDIENCE);
    }

    private static String assinar(JWTClaimsSet claims) throws Exception {
        return assinarComSegredo(claims, SECRET);
    }

    private static String assinarComSegredo(JWTClaimsSet claims, String secret) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}
