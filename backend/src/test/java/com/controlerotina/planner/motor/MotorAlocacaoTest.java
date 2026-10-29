package com.controlerotina.planner.motor;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;

class MotorAlocacaoTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate DIA_D = LocalDate.of(2026, 10, 20);

    @Test
    void deveRetornarPlanoVazioQuandoCapacidadeZero() {
        Tarefa tarefa = tarefa("A", Prioridade.ALTA, StatusTarefa.PENDENTE, prazoEmDias(1), 60, Instant.parse("2026-10-01T10:00:00Z"));

        ResultadoAlocacao resultado = MotorAlocacao.alocar(List.of(tarefa), 0, DIA_D);

        assertThat(resultado.blocos()).isEmpty();
        assertThat(resultado.temConflito()).isFalse();
        assertThat(resultado.mensagem()).isEqualTo(MotorAlocacao.MENSAGEM_SEM_TEMPO);
    }

    @Test
    void deveAlocarTarefasQueCabemPorOrdemDeScore() {
        Instant criadoCedo = Instant.parse("2026-10-01T10:00:00Z");
        Instant criadoTarde = Instant.parse("2026-10-02T10:00:00Z");
        Tarefa baixa = tarefa("baixa", Prioridade.BAIXA, StatusTarefa.PENDENTE, prazoEmDias(10), 60, criadoCedo);
        Tarefa alta = tarefa("alta", Prioridade.ALTA, StatusTarefa.PENDENTE, prazoEmDias(10), 60, criadoTarde);

        ResultadoAlocacao resultado = MotorAlocacao.alocar(List.of(baixa, alta), 120, DIA_D);

        assertThat(resultado.blocos()).hasSize(2);
        assertThat(resultado.blocos().get(0).tarefaId()).isEqualTo(alta.getId());
        assertThat(resultado.blocos().get(0).duracaoMinutos()).isEqualTo(60);
        assertThat(resultado.blocos().get(0).ordem()).isEqualTo(1);
        assertThat(resultado.blocos().get(1).tarefaId()).isEqualTo(baixa.getId());
        assertThat(resultado.temConflito()).isFalse();
        assertThat(resultado.mensagem()).isNull();
    }

    @Test
    void deveTruncarQuandoTarefaNaoCabeInteira() {
        Tarefa grande = tarefa("grande", Prioridade.ALTA, StatusTarefa.PENDENTE, prazoEmDias(1), 180, Instant.parse("2026-10-01T10:00:00Z"));

        ResultadoAlocacao resultado = MotorAlocacao.alocar(List.of(grande), 90, DIA_D);

        assertThat(resultado.blocos()).hasSize(1);
        assertThat(resultado.blocos().get(0).duracaoMinutos()).isEqualTo(90);
        assertThat(resultado.blocos().get(0).truncado()).isTrue();
        assertThat(resultado.temConflito()).isTrue();
        assertThat(resultado.mensagem()).isEqualTo(MotorAlocacao.MENSAGEM_PARCIAL);
    }

    @Test
    void devePararAposEsgotarCapacidadeComCorte() {
        Instant criado = Instant.parse("2026-10-01T10:00:00Z");
        Tarefa primeira = tarefa("1", Prioridade.ALTA, StatusTarefa.PENDENTE, prazoEmDias(1), 60, criado);
        Tarefa segunda = tarefa("2", Prioridade.MEDIA, StatusTarefa.PENDENTE, prazoEmDias(1), 120, criado.plusSeconds(1));

        ResultadoAlocacao resultado = MotorAlocacao.alocar(List.of(primeira, segunda), 90, DIA_D);

        assertThat(resultado.blocos()).hasSize(2);
        assertThat(resultado.blocos().get(0).duracaoMinutos()).isEqualTo(60);
        assertThat(resultado.blocos().get(0).truncado()).isFalse();
        assertThat(resultado.blocos().get(1).duracaoMinutos()).isEqualTo(30);
        assertThat(resultado.blocos().get(1).truncado()).isTrue();
        assertThat(resultado.temConflito()).isTrue();
    }

    @Test
    void deveDesempatarPorPrazoMaisCedoDepoisCreatedAt() {
        Instant criadoCedo = Instant.parse("2026-10-01T10:00:00Z");
        Instant criadoTarde = Instant.parse("2026-10-02T10:00:00Z");
        Instant prazoCedo = prazoEmDias(2);
        Instant prazoTarde = prazoEmDias(3);

        Tarefa mesmaScorePrazoTarde = tarefa("tardia", Prioridade.MEDIA, StatusTarefa.PENDENTE, prazoTarde, 60, criadoCedo);
        Tarefa mesmaScorePrazoCedo = tarefa("cedo", Prioridade.MEDIA, StatusTarefa.PENDENTE, prazoCedo, 60, criadoTarde);

        ResultadoAlocacao porPrazo = MotorAlocacao.alocar(
                List.of(mesmaScorePrazoTarde, mesmaScorePrazoCedo), 120, DIA_D);

        assertThat(porPrazo.blocos().get(0).tarefaId()).isEqualTo(mesmaScorePrazoCedo.getId());

        Tarefa empateA = tarefa("a", Prioridade.MEDIA, StatusTarefa.PENDENTE, prazoCedo, 60, criadoCedo);
        Tarefa empateB = tarefa("b", Prioridade.MEDIA, StatusTarefa.PENDENTE, prazoCedo, 60, criadoTarde);

        ResultadoAlocacao porCreated = MotorAlocacao.alocar(List.of(empateB, empateA), 120, DIA_D);

        assertThat(porCreated.blocos().get(0).tarefaId()).isEqualTo(empateA.getId());
    }

    @Test
    void deveConverterHorasNulasOuZeroEmCapacidadeZero() {
        assertThat(MotorAlocacao.capacidadeMinutos(null)).isZero();
        assertThat(MotorAlocacao.capacidadeMinutos(0)).isZero();
        assertThat(MotorAlocacao.capacidadeMinutos(2)).isEqualTo(120);
    }

    @Test
    void deveAlocarSaldoQuandoHaMinutosConcluidos() {
        Tarefa tarefa = tarefa("parcial", Prioridade.ALTA, StatusTarefa.EM_PROGRESSO, prazoEmDias(1), 180, Instant.parse("2026-10-01T10:00:00Z"));
        int saldo = MotorAlocacao.calcularSaldoMinutos(3 * 60, 60);

        ResultadoAlocacao resultado = MotorAlocacao.alocar(
                List.of(tarefa),
                Map.of(tarefa.getId(), saldo),
                240,
                DIA_D
        );

        assertThat(saldo).isEqualTo(120);
        assertThat(resultado.blocos()).hasSize(2);
        assertThat(resultado.blocos().get(0).duracaoMinutos()).isEqualTo(90);
        assertThat(resultado.blocos().get(1).duracaoMinutos()).isEqualTo(30);
        assertThat(resultado.temConflito()).isFalse();
    }

    @Test
    void saldoZeroNaoEElegivel() {
        Tarefa coberta = tarefa("coberta", Prioridade.ALTA, StatusTarefa.EM_PROGRESSO, prazoEmDias(1), 60, Instant.parse("2026-10-01T10:00:00Z"));
        Tarefa restante = tarefa("restante", Prioridade.MEDIA, StatusTarefa.PENDENTE, prazoEmDias(1), 60, Instant.parse("2026-10-01T11:00:00Z"));
        int saldoZero = MotorAlocacao.calcularSaldoMinutos(60, 60);

        ResultadoAlocacao resultado = MotorAlocacao.alocar(
                List.of(coberta, restante),
                Map.of(coberta.getId(), saldoZero, restante.getId(), 60),
                120,
                DIA_D
        );

        assertThat(saldoZero).isZero();
        assertThat(resultado.blocos()).hasSize(1);
        assertThat(resultado.blocos().get(0).tarefaId()).isEqualTo(restante.getId());
    }

    @Test
    void execucaoAbortadaNaoReduzSaldo() {
        // minutos concluídos = 0 (abortado não entra na soma) → saldo = estimativa cheia
        Tarefa tarefa = tarefa("com-aborto", Prioridade.ALTA, StatusTarefa.PENDENTE, prazoEmDias(1), 120, Instant.parse("2026-10-01T10:00:00Z"));
        int saldo = MotorAlocacao.calcularSaldoMinutos(2 * 60, 0);

        ResultadoAlocacao resultado = MotorAlocacao.alocar(
                List.of(tarefa),
                Map.of(tarefa.getId(), saldo),
                180,
                DIA_D
        );

        assertThat(saldo).isEqualTo(120);
        assertThat(resultado.blocos()).hasSize(2);
        assertThat(resultado.blocos().get(0).duracaoMinutos()).isEqualTo(90);
        assertThat(resultado.blocos().get(1).duracaoMinutos()).isEqualTo(30);
    }

    @Test
    void janelaDistribuiTarefaDe3hCom1hPorDiaEmDiasDistintos() {
        // segunda 20/10 → sexta 24/10
        Tarefa tarefa = tarefa("multidia", Prioridade.ALTA, StatusTarefa.PENDENTE, prazoEmDias(4), 180, Instant.parse("2026-10-01T10:00:00Z"));
        List<MotorAlocacao.DiaCapacidade> dias = List.of(
                new MotorAlocacao.DiaCapacidade(DIA_D, 60),
                new MotorAlocacao.DiaCapacidade(DIA_D.plusDays(1), 60),
                new MotorAlocacao.DiaCapacidade(DIA_D.plusDays(2), 60),
                new MotorAlocacao.DiaCapacidade(DIA_D.plusDays(3), 60),
                new MotorAlocacao.DiaCapacidade(DIA_D.plusDays(4), 60)
        );

        Map<LocalDate, ResultadoAlocacao> janela = MotorAlocacao.alocarJanela(
                List.of(tarefa),
                Map.of(tarefa.getId(), 180),
                dias
        );

        int total = janela.values().stream()
                .flatMap(r -> r.blocos().stream())
                .mapToInt(BlocoAlocado::duracaoMinutos)
                .sum();
        assertThat(total).isEqualTo(180);
        assertThat(janela.get(DIA_D).blocos()).hasSize(1);
        assertThat(janela.get(DIA_D).blocos().get(0).duracaoMinutos()).isEqualTo(60);
        assertThat(janela.get(DIA_D.plusDays(1)).blocos().get(0).duracaoMinutos()).isEqualTo(60);
        assertThat(janela.get(DIA_D.plusDays(2)).blocos().get(0).duracaoMinutos()).isEqualTo(60);
        assertThat(janela.get(DIA_D.plusDays(3)).blocos()).isEmpty();
        assertThat(janela.get(DIA_D.plusDays(4)).blocos()).isEmpty();
    }

    @Test
    void janelaSomaNaoExcedeSaldo() {
        Tarefa tarefa = tarefa("saldo", Prioridade.MEDIA, StatusTarefa.PENDENTE, prazoEmDias(5), 120, Instant.parse("2026-10-01T10:00:00Z"));
        List<MotorAlocacao.DiaCapacidade> dias = List.of(
                new MotorAlocacao.DiaCapacidade(DIA_D, 90),
                new MotorAlocacao.DiaCapacidade(DIA_D.plusDays(1), 90),
                new MotorAlocacao.DiaCapacidade(DIA_D.plusDays(2), 90)
        );

        Map<LocalDate, ResultadoAlocacao> janela = MotorAlocacao.alocarJanela(
                List.of(tarefa),
                Map.of(tarefa.getId(), 120),
                dias
        );

        int total = janela.values().stream()
                .flatMap(r -> r.blocos().stream())
                .mapToInt(BlocoAlocado::duracaoMinutos)
                .sum();
        assertThat(total).isEqualTo(120);
        assertThat(total).isLessThanOrEqualTo(120);
    }

    @Test
    void janelaCapacidadeZeroNaoGeraBlocoNaqueleDia() {
        Tarefa tarefa = tarefa("cap0", Prioridade.ALTA, StatusTarefa.PENDENTE, prazoEmDias(2), 120, Instant.parse("2026-10-01T10:00:00Z"));
        List<MotorAlocacao.DiaCapacidade> dias = List.of(
                new MotorAlocacao.DiaCapacidade(DIA_D, 0),
                new MotorAlocacao.DiaCapacidade(DIA_D.plusDays(1), 120)
        );

        Map<LocalDate, ResultadoAlocacao> janela = MotorAlocacao.alocarJanela(
                List.of(tarefa),
                Map.of(tarefa.getId(), 120),
                dias
        );

        assertThat(janela.get(DIA_D).blocos()).isEmpty();
        assertThat(janela.get(DIA_D).mensagem()).isEqualTo(MotorAlocacao.MENSAGEM_SEM_TEMPO);
        assertThat(janela.get(DIA_D.plusDays(1)).blocos()).hasSize(2);
        assertThat(janela.get(DIA_D.plusDays(1)).blocos().get(0).duracaoMinutos()).isEqualTo(90);
        assertThat(janela.get(DIA_D.plusDays(1)).blocos().get(1).duracaoMinutos()).isEqualTo(30);
    }

    @Test
    void janelaNaoAlocaDepoisDoPrazo() {
        Tarefa tarefa = tarefa("prazo", Prioridade.ALTA, StatusTarefa.PENDENTE, prazoEmDias(1), 180, Instant.parse("2026-10-01T10:00:00Z"));
        List<MotorAlocacao.DiaCapacidade> dias = List.of(
                new MotorAlocacao.DiaCapacidade(DIA_D, 60),
                new MotorAlocacao.DiaCapacidade(DIA_D.plusDays(1), 60),
                new MotorAlocacao.DiaCapacidade(DIA_D.plusDays(2), 60)
        );

        Map<LocalDate, ResultadoAlocacao> janela = MotorAlocacao.alocarJanela(
                List.of(tarefa),
                Map.of(tarefa.getId(), 180),
                dias
        );

        assertThat(janela.get(DIA_D).blocos()).hasSize(1);
        assertThat(janela.get(DIA_D.plusDays(1)).blocos()).hasSize(1);
        assertThat(janela.get(DIA_D.plusDays(2)).blocos()).isEmpty();
        int total = janela.values().stream()
                .flatMap(r -> r.blocos().stream())
                .mapToInt(BlocoAlocado::duracaoMinutos)
                .sum();
        assertThat(total).isEqualTo(120);
    }

    @Test
    void t1_janela12hCom8hAtePrazoMarcaConflitoComMinutosFora() {
        // 12h de trabalho, capacidade 8h até o prazo (4 dias × 2h) → 4h fora
        Tarefa tarefa = tarefa("Relatorio", Prioridade.ALTA, StatusTarefa.PENDENTE, prazoEmDias(3), 720, Instant.parse("2026-10-01T10:00:00Z"));
        List<MotorAlocacao.DiaCapacidade> dias = List.of(
                new MotorAlocacao.DiaCapacidade(DIA_D, 120),
                new MotorAlocacao.DiaCapacidade(DIA_D.plusDays(1), 120),
                new MotorAlocacao.DiaCapacidade(DIA_D.plusDays(2), 120),
                new MotorAlocacao.DiaCapacidade(DIA_D.plusDays(3), 120)
        );

        Map<LocalDate, ResultadoAlocacao> janela = MotorAlocacao.alocarJanela(
                List.of(tarefa),
                Map.of(tarefa.getId(), 720),
                dias
        );

        int total = janela.values().stream()
                .flatMap(r -> r.blocos().stream())
                .mapToInt(BlocoAlocado::duracaoMinutos)
                .sum();
        assertThat(total).isEqualTo(480);
        assertThat(janela.get(DIA_D).temConflito()).isTrue();
        assertThat(janela.get(DIA_D).mensagem())
                .contains("Relatorio")
                .contains("240")
                .contains("23/10/2026");
    }

    @Test
    void t2_janelaTarefaQueCabeInteiraNaoMarcaConflito() {
        Tarefa tarefa = tarefa("Cabivel", Prioridade.MEDIA, StatusTarefa.PENDENTE, prazoEmDias(2), 180, Instant.parse("2026-10-01T10:00:00Z"));
        List<MotorAlocacao.DiaCapacidade> dias = List.of(
                new MotorAlocacao.DiaCapacidade(DIA_D, 120),
                new MotorAlocacao.DiaCapacidade(DIA_D.plusDays(1), 120),
                new MotorAlocacao.DiaCapacidade(DIA_D.plusDays(2), 120)
        );

        Map<LocalDate, ResultadoAlocacao> janela = MotorAlocacao.alocarJanela(
                List.of(tarefa),
                Map.of(tarefa.getId(), 180),
                dias
        );

        int total = janela.values().stream()
                .flatMap(r -> r.blocos().stream())
                .mapToInt(BlocoAlocado::duracaoMinutos)
                .sum();
        assertThat(total).isEqualTo(180);
        assertThat(janela.values()).allMatch(r -> !r.temConflito());
        assertThat(janela.get(DIA_D).mensagem()).isNull();
    }

    @Test
    void t2_tarefa180EmDia240NaoGeraBlocoUnicoDe180() {
        Tarefa tarefa = tarefa("grande", Prioridade.ALTA, StatusTarefa.PENDENTE, prazoEmDias(0), 180,
                Instant.parse("2026-10-01T10:00:00Z"));
        List<MotorAlocacao.DiaCapacidade> dias = List.of(
                new MotorAlocacao.DiaCapacidade(DIA_D, 240)
        );

        Map<LocalDate, ResultadoAlocacao> janela = MotorAlocacao.alocarJanela(
                List.of(tarefa),
                Map.of(tarefa.getId(), 180),
                dias
        );

        List<BlocoAlocado> blocos = janela.get(DIA_D).blocos();
        assertThat(blocos).hasSize(2);
        assertThat(blocos).allMatch(b -> b.duracaoMinutos() <= MotorAlocacao.TETO_BLOCO_MINUTOS);
        assertThat(blocos.get(0).duracaoMinutos()).isEqualTo(90);
        assertThat(blocos.get(1).duracaoMinutos()).isEqualTo(90);
        assertThat(blocos.stream().mapToInt(BlocoAlocado::duracaoMinutos).sum()).isEqualTo(180);
    }

    @Test
    void t3_capacidadeRestante15NaoAbreBloco() {
        Tarefa tarefa = tarefa("resto", Prioridade.ALTA, StatusTarefa.PENDENTE, prazoEmDias(0), 60,
                Instant.parse("2026-10-01T10:00:00Z"));
        List<MotorAlocacao.DiaCapacidade> dias = List.of(
                new MotorAlocacao.DiaCapacidade(DIA_D, 15)
        );

        Map<LocalDate, ResultadoAlocacao> janela = MotorAlocacao.alocarJanela(
                List.of(tarefa),
                Map.of(tarefa.getId(), 60),
                dias
        );

        assertThat(janela.get(DIA_D).blocos()).isEmpty();
        assertThat(MotorAlocacao.minutosDoProximoBloco(60, 15)).isNull();
    }

    @Test
    void t4_saldoMenorQuePisoPermiteBlocoAbaixoDe30() {
        // Saldo residual 15 (ex.: 60 estimados − 45 concluídos); abaixo do piso 30.
        Tarefa tarefa = tarefa("sobra", Prioridade.MEDIA, StatusTarefa.PENDENTE, prazoEmDias(0), 60,
                Instant.parse("2026-10-01T10:00:00Z"));
        List<MotorAlocacao.DiaCapacidade> dias = List.of(
                new MotorAlocacao.DiaCapacidade(DIA_D, 120)
        );

        Map<LocalDate, ResultadoAlocacao> janela = MotorAlocacao.alocarJanela(
                List.of(tarefa),
                Map.of(tarefa.getId(), 15),
                dias
        );

        assertThat(janela.get(DIA_D).blocos()).hasSize(1);
        assertThat(janela.get(DIA_D).blocos().get(0).duracaoMinutos()).isEqualTo(15);
        assertThat(MotorAlocacao.minutosDoProximoBloco(15, 120)).isEqualTo(15);
    }

    @Test
    void t3_janelaEmpateCapacidadeMaiorScoreAlocadaOutraNoConflito() {
        Instant criado = Instant.parse("2026-10-01T10:00:00Z");
        Tarefa alta = tarefa("Prioritaria", Prioridade.ALTA, StatusTarefa.PENDENTE, prazoEmDias(0), 60, criado);
        Tarefa baixa = tarefa("Preterida", Prioridade.BAIXA, StatusTarefa.PENDENTE, prazoEmDias(0), 60, criado.plusSeconds(1));
        List<MotorAlocacao.DiaCapacidade> dias = List.of(
                new MotorAlocacao.DiaCapacidade(DIA_D, 60)
        );

        Map<LocalDate, ResultadoAlocacao> janela = MotorAlocacao.alocarJanela(
                List.of(alta, baixa),
                Map.of(alta.getId(), 60, baixa.getId(), 60),
                dias
        );

        ResultadoAlocacao dia = janela.get(DIA_D);
        assertThat(dia.blocos()).hasSize(1);
        assertThat(dia.blocos().get(0).tarefaId()).isEqualTo(alta.getId());
        assertThat(dia.temConflito()).isTrue();
        assertThat(dia.mensagem())
                .contains("Preterida")
                .contains("60")
                .doesNotContain("Prioritaria");
    }

    @Test
    void t2_capacidadeApertadaAlocaComPrazoAntesDaSemPrazo() {
        Instant criado = Instant.parse("2026-10-01T10:00:00Z");
        Tarefa comPrazo = tarefa(
                "ComPrazo", Prioridade.BAIXA, StatusTarefa.PENDENTE, prazoEmDias(1), 60, criado);
        Tarefa semPrazo = tarefa(
                "SemPrazo", Prioridade.ALTA, StatusTarefa.PENDENTE, null, 60, criado.plusSeconds(1));

        ResultadoAlocacao resultado = MotorAlocacao.alocar(List.of(semPrazo, comPrazo), 60, DIA_D);

        assertThat(resultado.blocos()).hasSize(1);
        assertThat(resultado.blocos().get(0).tarefaId()).isEqualTo(comPrazo.getId());
    }

    @Test
    void t3_semPrazoNaoGeraConflitoDePrazoNaJanela() {
        Instant criado = Instant.parse("2026-10-01T10:00:00Z");
        Tarefa semPrazo = tarefa(
                "SemPrazo", Prioridade.ALTA, StatusTarefa.PENDENTE, null, 180, criado);
        List<MotorAlocacao.DiaCapacidade> dias = List.of(
                new MotorAlocacao.DiaCapacidade(DIA_D, 60)
        );

        Map<LocalDate, ResultadoAlocacao> janela = MotorAlocacao.alocarJanela(
                List.of(semPrazo),
                Map.of(semPrazo.getId(), 180),
                dias
        );

        ResultadoAlocacao dia = janela.get(DIA_D);
        assertThat(dia.blocos()).hasSize(1);
        assertThat(dia.temConflito()).isFalse();
        assertThat(dia.mensagem()).isNull();
        assertThat(MotorAlocacao.fimJanela(DIA_D, List.of(semPrazo))).isEqualTo(DIA_D);
    }

    private static Instant prazoEmDias(int diasAposD) {
        return DIA_D.plusDays(diasAposD).atTime(12, 0).atZone(ZONE).toInstant();
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
                "descricao",
                prioridade,
                prazo,
                status,
                duracaoMinutos,
                createdAt,
                createdAt
        );
    }
}
