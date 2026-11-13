package com.controlerotina.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JwtSecretValidatorTest {

    private static final String SECRET_OK = "controle-rotina-dev-secret-key-min-32-chars!!";

    @Test
    void deveFalharQuandoSecretAusente() {
        assertThatThrownBy(() -> JwtSecretValidator.validar(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_SECURITY_JWT_SECRET");

        assertThatThrownBy(() -> JwtSecretValidator.validar("  "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_SECURITY_JWT_SECRET");
    }

    @Test
    void deveFalharQuandoSecretCurto() {
        assertThatThrownBy(() -> JwtSecretValidator.validar("curto-demais"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }

    @Test
    void deveAceitarSecretComTamanhoAdequado() {
        assertThatCode(() -> JwtSecretValidator.validar(SECRET_OK))
                .doesNotThrowAnyException();
    }

    @Test
    void deveAceitarSecretComExatamente32Bytes() {
        String secret32 = "a".repeat(32);
        assertThatCode(() -> JwtSecretValidator.validar(secret32))
                .doesNotThrowAnyException();
    }

    @Test
    void deveFalharQuandoSecretTem31Bytes() {
        assertThatThrownBy(() -> JwtSecretValidator.validar("a".repeat(31)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32")
                .hasMessageContaining("31");
    }

    @Test
    void deveFalharQuandoSecretUtf8FicaAbaixoDe32Bytes() {
        assertThatThrownBy(() -> JwtSecretValidator.validar("á".repeat(15)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void construtorDeveValidarSecretDasProperties() {
        assertThatThrownBy(() -> new JwtSecretValidator(propertiesComSecret("curto")))
                .isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> new JwtSecretValidator(propertiesComSecret(SECRET_OK)))
                .doesNotThrowAnyException();
    }

    private static com.controlerotina.config.SecurityProperties propertiesComSecret(String secret) {
        return new com.controlerotina.config.SecurityProperties(
                new com.controlerotina.config.SecurityProperties.Jwt(
                        secret,
                        java.time.Duration.ofMinutes(15),
                        java.time.Duration.ofDays(5),
                        "controle-rotina",
                        "controle-rotina-api"
                ),
                new com.controlerotina.config.SecurityProperties.RefreshCookie(
                        "refresh_token", "/auth", true, false, "Lax"
                )
        );
    }
}
