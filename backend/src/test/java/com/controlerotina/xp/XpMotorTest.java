package com.controlerotina.xp;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.controlerotina.tarefa.entity.Prioridade;

class XpMotorTest {

    private static final Instant INICIO = Instant.parse("2026-10-27T12:00:00Z");

    @ParameterizedTest
    @CsvSource({
            "BAIXA, 1.0",
            "MEDIA, 1.5",
            "ALTA, 2.0"
    })
    void deveRetornarPesoDasTresPrioridades(Prioridade prioridade, double pesoEsperado) {
        assertThat(XpMotor.peso(prioridade)).isEqualTo(pesoEsperado);
    }

    @ParameterizedTest
    @CsvSource({
            "60, BAIXA, 60",
            "60, MEDIA, 90",
            "60, ALTA, 120",
            "45, MEDIA, 68"
    })
    void deveCalcularXpDoBlocoComoMinutosEfetivosVezesPeso(int minutosEfetivos, Prioridade prioridade, int xpEsperado) {
        assertThat(XpMotor.xpBloco(minutosEfetivos, prioridade)).isEqualTo(xpEsperado);
    }

    @ParameterizedTest
    @CsvSource({
            "60, BAIXA, 6",
            "60, MEDIA, 9",
            "60, ALTA, 12",
            "120, MEDIA, 18"
    })
    void deveCalcularBonusDaTarefaComoDezPorCentoDosMinutosEfetivos(
            int somaMinutosEfetivos,
            Prioridade prioridade,
            int bonusEsperado
    ) {
        assertThat(XpMotor.bonusTarefa(somaMinutosEfetivos, prioridade)).isEqualTo(bonusEsperado);
    }

    @Test
    void t1_bloco60MinApos80PorCentoCreditaXpCheio() {
        Instant fim = INICIO.plus(48, ChronoUnit.MINUTES);

        assertThat(XpMotor.minutosEfetivos(60, INICIO, fim)).isEqualTo(60);
        assertThat(XpMotor.xpBloco(60, INICIO, fim, Prioridade.MEDIA)).isEqualTo(90);
    }

    @Test
    void t2_concluirEmPoucosSegundosCreditaZeroXp() {
        Instant fim = INICIO.plus(5, ChronoUnit.SECONDS);

        assertThat(XpMotor.minutosEfetivos(60, INICIO, fim)).isZero();
        assertThat(XpMotor.xpBloco(60, INICIO, fim, Prioridade.MEDIA)).isZero();
    }

    @Test
    void t3_metadeDoTempoCreditaMetadeDoXpSemBonusDe80PorCento() {
        Instant fim = INICIO.plus(30, ChronoUnit.MINUTES);

        assertThat(XpMotor.minutosEfetivos(60, INICIO, fim)).isEqualTo(30);
        assertThat(XpMotor.xpBloco(60, INICIO, fim, Prioridade.MEDIA)).isEqualTo(45);
    }

    @Test
    void t4_bonusFinalizacaoUsaSomaEfetivosNaoEstimativaCheia() {
        // estimativa da tarefa seria 60 → bônus 9; com 30 efetivos → bônus 5
        assertThat(XpMotor.bonusTarefa(30, Prioridade.MEDIA)).isEqualTo(5);
        assertThat(XpMotor.bonusTarefa(60, Prioridade.MEDIA)).isEqualTo(9);
    }

    @Test
    void progressoComZeroXpFicaNoNivel1() {
        XpMotor.ProgressoXp progresso = XpMotor.progresso(0);

        assertThat(progresso.nivel()).isEqualTo(1);
        assertThat(progresso.xpNoNivel()).isZero();
        assertThat(progresso.xpParaProximoNivel()).isEqualTo(10);
    }

    @Test
    void progressoComCustoExatoDoNivelSobeSemResto() {
        // nível 1 custa 10 XP → exatamente 10 sobe para nível 2 com 0 no nível
        XpMotor.ProgressoXp progresso = XpMotor.progresso(10);

        assertThat(progresso.nivel()).isEqualTo(2);
        assertThat(progresso.xpNoNivel()).isZero();
        assertThat(progresso.xpParaProximoNivel()).isEqualTo(20);
    }

    @Test
    void progressoComUmXpAcimaDoCustoDoNivelMantemResto() {
        XpMotor.ProgressoXp progresso = XpMotor.progresso(11);

        assertThat(progresso.nivel()).isEqualTo(2);
        assertThat(progresso.xpNoNivel()).isEqualTo(1);
        assertThat(progresso.xpParaProximoNivel()).isEqualTo(20);
    }
}
