package com.controlerotina.planner.motor;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.controlerotina.capacidade.entity.DiaSemana;
import com.controlerotina.habito.entity.Habito;
import com.controlerotina.habito.entity.StatusHabito;
import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;

class MotorAlocacaoHabitoTest {

    /** Terça-feira. */
    private static final LocalDate DIA_D = LocalDate.of(2026, 10, 20);

    @Test
    void t1_scoreHabitoEh85MaisFatorDuracaoEEmpateFavorTarefa() {
        assertThat(ScoreV1.calcularHabito(60)).isEqualTo(85 + 15);
        assertThat(ScoreV1.calcularHabito(90)).isEqualTo(85 + 10);

        Instant criado = Instant.parse("2026-10-01T10:00:00Z");
        // ALTA sem prazo + duração > 240 → score 100; hábito 60 min → 100. Empate: tarefa primeiro.
        Tarefa tarefaEmpate = tarefa("tarefa-empate", Prioridade.ALTA, StatusTarefa.PENDENTE,
                null, 300, criado);
        assertThat(ScoreV1.calcular(tarefaEmpate, DIA_D)).isEqualTo(100);

        Habito habito = habito("habito", 60, EnumSet.of(DiaSemana.TERCA), StatusHabito.ATIVO, criado.plusSeconds(1));
        assertThat(ScoreV1.calcularHabito(60)).isEqualTo(100);

        ResultadoAlocacao resultado = MotorAlocacao.alocar(
                List.of(tarefaEmpate),
                390,
                DIA_D,
                List.of(),
                List.of(habito)
        );

        assertThat(resultado.blocos().get(0).tarefaId()).isEqualTo(tarefaEmpate.getId());
        assertThat(resultado.blocos().get(resultado.blocos().size() - 1).habitoId())
                .isEqualTo(habito.getId());
        assertThat(resultado.blocos())
                .filteredOn(BlocoAlocado::isHabito)
                .hasSize(1);
    }

    @Test
    void t2_elegibilidadeHabitoAtivoNoWeekday() {
        Instant criado = Instant.parse("2026-10-01T10:00:00Z");
        Habito ativoTer = habito("ok", 60, EnumSet.of(DiaSemana.TERCA), StatusHabito.ATIVO, criado);
        Habito pausado = habito("pausado", 60, EnumSet.of(DiaSemana.TERCA), StatusHabito.PAUSADO, criado);
        Habito outroDia = habito("quarta", 60, EnumSet.of(DiaSemana.QUARTA), StatusHabito.ATIVO, criado);
        Habito deletado = habito("del", 60, EnumSet.of(DiaSemana.TERCA), StatusHabito.ATIVO, criado);
        deletado.softDelete(criado);

        List<Habito> elegiveis = MotorAlocacao.filtrarHabitosElegiveis(
                List.of(ativoTer, pausado, outroDia, deletado), DIA_D);

        assertThat(elegiveis).extracting(Habito::getId).containsExactly(ativoTer.getId());

        ResultadoAlocacao resultado = MotorAlocacao.alocar(
                List.of(),
                120,
                DIA_D,
                List.of(),
                List.of(ativoTer, pausado, outroDia, deletado)
        );

        assertThat(resultado.blocos()).hasSize(1);
        assertThat(resultado.blocos().get(0).habitoId()).isEqualTo(ativoTer.getId());
        assertThat(resultado.itensNaoAlocados()).isEmpty();
    }

    @Test
    void t3_habitoCabeOuVaiParaOmitidosSemParcial() {
        Instant criado = Instant.parse("2026-10-01T10:00:00Z");
        Habito curto = habito("cabe", 60, EnumSet.of(DiaSemana.TERCA), StatusHabito.ATIVO, criado);

        ResultadoAlocacao cabe = MotorAlocacao.alocar(
                List.of(), 120, DIA_D, List.of(), List.of(curto));

        assertThat(cabe.blocos()).hasSize(1);
        assertThat(cabe.blocos().get(0).duracaoMinutos()).isEqualTo(60);
        assertThat(cabe.blocos().get(0).truncado()).isFalse();
        assertThat(cabe.temConflito()).isFalse();
        assertThat(cabe.itensNaoAlocados()).isEmpty();

        Habito longo = habito("nao-cabe", 90, EnumSet.of(DiaSemana.TERCA), StatusHabito.ATIVO, criado);
        ResultadoAlocacao naoCabe = MotorAlocacao.alocar(
                List.of(), 60, DIA_D, List.of(), List.of(longo));

        assertThat(naoCabe.blocos()).isEmpty();
        assertThat(naoCabe.temConflito()).isTrue();
        assertThat(naoCabe.itensNaoAlocados()).hasSize(1);
        assertThat(naoCabe.itensNaoAlocados().get(0).tipo()).isEqualTo(TipoOrigemAlocacao.HABITO);
        assertThat(naoCabe.itensNaoAlocados().get(0).id()).isEqualTo(longo.getId());
        assertThat(naoCabe.itensNaoAlocados().get(0).motivo()).isEqualTo(MotivoNaoAlocado.CAPACIDADE);
        assertThat(naoCabe.mensagem()).isEqualTo(MotorAlocacao.MENSAGEM_HABITO_OMITIDO);
    }

