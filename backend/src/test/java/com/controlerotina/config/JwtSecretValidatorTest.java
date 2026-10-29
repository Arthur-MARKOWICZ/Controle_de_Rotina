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
}
