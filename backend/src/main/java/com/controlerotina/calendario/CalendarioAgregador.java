package com.controlerotina.calendario;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.controlerotina.capacidade.entity.OrigemCapacidade;
import com.controlerotina.historico.PeriodoBRT;
import com.controlerotina.planner.entity.StatusPlano;
import com.controlerotina.shared.exception.IntervaloCapacidadeInvalidoException;
import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.entity.StatusTarefa;

public final class CalendarioAgregador {

    static final int INTERVALO_MAXIMO_DIAS = 31;

    private CalendarioAgregador() {
    }

    public static void validarIntervalo(LocalDate dataDe, LocalDate dataAte) {
        if (dataDe == null || dataAte == null) {
            throw new IntervaloCapacidadeInvalidoException("dataDe e dataAte são obrigatórios.");
        }
        if (dataAte.isBefore(dataDe)) {
            throw new IntervaloCapacidadeInvalidoException("dataAte deve ser maior ou igual a dataDe.");
        }
        if (ChronoUnit.DAYS.between(dataDe, dataAte) + 1 > INTERVALO_MAXIMO_DIAS) {
            throw new IntervaloCapacidadeInvalidoException(
                    "Intervalo máximo permitido é de " + INTERVALO_MAXIMO_DIAS + " dias."
            );
        }
    }

    public static DiaMontado montarDia(
            LocalDate data,
            int capacidadeMinutos,
            OrigemCapacidade origemCapacidade,
            List<PlanoCandidato> planosDoDia,
            List<TarefaPrazoInput> tarefasCandidatas
    ) {
        PlanoCandidato aprovado = selecionarPlanoAprovado(planosDoDia);
        List<BlocoInput> blocos = aprovado != null ? aprovado.blocos() : List.of();
        int minutosAlocados = blocos.stream().mapToInt(BlocoInput::duracaoMinutos).sum();
        boolean sobrecarga = minutosAlocados > capacidadeMinutos;

        List<TarefaPrazoInput> tarefasComPrazo = filtrarTarefasComPrazoNoDia(tarefasCandidatas, data);

        PlanoMontado plano = aprovado == null
                ? null
                : new PlanoMontado(aprovado.id(), aprovado.versao(), aprovado.temConflito(), blocos);

        return new DiaMontado(
                data,
                capacidadeMinutos,
                origemCapacidade,
                minutosAlocados,
                sobrecarga,
                aprovado != null ? StatusPlano.APROVADO : null,
                plano,
                tarefasComPrazo
        );
    }

    static PlanoCandidato selecionarPlanoAprovado(List<PlanoCandidato> planosDoDia) {
        return planosDoDia.stream()
                .filter(p -> p.status() == StatusPlano.APROVADO)
                .max(Comparator.comparingInt(PlanoCandidato::versao))
                .orElse(null);
    }

    static List<TarefaPrazoInput> filtrarTarefasComPrazoNoDia(
            List<TarefaPrazoInput> candidatas,
            LocalDate data
    ) {
        return candidatas.stream()
                .filter(t -> t.deletedAt() == null)
                .filter(t -> t.status() != StatusTarefa.FINALIZADA)
                .filter(t -> t.prazo() != null)
                .filter(t -> diaCivilBrt(t.prazo()).equals(data))
                .sorted(Comparator
                        .comparing(TarefaPrazoInput::prazo)
                        .thenComparing(TarefaPrazoInput::titulo))
                .toList();
    }

    static LocalDate diaCivilBrt(Instant instante) {
        return instante.atZone(PeriodoBRT.ZONE).toLocalDate();
    }

    public record BlocoInput(
            UUID id,
            UUID tarefaId,
            String titulo,
            int duracaoMinutos,
            int ordem,
            Prioridade prioridade
    ) {
    }

    public record PlanoCandidato(
            UUID id,
            StatusPlano status,
            int versao,
            boolean temConflito,
            List<BlocoInput> blocos
    ) {
    }

    public record TarefaPrazoInput(
            UUID id,
            String titulo,
            Instant prazo,
            StatusTarefa status,
            Prioridade prioridade,
            int duracaoMinutos,
            Instant deletedAt
    ) {
    }

    public record PlanoMontado(
            UUID id,
            int versao,
            boolean temConflito,
            List<BlocoInput> blocos
    ) {
    }

    public record DiaMontado(
            LocalDate data,
            int capacidadeMinutos,
            OrigemCapacidade origemCapacidade,
            int minutosAlocados,
            boolean sobrecarga,
            StatusPlano statusPlano,
            PlanoMontado plano,
            List<TarefaPrazoInput> tarefasComPrazo
    ) {
    }
}
