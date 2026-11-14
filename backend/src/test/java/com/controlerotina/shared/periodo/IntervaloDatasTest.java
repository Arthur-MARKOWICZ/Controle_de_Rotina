package com.controlerotina.shared.periodo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.controlerotina.shared.exception.IntervaloCapacidadeInvalidoException;

class IntervaloDatasTest {

    private static final LocalDate DIA = LocalDate.of(2026, 11, 14);

    @Test
    void deveAceitarIntervaloDentroDoLimite() {
        assertThatCode(() -> IntervaloDatas.validar(DIA.minusDays(10), DIA, 31)).doesNotThrowAnyException();
    }

    @Test
    void deveAceitarDataDeIgualADataAte() {
        assertThatCode(() -> IntervaloDatas.validar(DIA, DIA, 1)).doesNotThrowAnyException();
    }

    @Test
    void deveAceitarIntervaloExatamenteNoLimite() {
        assertThatCode(() -> IntervaloDatas.validar(DIA.minusDays(365), DIA, 366)).doesNotThrowAnyException();
    }

    @Test
    void deveAceitarIntervaloQueAtravessaViradaDeAno() {
        assertThatCode(() -> IntervaloDatas.validar(
                LocalDate.of(2026, 12, 25), LocalDate.of(2027, 1, 5), 31)).doesNotThrowAnyException();
    }

    @Test
    void deveLancarIntervaloInvalidoQuandoDataDeNula() {
        assertThatThrownBy(() -> IntervaloDatas.validar(null, DIA, 31))
                .isInstanceOf(IntervaloCapacidadeInvalidoException.class)
                .satisfies(e -> assertThat(detalhe(e)).contains("obrigatórios"));
    }

    @Test
    void deveLancarIntervaloInvalidoQuandoDataAteNula() {
        assertThatThrownBy(() -> IntervaloDatas.validar(DIA, null, 31))
                .isInstanceOf(IntervaloCapacidadeInvalidoException.class)
                .satisfies(e -> assertThat(detalhe(e)).contains("obrigatórios"));
    }

    @Test
    void deveLancarIntervaloInvalidoQuandoDataAteAntesDeDataDe() {
        assertThatThrownBy(() -> IntervaloDatas.validar(DIA, DIA.minusDays(1), 31))
                .isInstanceOf(IntervaloCapacidadeInvalidoException.class)
                .satisfies(e -> assertThat(detalhe(e)).contains("maior ou igual"));
    }

    @Test
    void deveLancarIntervaloInvalidoUmDiaAcimaDoLimite() {
        assertThatThrownBy(() -> IntervaloDatas.validar(DIA.minusDays(366), DIA, 366))
                .isInstanceOf(IntervaloCapacidadeInvalidoException.class)
                .satisfies(e -> assertThat(detalhe(e)).contains("366 dias"));
    }

    @Test
    void deveRespeitarLimitePassadoComoParametro() {
        assertThatThrownBy(() -> IntervaloDatas.validar(DIA.minusDays(31), DIA, 31))
                .isInstanceOf(IntervaloCapacidadeInvalidoException.class)
                .satisfies(e -> assertThat(detalhe(e)).contains("31 dias"));
        assertThatCode(() -> IntervaloDatas.validar(DIA.minusDays(31), DIA, 366)).doesNotThrowAnyException();
    }

    private static String detalhe(Throwable e) {
        IntervaloCapacidadeInvalidoException ex = (IntervaloCapacidadeInvalidoException) e;
        assertThat(ex.getBody().getTitle()).isEqualTo("Intervalo inválido");
        return ex.getBody().getDetail();
    }
}
