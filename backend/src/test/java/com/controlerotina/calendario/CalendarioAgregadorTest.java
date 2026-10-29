package com.controlerotina.calendario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.controlerotina.capacidade.entity.OrigemCapacidade;
import com.controlerotina.planner.entity.StatusPlano;
import com.controlerotina.shared.exception.IntervaloCapacidadeInvalidoException;
import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.entity.StatusTarefa;

class CalendarioAgregadorTest {

    private static final LocalDate DIA = LocalDate.of(2026, 10, 27);
    private static final UUID PLANO_APROVADO_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLANO_RASCUNHO_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BLOCO_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID TAREFA_BLOCO_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Test
    void t1_deveMontarDiaComPlanoAprovadoEIgnorarRascunho() {
        CalendarioAgregador.PlanoCandidato aprovado = new CalendarioAgregador.PlanoCandidato(
                PLANO_APROVADO_ID,
                StatusPlano.APROVADO,
                2,
                false,
                List.of(new CalendarioAgregador.BlocoInput(
                        BLOCO_ID,
                        TAREFA_BLOCO_ID,
                        "Estudar Spring",
                        60,
                        1,
                        Prioridade.ALTA
                ))
        );
        CalendarioAgregador.PlanoCandidato rascunho = new CalendarioAgregador.PlanoCandidato(
                PLANO_RASCUNHO_ID,
                StatusPlano.RASCUNHO,
                3,
                false,
                List.of(new CalendarioAgregador.BlocoInput(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "Não deve aparecer",
                        90,
                        1,
                        Prioridade.MEDIA
                ))
        );

        CalendarioAgregador.DiaMontado dia = CalendarioAgregador.montarDia(
                DIA,
                240,
                OrigemCapacidade.SEMANAL,
                List.of(rascunho, aprovado),
                List.of()
        );

        assertThat(dia.statusPlano()).isEqualTo(StatusPlano.APROVADO);
        assertThat(dia.plano()).isNotNull();
        assertThat(dia.plano().id()).isEqualTo(PLANO_APROVADO_ID);
        assertThat(dia.plano().versao()).isEqualTo(2);
        assertThat(dia.plano().blocos()).extracting(CalendarioAgregador.BlocoInput::titulo)
                .containsExactly("Estudar Spring");
        assertThat(dia.minutosAlocados()).isEqualTo(60);
        assertThat(dia.sobrecarga()).isFalse();
    }

    @Test
    void t2_deveMarcarSobrecargaQuandoAlocadoExcedeCapacidade() {
        CalendarioAgregador.PlanoCandidato aprovado = planoComBlocos(180);

        CalendarioAgregador.DiaMontado comSobrecarga = CalendarioAgregador.montarDia(
                DIA, 120, OrigemCapacidade.OVERRIDE, List.of(aprovado), List.of());
        assertThat(comSobrecarga.sobrecarga()).isTrue();
        assertThat(comSobrecarga.minutosAlocados()).isEqualTo(180);

        CalendarioAgregador.DiaMontado noLimite = CalendarioAgregador.montarDia(
                DIA, 180, OrigemCapacidade.OVERRIDE, List.of(aprovado), List.of());
        assertThat(noLimite.sobrecarga()).isFalse();

        CalendarioAgregador.DiaMontado abaixo = CalendarioAgregador.montarDia(
                DIA, 240, OrigemCapacidade.SEMANAL, List.of(aprovado), List.of());
        assertThat(abaixo.sobrecarga()).isFalse();
    }

    @Test
    void t3_deveFiltrarTarefasComPrazoNoDiaCivilBrt() {
        UUID noDia = UUID.fromString("55555555-5555-5555-5555-555555555555");
        UUID outroDia = UUID.fromString("66666666-6666-6666-6666-666666666666");
        UUID finalizada = UUID.fromString("77777777-7777-7777-7777-777777777777");
        UUID deletada = UUID.fromString("88888888-8888-8888-8888-888888888888");
        UUID semPrazo = UUID.fromString("99999999-9999-9999-9999-999999999999");

        // 2026-10-27 23:30 BRT = 2026-10-28 02:30Z — ainda é dia 27 em BRT
        Instant prazoNoDia = Instant.parse("2026-10-28T02:30:00Z");
        Instant prazoOutroDia = Instant.parse("2026-10-28T03:00:00Z"); // 00:00 BRT do dia 28

        List<CalendarioAgregador.TarefaPrazoInput> candidatas = List.of(
                new CalendarioAgregador.TarefaPrazoInput(
                        noDia, "Entregar relatório", prazoNoDia, StatusTarefa.PENDENTE,
                        Prioridade.ALTA, 120, null),
                new CalendarioAgregador.TarefaPrazoInput(
                        outroDia, "Outro dia", prazoOutroDia, StatusTarefa.PENDENTE,
                        Prioridade.MEDIA, 60, null),
                new CalendarioAgregador.TarefaPrazoInput(
                        finalizada, "Já finalizada", prazoNoDia, StatusTarefa.FINALIZADA,
                        Prioridade.BAIXA, 30, null),
                new CalendarioAgregador.TarefaPrazoInput(
                        deletada, "Deletada", prazoNoDia, StatusTarefa.PENDENTE,
                        Prioridade.MEDIA, 30, Instant.parse("2026-10-26T12:00:00Z")),
                new CalendarioAgregador.TarefaPrazoInput(
                        semPrazo, "Sem prazo", null, StatusTarefa.PENDENTE,
                        Prioridade.MEDIA, 30, null)
        );

        CalendarioAgregador.DiaMontado dia = CalendarioAgregador.montarDia(
                DIA, 0, OrigemCapacidade.VAZIO, List.of(), candidatas);

        assertThat(dia.tarefasComPrazo()).extracting(CalendarioAgregador.TarefaPrazoInput::id)
                .containsExactly(noDia);
        assertThat(dia.tarefasComPrazo()).extracting(CalendarioAgregador.TarefaPrazoInput::titulo)
                .containsExactly("Entregar relatório");
    }

    @Test
    void t4_deveRejeitarIntervaloInvertidoOuMaiorQue31Dias() {
        assertThatThrownBy(() -> CalendarioAgregador.validarIntervalo(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 10, 1)))
                .isInstanceOf(IntervaloCapacidadeInvalidoException.class);

        assertThatThrownBy(() -> CalendarioAgregador.validarIntervalo(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1)))
                .isInstanceOf(IntervaloCapacidadeInvalidoException.class);

        assertThatThrownBy(() -> CalendarioAgregador.validarIntervalo(null, DIA))
                .isInstanceOf(IntervaloCapacidadeInvalidoException.class);

        CalendarioAgregador.validarIntervalo(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
    }

    private static CalendarioAgregador.PlanoCandidato planoComBlocos(int duracaoMinutos) {
        return new CalendarioAgregador.PlanoCandidato(
                PLANO_APROVADO_ID,
                StatusPlano.APROVADO,
                1,
                false,
                List.of(new CalendarioAgregador.BlocoInput(
                        BLOCO_ID,
                        TAREFA_BLOCO_ID,
                        "Bloco",
                        duracaoMinutos,
                        1,
                        Prioridade.MEDIA
                ))
        );
    }
}