    @Test
    void t4_urgenteAntesDoHabitoESemPrazoMediaDepois() {
        Instant criado = Instant.parse("2026-10-01T10:00:00Z");
        // ALTA + vence em D: 100+60+15 = 175
        Tarefa urgente = tarefa("urgente", Prioridade.ALTA, StatusTarefa.PENDENTE,
                prazoEmDias(0), 60, criado);
        Habito habito = habito("habito", 60, EnumSet.of(DiaSemana.TERCA), StatusHabito.ATIVO, criado.plusSeconds(1));
        // MEDIA sem prazo: 50+0+15 = 65 < 100 do hábito
        Tarefa semPrazo = tarefa("sem-prazo", Prioridade.MEDIA, StatusTarefa.PENDENTE,
                null, 60, criado.plusSeconds(2));

        ResultadoAlocacao resultado = MotorAlocacao.alocar(
                List.of(urgente, semPrazo),
                180,
                DIA_D,
                List.of(),
                List.of(habito)
        );

        assertThat(resultado.blocos()).hasSize(3);
        assertThat(resultado.blocos().get(0).tarefaId()).isEqualTo(urgente.getId());
        assertThat(resultado.blocos().get(1).habitoId()).isEqualTo(habito.getId());
        assertThat(resultado.blocos().get(2).tarefaId()).isEqualTo(semPrazo.getId());
    }

    @Test
    void correcoes_t2_capacidadeZeroComHabitoElegivelRegistraOmitidoEConflito() {
        Instant criado = Instant.parse("2026-10-01T10:00:00Z");
        Habito habito = habito("habito", 60, EnumSet.of(DiaSemana.TERCA), StatusHabito.ATIVO, criado);

        ResultadoAlocacao resultado = MotorAlocacao.alocar(
                List.of(), 0, DIA_D, List.of(), List.of(habito));

        assertThat(resultado.blocos()).isEmpty();
        assertThat(resultado.temConflito()).isTrue();
        assertThat(resultado.itensNaoAlocados()).hasSize(1);
        assertThat(resultado.itensNaoAlocados().get(0).tipo()).isEqualTo(TipoOrigemAlocacao.HABITO);
        assertThat(resultado.itensNaoAlocados().get(0).id()).isEqualTo(habito.getId());
        assertThat(resultado.mensagem()).isEqualTo(MotorAlocacao.MENSAGEM_HABITO_OMITIDO);
    }

    @Test
    void correcoes_t3_poolUnificadoHabitoVenceBaixaComPrazoLonginquo() {
        Instant criado = Instant.parse("2026-10-01T10:00:00Z");
        // BAIXA + prazo longínquo (>8d): 20+0+15 = 35
        Tarefa baixa = tarefa("baixa-longe", Prioridade.BAIXA, StatusTarefa.PENDENTE,
                prazoEmDias(20), 60, criado);
        // hábito 60 min → score 100
        Habito habito = habito("habito", 60, EnumSet.of(DiaSemana.TERCA), StatusHabito.ATIVO, criado.plusSeconds(1));

        ResultadoAlocacao resultado = MotorAlocacao.alocar(
                List.of(baixa), 60, DIA_D, List.of(), List.of(habito));

        assertThat(resultado.blocos()).hasSize(1);
        assertThat(resultado.blocos().get(0).habitoId()).isEqualTo(habito.getId());
        assertThat(resultado.itensNaoAlocados())
                .anySatisfy(i -> {
                    assertThat(i.tipo()).isEqualTo(TipoOrigemAlocacao.TAREFA);
                    assertThat(i.id()).isEqualTo(baixa.getId());
                });
    }

    @Test
    void correcoes_t4_tarefaQueNaoCabeEntraEmItensNaoAlocados() {
        Instant criado = Instant.parse("2026-10-01T10:00:00Z");
        Tarefa tarefa = tarefa("nao-cabe", Prioridade.MEDIA, StatusTarefa.PENDENTE,
                null, 90, criado);

        // Capacidade < piso (30): não abre bloco; saldo residual vira omitido TAREFA.
        ResultadoAlocacao resultado = MotorAlocacao.alocar(List.of(tarefa), 20, DIA_D);

        assertThat(resultado.blocos()).isEmpty();
        assertThat(resultado.itensNaoAlocados()).hasSize(1);
        assertThat(resultado.itensNaoAlocados().get(0).tipo()).isEqualTo(TipoOrigemAlocacao.TAREFA);
        assertThat(resultado.itensNaoAlocados().get(0).id()).isEqualTo(tarefa.getId());
        assertThat(resultado.itensNaoAlocados().get(0).motivo()).isEqualTo(MotivoNaoAlocado.CAPACIDADE);
        assertThat(resultado.temConflito()).isTrue();
    }

    private static Instant prazoEmDias(int diasAPartirDeD) {
        return DIA_D.plusDays(diasAPartirDeD).atTime(18, 0)
                .atZone(java.time.ZoneId.of("America/Sao_Paulo"))
                .toInstant();
    }

    private static Tarefa tarefa(
            String titulo,
            Prioridade prioridade,
            StatusTarefa status,
            Instant prazo,
            int duracaoMinutos,
            Instant createdAt
    ) {
        return new Tarefa(
                UUID.randomUUID(),
                UUID.randomUUID(),
                titulo,
                "desc",
                prioridade,
                prazo,
                status,
                duracaoMinutos,
                createdAt,
                createdAt
        );
    }

    private static Habito habito(
            String titulo,
            int duracaoMinutos,
            Set<DiaSemana> dias,
            StatusHabito status,
            Instant createdAt
    ) {
        return new Habito(
                UUID.randomUUID(),
                UUID.randomUUID(),
                titulo,
                null,
                duracaoMinutos,
                dias,
                null,
                status,
                0,
                0,
                createdAt,
                createdAt
        );
    }
}
