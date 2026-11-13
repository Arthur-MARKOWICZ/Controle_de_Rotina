package com.controlerotina.tarefa.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DuracaoMinutosValidaValidatorTest {

    private DuracaoMinutosValidaValidator validator;

    @BeforeEach
    void setUp() {
        validator = new DuracaoMinutosValidaValidator();
    }

    @Test
    void deveAceitarMultiploDe15APartirDe15() {
        assertThat(validator.isValid(15, null)).isTrue();
        assertThat(validator.isValid(60, null)).isTrue();
        assertThat(validator.isValid(120, null)).isTrue();
    }

    @Test
    void deveAceitarNullComoAusente() {
        assertThat(validator.isValid(null, null)).isTrue();
    }

    @Test
    void deveRejeitarZero() {
        assertThat(validator.isValid(0, null)).isFalse();
    }

    @Test
    void deveRejeitarValorNegativo() {
        assertThat(validator.isValid(-15, null)).isFalse();
    }

    @Test
    void deveRejeitarAbaixoDoMinimo() {
        assertThat(validator.isValid(14, null)).isFalse();
    }

    @Test
    void deveRejeitarNaoMultiploDe15() {
        assertThat(validator.isValid(16, null)).isFalse();
        assertThat(validator.isValid(50, null)).isFalse();
    }

    @Test
    void deveRejeitarMaximoNaoAlinhado() {
        assertThat(validator.isValid(Integer.MAX_VALUE, null)).isFalse();
    }
}
