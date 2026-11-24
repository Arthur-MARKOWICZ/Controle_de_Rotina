package com.controlerotina.objetivo.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.controlerotina.execucao.entity.StatusExecucao;
import com.controlerotina.execucao.repository.CumprimentoHabitoLinha;
import com.controlerotina.execucao.repository.ExecucaoRepository;
import com.controlerotina.execucao.repository.MinutoTarefaLinha;
import com.controlerotina.habito.entity.Habito;
import com.controlerotina.historico.PeriodoBRT;
import com.controlerotina.objetivo.dto.ObjetivoEvolucaoResponse;
import com.controlerotina.objetivo.dto.PontoEvolucaoResponse;
import com.controlerotina.objetivo.entity.Objetivo;
import com.controlerotina.objetivo.repository.ObjetivoRepository;
import com.controlerotina.objetivo.service.RegrasContagemObjetivo.Janela;
import com.controlerotina.shared.exception.ObjetivoNaoEncontradoException;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;
import com.controlerotina.tarefa.repository.TarefaRepository;

/**
 * Série de minutos concluídos acumulados por dia de um objetivo.
 *
 * <p>Usa o mesmo conjunto de itens, as mesmas regras de contagem e os mesmos limites por item do
 * {@link ObjetivoProgressoService}, então o último acumulado é igual a {@code progresso.minutosConcluidos}.
 * É o retrato do conjunto atual: desvincular, pausar ou cancelar um item o tira da curva inteira.
 */
@Service
public class ObjetivoEvolucaoService {

    private final ObjetivoRepository objetivoRepository;
    private final ObjetivoVinculoService objetivoVinculoService;
    private final TarefaRepository tarefaRepository;
    private final ExecucaoRepository execucaoRepository;
    private final Clock clock;

