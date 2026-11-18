package com.controlerotina.objetivo.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.controlerotina.capacidade.entity.DiaSemana;
import com.controlerotina.execucao.entity.StatusExecucao;
import com.controlerotina.execucao.repository.CumprimentoHabitoLinha;
import com.controlerotina.execucao.repository.ExecucaoRepository;
import com.controlerotina.habito.entity.Habito;
import com.controlerotina.habito.entity.StatusHabito;
import com.controlerotina.historico.PeriodoBRT;
import com.controlerotina.objetivo.dto.ObjetivoProgressoResponse;
import com.controlerotina.objetivo.entity.Objetivo;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;
import com.controlerotina.tarefa.repository.TarefaRepository;

@Service
public class ObjetivoProgressoService {

    private final TarefaRepository tarefaRepository;
    private final ExecucaoRepository execucaoRepository;
    private final Clock clock;

    public ObjetivoProgressoService(
            TarefaRepository tarefaRepository,
            ExecucaoRepository execucaoRepository,
            Clock clock
    ) {
        this.tarefaRepository = tarefaRepository;
        this.execucaoRepository = execucaoRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public @Nullable ObjetivoProgressoResponse calcular(Objetivo objetivo, ObjetivoConjunto conjunto) {
        return calcularLote(List.of(objetivo), Map.of(objetivo.getId(), conjunto)).get(objetivo.getId());
    }

    @Transactional(readOnly = true)
    public Map<UUID, ObjetivoProgressoResponse> calcularLote(
            Collection<Objetivo> objetivos,
            Map<UUID, ObjetivoConjunto> conjuntos
    ) {
        if (objetivos == null || objetivos.isEmpty()) {
            return Map.of();
        }
        List<Objetivo> lista = objetivos.stream().filter(objetivo -> objetivo != null).toList();
        if (lista.isEmpty()) {
            return Map.of();
        }
        UUID usuarioId = lista.get(0).getUsuarioId();
        LocalDate hoje = LocalDate.now(clock.withZone(PeriodoBRT.ZONE));

        Set<UUID> idsParciais = new HashSet<>();
        Set<UUID> habitoIdsAtivos = new HashSet<>();
        List<Janela> janelas = new ArrayList<>();

        for (Objetivo objetivo : lista) {
            ObjetivoConjunto conjunto = conjuntos.getOrDefault(objetivo.getId(), ObjetivoConjunto.vazio());
            for (ObjetivoConjunto.TarefaRaiz item : conjunto.tarefasRaiz()) {
                Tarefa tarefa = item.tarefa();
                if (!contaNoProgresso(tarefa) || tarefa.getStatus() == StatusTarefa.FINALIZADA) {
                    continue;
                }
                idsParciais.add(tarefa.getId());
            }
            for (ObjetivoConjunto.HabitoVinculado vinculo : conjunto.habitos()) {
                if (!contaNoProgresso(vinculo.habito())) {
                    continue;
                }
                habitoIdsAtivos.add(vinculo.habito().getId());
                Janela janela = janela(objetivo, vinculo.vinculoCreatedAt(), hoje);
                if (!janela.inicio().isAfter(janela.fim())) {
                    janelas.add(janela);
                }
            }
        }

        Map<UUID, Integer> feitoPorTarefa = carregarFeitoTarefas(usuarioId, idsParciais);
        Map<UUID, List<CumprimentoHabitoLinha>> cumprimentosPorHabito = carregarCumprimentos(
                usuarioId, habitoIdsAtivos, janelas);

        Map<UUID, ObjetivoProgressoResponse> resultado = new HashMap<>();
        for (Objetivo objetivo : lista) {
            resultado.put(objetivo.getId(), somar(
                    objetivo,
                    conjuntos.getOrDefault(objetivo.getId(), ObjetivoConjunto.vazio()),
                    hoje,
                    feitoPorTarefa,
                    cumprimentosPorHabito
            ));
        }
        return resultado;
    }

    private @Nullable ObjetivoProgressoResponse somar(
            Objetivo objetivo,
            ObjetivoConjunto conjunto,
            LocalDate hoje,
            Map<UUID, Integer> feitoPorTarefa,
            Map<UUID, List<CumprimentoHabitoLinha>> cumprimentosPorHabito
    ) {
        int minutosTotais = 0;
        int minutosConcluidos = 0;

        for (ObjetivoConjunto.TarefaRaiz item : conjunto.tarefasRaiz()) {
            Tarefa tarefa = item.tarefa();
            if (!contaNoProgresso(tarefa)) {
                continue;
            }
            int duracao = tarefa.getDuracaoMinutos();
            minutosTotais += duracao;
            if (tarefa.getStatus() == StatusTarefa.FINALIZADA) {
                minutosConcluidos += duracao;
            } else {
                minutosConcluidos += Math.min(duracao, feitoPorTarefa.getOrDefault(tarefa.getId(), 0));
            }
        }

        for (ObjetivoConjunto.HabitoVinculado vinculo : conjunto.habitos()) {
            Habito habito = vinculo.habito();
            if (!contaNoProgresso(habito)) {
                continue;
            }
            Janela janela = janela(objetivo, vinculo.vinculoCreatedAt(), hoje);
            int diasEsperados = diasEsperados(habito, janela);
            int totalHabito = habito.getDuracaoMinutos() * diasEsperados;
            if (totalHabito <= 0) {
                continue;
            }
            int feitoHabito = 0;
            for (CumprimentoHabitoLinha linha : cumprimentosPorHabito.getOrDefault(habito.getId(), List.of())) {
                if (!linha.data().isBefore(janela.inicio()) && !linha.data().isAfter(janela.fim())) {
                    feitoHabito += linha.minutosDedicados();
                }
            }
            minutosTotais += totalHabito;
            minutosConcluidos += Math.min(totalHabito, feitoHabito);
        }

        if (minutosTotais <= 0) {
            return null;
        }
        int percentual = Math.min(100, (int) Math.floor(100.0 * minutosConcluidos / minutosTotais));
        return new ObjetivoProgressoResponse(percentual, minutosConcluidos, minutosTotais);
    }

    private Map<UUID, Integer> carregarFeitoTarefas(UUID usuarioId, Set<UUID> idsParciais) {
        if (idsParciais.isEmpty()) {
            return Map.of();
        }
        List<Tarefa> filhos = tarefaRepository.findByParentIdInAndDeletedAtIsNull(idsParciais);
        Set<UUID> idsConsulta = new HashSet<>(idsParciais);
        Map<UUID, UUID> paiPorFilho = new HashMap<>();
        for (Tarefa filho : filhos) {
            idsConsulta.add(filho.getId());
            paiPorFilho.put(filho.getId(), filho.getParentId());
        }
        Map<UUID, Integer> feitoPorRaiz = new HashMap<>();
        for (Object[] linha : execucaoRepository.somarDuracaoMinutosPorTarefasEStatus(
                idsConsulta, usuarioId, StatusExecucao.CONCLUIDO)) {
            UUID tarefaId = (UUID) linha[0];
            int minutos = ((Number) linha[1]).intValue();
            UUID raizId = idsParciais.contains(tarefaId) ? tarefaId : paiPorFilho.get(tarefaId);
            if (raizId != null) {
                feitoPorRaiz.merge(raizId, minutos, Integer::sum);
            }
        }
        return feitoPorRaiz;
    }

    private Map<UUID, List<CumprimentoHabitoLinha>> carregarCumprimentos(
            UUID usuarioId,
            Set<UUID> habitoIds,
            List<Janela> janelas
    ) {
        if (habitoIds.isEmpty() || janelas.isEmpty()) {
            return Map.of();
        }
        LocalDate minInicio = janelas.get(0).inicio();
        LocalDate maxFim = janelas.get(0).fim();
        for (Janela janela : janelas) {
            if (janela.inicio().isBefore(minInicio)) {
                minInicio = janela.inicio();
            }
            if (janela.fim().isAfter(maxFim)) {
                maxFim = janela.fim();
            }
        }
        return execucaoRepository.listarCumprimentosHabitosNoIntervalo(
                        usuarioId, habitoIds, minInicio, maxFim, StatusExecucao.CONCLUIDO)
                .stream()
                .collect(Collectors.groupingBy(CumprimentoHabitoLinha::habitoId));
    }

    private static boolean contaNoProgresso(Tarefa tarefa) {
        return tarefa.getDeletedAt() == null && tarefa.getStatus() != StatusTarefa.CANCELADA;
    }

    private static boolean contaNoProgresso(Habito habito) {
        return habito.getDeletedAt() == null && habito.getStatus() == StatusHabito.ATIVO;
    }

    private static Janela janela(Objetivo objetivo, Instant vinculoCreatedAt, LocalDate hoje) {
        LocalDate inicio = vinculoCreatedAt.atZone(PeriodoBRT.ZONE).toLocalDate();
        LocalDate fim = hoje;
        if (objetivo.getPrazo() != null && objetivo.getPrazo().isBefore(hoje)) {
            fim = objetivo.getPrazo();
        }
        LocalDate freeze = null;
        if (objetivo.getConcluidoEm() != null) {
            freeze = objetivo.getConcluidoEm().atZone(PeriodoBRT.ZONE).toLocalDate();
        }
        if (objetivo.getArquivadoEm() != null) {
            LocalDate arquivado = objetivo.getArquivadoEm().atZone(PeriodoBRT.ZONE).toLocalDate();
            freeze = freeze == null || arquivado.isBefore(freeze) ? arquivado : freeze;
        }
        if (freeze != null && freeze.isBefore(fim)) {
            fim = freeze;
        }
        return new Janela(inicio, fim);
    }

    private static int diasEsperados(Habito habito, Janela janela) {
        if (janela.inicio().isAfter(janela.fim())) {
            return 0;
        }
        int dias = 0;
        for (LocalDate dia = janela.inicio(); !dia.isAfter(janela.fim()); dia = dia.plusDays(1)) {
            if (habito.getDiasSemana().contains(DiaSemana.de(dia))) {
                dias++;
            }
        }
        return dias;
    }

    private record Janela(LocalDate inicio, LocalDate fim) {
    }
}
