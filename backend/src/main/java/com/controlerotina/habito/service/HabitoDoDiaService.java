package com.controlerotina.habito.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.controlerotina.capacidade.entity.DiaSemana;
import com.controlerotina.execucao.entity.Execucao;
import com.controlerotina.execucao.entity.StatusExecucao;
import com.controlerotina.execucao.repository.ExecucaoRepository;
import com.controlerotina.habito.dto.HabitoDoDiaResponse;
import com.controlerotina.habito.dto.SlotExecucaoHabitoResponse;
import com.controlerotina.habito.entity.Habito;
import com.controlerotina.habito.entity.StatusHabito;
import com.controlerotina.habito.repository.HabitoRepository;
import com.controlerotina.historico.PeriodoBRT;
import com.controlerotina.planner.entity.BlocoPlano;
import com.controlerotina.planner.entity.Plano;
import com.controlerotina.planner.entity.StatusPlano;
import com.controlerotina.planner.repository.BlocoPlanoRepository;
import com.controlerotina.planner.repository.PlanoRepository;

@Service
public class HabitoDoDiaService {

    private final HabitoRepository habitoRepository;
    private final PlanoRepository planoRepository;
    private final BlocoPlanoRepository blocoPlanoRepository;
    private final ExecucaoRepository execucaoRepository;
    private final HabitoStreakService habitoStreakService;
    private final Clock clock;

    public HabitoDoDiaService(
            HabitoRepository habitoRepository,
            PlanoRepository planoRepository,
            BlocoPlanoRepository blocoPlanoRepository,
            ExecucaoRepository execucaoRepository,
            HabitoStreakService habitoStreakService,
            Clock clock
    ) {
        this.habitoRepository = habitoRepository;
        this.planoRepository = planoRepository;
        this.blocoPlanoRepository = blocoPlanoRepository;
        this.execucaoRepository = execucaoRepository;
        this.habitoStreakService = habitoStreakService;
        this.clock = clock;
    }

    @Transactional
    public List<HabitoDoDiaResponse> listarDoDia(UUID usuarioId, LocalDate data) {
        LocalDate dia = data != null ? data : LocalDate.now(clock.withZone(PeriodoBRT.ZONE));
        return listarNoIntervalo(usuarioId, dia, dia).getOrDefault(dia, List.of());
    }