    public ObjetivoEvolucaoService(
            ObjetivoRepository objetivoRepository,
            ObjetivoVinculoService objetivoVinculoService,
            TarefaRepository tarefaRepository,
            ExecucaoRepository execucaoRepository,
            Clock clock
    ) {
        this.objetivoRepository = objetivoRepository;
        this.objetivoVinculoService = objetivoVinculoService;
        this.tarefaRepository = tarefaRepository;
        this.execucaoRepository = execucaoRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ObjetivoEvolucaoResponse obter(UUID usuarioId, UUID objetivoId) {
        Objetivo objetivo = objetivoRepository.findByIdAndUsuarioId(objetivoId, usuarioId)
                .orElseThrow(ObjetivoNaoEncontradoException::new);
        return calcular(objetivo, objetivoVinculoService.resolverConjunto(objetivoId));
    }

    @Transactional(readOnly = true)
    public ObjetivoEvolucaoResponse calcular(Objetivo objetivo, ObjetivoConjunto conjunto) {
        LocalDate hoje = LocalDate.now(clock.withZone(PeriodoBRT.ZONE));
        Map<LocalDate, Integer> porDia = new TreeMap<>();
        int antesDoHistorico = 0;

        antesDoHistorico += somarTarefas(objetivo, conjunto, porDia);
        somarHabitos(objetivo, conjunto, hoje, porDia);

        List<PontoEvolucaoResponse> pontos = new ArrayList<>();
        int acumulado = antesDoHistorico;
        for (Map.Entry<LocalDate, Integer> dia : porDia.entrySet()) {
            if (dia.getValue() <= 0) {
                continue;
            }
            acumulado += dia.getValue();
            pontos.add(new PontoEvolucaoResponse(dia.getKey(), dia.getValue(), acumulado));
        }
        return new ObjetivoEvolucaoResponse(antesDoHistorico, pontos);
    }

    /** Soma as tarefas em {@code porDia} e devolve os minutos sem data (finalizadas antes do registro). */
    private int somarTarefas(Objetivo objetivo, ObjetivoConjunto conjunto, Map<LocalDate, Integer> porDia) {
        List<Tarefa> raizes = conjunto.tarefasRaiz().stream()
                .map(ObjetivoConjunto.TarefaRaiz::tarefa)
                .filter(RegrasContagemObjetivo::contaNoProgresso)
                .toList();
        if (raizes.isEmpty()) {
            return 0;
        }
        UUID usuarioId = objetivo.getUsuarioId();
        Set<UUID> raizIds = raizes.stream().map(Tarefa::getId).collect(Collectors.toSet());

        // Execuções de subtarefa contam na raiz, como no progresso.
        Map<UUID, UUID> raizPorTarefa = new HashMap<>();
        raizIds.forEach(id -> raizPorTarefa.put(id, id));
        for (Tarefa filho : tarefaRepository.findByParentIdInAndDeletedAtIsNull(raizIds)) {
            raizPorTarefa.put(filho.getId(), filho.getParentId());
        }
        Map<UUID, List<MinutoTarefaLinha>> linhasPorRaiz = execucaoRepository
                .listarMinutosConcluidosPorTarefas(new HashSet<>(raizPorTarefa.keySet()), usuarioId, StatusExecucao.CONCLUIDO)
                .stream()
                .collect(Collectors.groupingBy(linha -> raizPorTarefa.get(linha.tarefaId())));

        int semData = 0;
        for (Tarefa raiz : raizes) {
            semData += somarTarefa(raiz, linhasPorRaiz.getOrDefault(raiz.getId(), List.of()), porDia);
        }
        return semData;
    }

    private int somarTarefa(Tarefa raiz, List<MinutoTarefaLinha> linhas, Map<LocalDate, Integer> porDia) {
        int duracao = raiz.getDuracaoMinutos();
        boolean finalizada = raiz.getStatus() == StatusTarefa.FINALIZADA;
        Instant finalizadaEm = raiz.getFinalizadaEm();

        List<EventoTarefa> eventos = new ArrayList<>();
        for (MinutoTarefaLinha linha : linhas) {
            eventos.add(new EventoTarefa(instanteDa(linha), linha.minutos(), false));
        }
        if (finalizada && finalizadaEm != null) {
            eventos.add(new EventoTarefa(finalizadaEm, 0, true));
        }
        // Estável: um saldo no mesmo instante de uma execução vem depois dela.
        eventos.sort(Comparator.comparing(EventoTarefa::quando).thenComparing(EventoTarefa::saldo));

        int acumulado = 0;
        for (EventoTarefa evento : eventos) {
            int minutos = evento.saldo()
                    ? duracao - acumulado
                    : Math.min(evento.minutos(), duracao - acumulado);
            if (minutos <= 0) {
                continue;
            }
            acumulado += minutos;
            porDia.merge(diaBrt(evento.quando()), minutos, Integer::sum);
        }
        // Finalizada sem data: o que faltar para a duração entra "antes do histórico".
        return finalizada && finalizadaEm == null ? duracao - acumulado : 0;
    }

    private void somarHabitos(
            Objetivo objetivo,
            ObjetivoConjunto conjunto,
            LocalDate hoje,
            Map<LocalDate, Integer> porDia
    ) {
        Map<UUID, Janela> janelaPorHabito = new HashMap<>();
        Map<UUID, Habito> habitoPorId = new HashMap<>();
        for (ObjetivoConjunto.HabitoVinculado vinculo : conjunto.habitos()) {
            if (!RegrasContagemObjetivo.contaNoProgresso(vinculo.habito())) {
                continue;
            }
            Janela janela = RegrasContagemObjetivo.janela(objetivo, vinculo.vinculoCreatedAt(), hoje);
            if (janela.vazia()) {
                continue;
            }
            janelaPorHabito.put(vinculo.habito().getId(), janela);
            habitoPorId.put(vinculo.habito().getId(), vinculo.habito());
        }
        if (janelaPorHabito.isEmpty()) {
            return;
        }
        LocalDate minInicio = janelaPorHabito.values().stream().map(Janela::inicio).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate maxFim = janelaPorHabito.values().stream().map(Janela::fim).max(Comparator.naturalOrder()).orElseThrow();
        Map<UUID, List<CumprimentoHabitoLinha>> cumprimentos = execucaoRepository
                .listarCumprimentosHabitosNoIntervalo(
                        objetivo.getUsuarioId(), janelaPorHabito.keySet(), minInicio, maxFim, StatusExecucao.CONCLUIDO)
                .stream()
                .collect(Collectors.groupingBy(CumprimentoHabitoLinha::habitoId));

        for (Map.Entry<UUID, Janela> entrada : janelaPorHabito.entrySet()) {
            Habito habito = habitoPorId.get(entrada.getKey());
            Janela janela = entrada.getValue();
            int total = habito.getDuracaoMinutos() * RegrasContagemObjetivo.diasEsperados(habito, janela);
            if (total <= 0) {
                continue;
            }
            List<CumprimentoHabitoLinha> linhas = new ArrayList<>(cumprimentos.getOrDefault(entrada.getKey(), List.of()));
            linhas.sort(Comparator.comparing(CumprimentoHabitoLinha::data));
            int acumulado = 0;
            for (CumprimentoHabitoLinha linha : linhas) {
                if (!janela.contem(linha.data())) {
                    continue;
                }
                int minutos = Math.min(linha.minutosDedicados(), total - acumulado);
                if (minutos <= 0) {
                    continue;
                }
                acumulado += minutos;
                porDia.merge(linha.data(), minutos, Integer::sum);
            }
        }
    }

    private static Instant instanteDa(MinutoTarefaLinha linha) {
        return linha.concluidoEm() != null
                ? linha.concluidoEm()
                : linha.planoData().atStartOfDay(PeriodoBRT.ZONE).toInstant();
    }

    private static LocalDate diaBrt(Instant instante) {
        return instante.atZone(PeriodoBRT.ZONE).toLocalDate();
    }

    /** Execução de tarefa ({@code saldo} falso) ou o saldo da finalização ({@code saldo} verdadeiro). */
    private record EventoTarefa(Instant quando, int minutos, boolean saldo) {
    }
}
