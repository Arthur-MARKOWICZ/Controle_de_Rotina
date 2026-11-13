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

    @Test
    void deveAceitarSenhaCom72Caracteres() {
        String senha = "Aa1!" + "a".repeat(68);
        assertThat(senha).hasSize(72);
        assertThat(validator.isValid(senha, null)).isTrue();
    }

    @Test
    void deveRejeitarSenhaCom73Caracteres() {
        String senha = "Aa1!" + "a".repeat(69);
        assertThat(senha).hasSize(73);
        assertThat(validator.isValid(senha, null)).isFalse();
    }

    @Test
    void deveRejeitarSenhaCom7CaracteresNoLimiteMinimo() {
        assertThat(validator.isValid("Aa1!aaa", null)).isFalse();
        assertThat(validator.isValid("Aa1!aaaa", null)).isTrue();
    }

    @Test
    void senhaEmBrancoNaoDeveSerTratadaComoAusente() {
        assertThat(validator.isValid("        ", null)).isFalse();
    }
}