    @Transactional
    public Map<LocalDate, List<HabitoDoDiaResponse>> listarNoIntervalo(
            UUID usuarioId,
            LocalDate dataDe,
            LocalDate dataAte
    ) {
        List<Habito> ativos = habitoRepository.findByUsuarioIdAndStatusAndDeletedAtIsNull(
                usuarioId, StatusHabito.ATIVO);
        List<Habito> alterados = habitoStreakService.aplicarQuebraPreguicosaEmLote(ativos);
        if (!alterados.isEmpty()) {
            habitoRepository.saveAll(alterados);
        }

        List<Plano> aprovados = planoRepository
                .findByUsuarioIdAndStatusAndDataBetweenOrderByDataAscVersaoDesc(
                        usuarioId, StatusPlano.APROVADO, dataDe, dataAte);
        List<Plano> avulsos = planoRepository
                .findByUsuarioIdAndStatusAndDataBetweenOrderByDataAscVersaoDesc(
                        usuarioId, StatusPlano.HABITO_AVULSO, dataDe, dataAte);

        Map<LocalDate, Plano> aprovadoPorDia = new HashMap<>();
        for (Plano plano : aprovados) {
            aprovadoPorDia.putIfAbsent(plano.getData(), plano);
        }
        Map<LocalDate, Plano> avulsoPorDia = new HashMap<>();
        for (Plano plano : avulsos) {
            avulsoPorDia.putIfAbsent(plano.getData(), plano);
        }

        List<UUID> planoIds = new ArrayList<>();
        aprovadoPorDia.values().forEach(p -> planoIds.add(p.getId()));
        avulsoPorDia.values().forEach(p -> planoIds.add(p.getId()));

        Map<UUID, List<BlocoPlano>> blocosPorPlano = planoIds.isEmpty()
                ? Map.of()
                : blocoPlanoRepository.findByPlanoIdInOrderByOrdemAsc(planoIds).stream()
                        .collect(Collectors.groupingBy(BlocoPlano::getPlanoId));

        List<UUID> blocoIds = blocosPorPlano.values().stream()
                .flatMap(List::stream)
                .map(BlocoPlano::getId)
                .toList();
        Map<UUID, Execucao> execucaoPorBloco = blocoIds.isEmpty()
                ? Map.of()
                : execucaoRepository.findByUsuarioIdAndBlocoPlanoIdIn(usuarioId, blocoIds).stream()
                        .collect(Collectors.toMap(Execucao::getBlocoPlanoId, Function.identity()));

        Set<String> cumpridos = new HashSet<>();
        for (Map.Entry<UUID, List<BlocoPlano>> entry : blocosPorPlano.entrySet()) {
            UUID planoId = entry.getKey();
            LocalDate dataPlano = dataDoPlano(aprovadoPorDia, avulsoPorDia, planoId);
            if (dataPlano == null) {
                continue;
            }
            for (BlocoPlano bloco : entry.getValue()) {
                if (bloco.getHabitoId() == null) {
                    continue;
                }
                Execucao execucao = execucaoPorBloco.get(bloco.getId());
                if (execucao != null && execucao.getStatus() == StatusExecucao.CONCLUIDO) {
                    cumpridos.add(chaveCumprimento(bloco.getHabitoId(), dataPlano));
                }
            }
        }

        Map<LocalDate, List<HabitoDoDiaResponse>> porDia = new HashMap<>();
        for (LocalDate dia = dataDe; !dia.isAfter(dataAte); dia = dia.plusDays(1)) {
            DiaSemana weekday = DiaSemana.de(dia);
            Plano aprovado = aprovadoPorDia.get(dia);
            Plano avulso = avulsoPorDia.get(dia);
            Map<UUID, BlocoPlano> blocoAprovadoPorHabito = indexarBlocosHabito(
                    aprovado == null ? List.of() : blocosPorPlano.getOrDefault(aprovado.getId(), List.of()));
            Map<UUID, BlocoPlano> blocoAvulsoPorHabito = indexarBlocosHabito(
                    avulso == null ? List.of() : blocosPorPlano.getOrDefault(avulso.getId(), List.of()));

            LocalDate diaFinal = dia;
            List<HabitoDoDiaResponse> itens = ativos.stream()
                    .filter(h -> h.getDiasSemana().contains(weekday))
                    .sorted(Comparator
                            .comparing(Habito::getHorarioPreferencial, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(Habito::getTitulo))
                    .map(h -> mapear(h, diaFinal, blocoAprovadoPorHabito, blocoAvulsoPorHabito, execucaoPorBloco, cumpridos))
                    .toList();
            porDia.put(dia, itens);
        }
        return porDia;
    }

    private static HabitoDoDiaResponse mapear(
            Habito habito,
            LocalDate dia,
            Map<UUID, BlocoPlano> blocoAprovadoPorHabito,
            Map<UUID, BlocoPlano> blocoAvulsoPorHabito,
            Map<UUID, Execucao> execucaoPorBloco,
            Set<String> cumpridos
    ) {
        BlocoPlano noPlano = blocoAprovadoPorHabito.get(habito.getId());
        BlocoPlano avulso = blocoAvulsoPorHabito.get(habito.getId());
        SlotExecucaoHabitoResponse execucaoNoPlano = noPlano == null
                ? null
                : SlotExecucaoHabitoResponse.de(noPlano, execucaoPorBloco.get(noPlano.getId()));
        SlotExecucaoHabitoResponse execucaoAvulsa = avulso == null
                ? null
                : SlotExecucaoHabitoResponse.de(avulso, execucaoPorBloco.get(avulso.getId()));

        return new HabitoDoDiaResponse(
                habito.getId(),
                habito.getTitulo(),
                habito.getDuracaoMinutos(),
                habito.getHorarioPreferencial(),
                habito.getStreakAtual(),
                cumpridos.contains(chaveCumprimento(habito.getId(), dia)),
                noPlano != null,
                execucaoAvulsa,
                execucaoNoPlano
        );
    }

    private static Map<UUID, BlocoPlano> indexarBlocosHabito(List<BlocoPlano> blocos) {
        Map<UUID, BlocoPlano> map = new HashMap<>();
        for (BlocoPlano bloco : blocos) {
            if (bloco.getHabitoId() != null) {
                map.putIfAbsent(bloco.getHabitoId(), bloco);
            }
        }
        return map;
    }

    private static LocalDate dataDoPlano(
            Map<LocalDate, Plano> aprovados,
            Map<LocalDate, Plano> avulsos,
            UUID planoId
    ) {
        for (Plano plano : aprovados.values()) {
            if (Objects.equals(plano.getId(), planoId)) {
                return plano.getData();
            }
        }
        for (Plano plano : avulsos.values()) {
            if (Objects.equals(plano.getId(), planoId)) {
                return plano.getData();
            }
        }
        return null;
    }

    private static String chaveCumprimento(UUID habitoId, LocalDate dia) {
        return habitoId + "|" + dia;
    }
}
