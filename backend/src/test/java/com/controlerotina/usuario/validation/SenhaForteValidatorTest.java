package com.controlerotina.usuario.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SenhaForteValidatorTest {

    private SenhaForteValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SenhaForteValidator();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Senha@123",
            "Abcdef1!",
            "Xyz9#wxyz"
    })
    void deveAceitarSenhaForte(String senha) {
        assertThat(validator.isValid(senha, null)).isTrue();
    }

    @Test
    void deveAceitarNullComoAusente() {
        assertThat(validator.isValid(null, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "curta1!",
            "semmaiuscula1!",
            "SEMMINUSCULA1!",
            "SemNumero!",
            "SemEspecial1",
            "12345678"
    })
    void deveRejeitarSenhaFraca(String senha) {
        assertThat(validator.isValid(senha, null)).isFalse();
    }
}
