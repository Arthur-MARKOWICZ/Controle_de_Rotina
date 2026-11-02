package com.controlerotina.habito.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.controlerotina.capacidade.entity.DiaSemana;
import com.controlerotina.execucao.entity.StatusExecucao;
import com.controlerotina.execucao.repository.ExecucaoRepository;
import com.controlerotina.habito.entity.Habito;
import com.controlerotina.historico.PeriodoBRT;

@Service
public class HabitoStreakService {

    private final ExecucaoRepository execucaoRepository;
    private final Clock clock;

    public HabitoStreakService(ExecucaoRepository execucaoRepository, Clock clock) {
        this.execucaoRepository = execucaoRepository;
        this.clock = clock;
    }

    /**
     * @param jaCumpridoAntes true se já existia execução CONCLUIDO do hábito em {@code dataPlano}
     *                        antes desta conclusão (idempotência).
     */
    @Transactional
    public void registrarCumprimento(Habito habito, LocalDate dataPlano, boolean jaCumpridoAntes) {
        if (jaCumpridoAntes) {
            return;
        }

        LocalDate anterior = ultimoDiaAgendadoAntes(dataPlano, habito.getDiasSemana());
        int streakAtual;
        if (anterior != null && execucaoRepository.existsCumprimentoHabitoNaData(
                habito.getId(), anterior, StatusExecucao.CONCLUIDO)) {
            streakAtual = habito.getStreakAtual() + 1;
        } else {
            streakAtual = 1;
        }
        int melhor = Math.max(habito.getMelhorStreak(), streakAtual);
        habito.atualizarStreak(streakAtual, melhor, clock.instant());
    }

    /**
     * @return true se o streak foi alterado (precisa persistir).
     */
    @Transactional
    public boolean aplicarQuebraPreguicosa(Habito habito) {
        if (habito.getStreakAtual() <= 0) {
            return false;
        }
        LocalDate hoje = LocalDate.now(clock.withZone(PeriodoBRT.ZONE));
        if (hojeEstaCumprido(habito, hoje)) {
            return false;
        }
        LocalDate anterior = ultimoDiaAgendadoAntes(hoje, habito.getDiasSemana());
        if (anterior == null) {
            return false;
        }
        if (!execucaoRepository.existsCumprimentoHabitoNaData(
                habito.getId(), anterior, StatusExecucao.CONCLUIDO)) {
            habito.atualizarStreak(0, habito.getMelhorStreak(), clock.instant());
            return true;
        }
        return false;
    }

    /**
     * Aplica quebra preguiçosa em lote com uma query de cumprimentos nas datas relevantes.
     *
     * @return hábitos cujo streak mudou (para persistência explícita).
     */
    @Transactional
    public List<Habito> aplicarQuebraPreguicosaEmLote(Collection<Habito> habitos) {
        if (habitos == null || habitos.isEmpty()) {
            return List.of();
        }
        LocalDate hoje = LocalDate.now(clock.withZone(PeriodoBRT.ZONE));
        Set<UUID> habitoIds = new HashSet<>();
        Set<LocalDate> datas = new HashSet<>();
        datas.add(hoje);

        Map<UUID, LocalDate> anteriorPorHabito = new HashMap<>();
        for (Habito habito : habitos) {
            if (habito.getStreakAtual() <= 0) {
                continue;
            }
            habitoIds.add(habito.getId());
            LocalDate anterior = ultimoDiaAgendadoAntes(hoje, habito.getDiasSemana());
            if (anterior != null) {
                anteriorPorHabito.put(habito.getId(), anterior);
                datas.add(anterior);
            }
        }
        if (habitoIds.isEmpty()) {
            return List.of();
        }

        Set<String> cumpridos = carregarCumprimentos(habitoIds, datas);
        List<Habito> alterados = new java.util.ArrayList<>();
        for (Habito habito : habitos) {
            if (habito.getStreakAtual() <= 0) {
                continue;
            }
            if (habito.getDiasSemana().contains(DiaSemana.de(hoje))
                    && cumpridos.contains(chave(habito.getId(), hoje))) {
                continue;
            }
            LocalDate anterior = anteriorPorHabito.get(habito.getId());
            if (anterior == null) {
                continue;
            }
            if (!cumpridos.contains(chave(habito.getId(), anterior))) {
                habito.atualizarStreak(0, habito.getMelhorStreak(), clock.instant());
                alterados.add(habito);
            }
        }
        return List.copyOf(alterados);
    }

    private boolean hojeEstaCumprido(Habito habito, LocalDate hoje) {
        return habito.getDiasSemana().contains(DiaSemana.de(hoje))
                && execucaoRepository.existsCumprimentoHabitoNaData(
                        habito.getId(), hoje, StatusExecucao.CONCLUIDO);
    }

    private Set<String> carregarCumprimentos(Set<UUID> habitoIds, Set<LocalDate> datas) {
        List<Object[]> rows = execucaoRepository.listarCumprimentosHabitoNasDatas(
                habitoIds, datas, StatusExecucao.CONCLUIDO);
        Set<String> cumpridos = new HashSet<>();
        for (Object[] row : rows) {
            cumpridos.add(chave((UUID) row[0], (LocalDate) row[1]));
        }
        return cumpridos;
    }

    private static String chave(UUID habitoId, LocalDate data) {
        return habitoId + "|" + data;
    }

    static LocalDate ultimoDiaAgendadoAntes(LocalDate data, Set<DiaSemana> diasSemana) {
        if (diasSemana == null || diasSemana.isEmpty()) {
            return null;
        }
        LocalDate cursor = data.minusDays(1);
        for (int i = 0; i < 14; i++) {
            if (diasSemana.contains(DiaSemana.de(cursor))) {
                return cursor;
            }
            cursor = cursor.minusDays(1);
        }
        return null;
    }
}
