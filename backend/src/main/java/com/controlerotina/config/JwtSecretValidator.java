package com.controlerotina.config;

import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;

@Component
public class JwtSecretValidator {

    static final int MIN_SECRET_LENGTH_BYTES = 32;

    public JwtSecretValidator(SecurityProperties securityProperties) {
        validar(securityProperties.jwt().secret());
    }

    static void validar(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "Segredo JWT ausente. Defina a variável de ambiente APP_SECURITY_JWT_SECRET "
                            + "com pelo menos " + MIN_SECRET_LENGTH_BYTES
                            + " bytes, ou ative o perfil local/dev com secret configurado.");
        }

        int lengthBytes = secret.getBytes(StandardCharsets.UTF_8).length;
        if (lengthBytes < MIN_SECRET_LENGTH_BYTES) {
            throw new IllegalStateException(
                    "app.security.jwt.secret deve ter pelo menos " + MIN_SECRET_LENGTH_BYTES
                            + " bytes para HS256 (atual: " + lengthBytes + ").");
        }
    }
}
