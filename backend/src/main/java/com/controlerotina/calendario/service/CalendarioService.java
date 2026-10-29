package com.controlerotina.calendario.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.controlerotina.calendario.CalendarioAgregador;
import com.controlerotina.calendario.dto.CalendarioDiaResponse;
import com.controlerotina.calendario.dto.CalendarioResponse;
import com.controlerotina.calendario.dto.PeriodoCalendarioResponse;
import com.controlerotina.capacidade.dto.CapacidadeResolvidaResponse;
import com.controlerotina.capacidade.entity.OrigemCapacidade;
import com.controlerotina.capacidade.service.CapacidadeResolvidaService;
import com.controlerotina.historico.PeriodoBRT;
import com.controlerotina.planner.entity.BlocoPlano;
import com.controlerotina.planner.entity.Plano;
import com.controlerotina.planner.entity.StatusPlano;
import com.controlerotina.planner.repository.BlocoPlanoRepository;
import com.controlerotina.planner.repository.PlanoRepository;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;
import com.controlerotina.tarefa.repository.TarefaRepository;

@Service
public class CalendarioService {

    private final CapacidadeResolvidaService capacidadeResolvidaService;
    private final PlanoRepository planoRepository;
    private final BlocoPlanoRepository blocoPlanoRepository;
    private final TarefaRepository tarefaRepository;

    public CalendarioService(
            CapacidadeResolvidaService capacidadeResolvidaService,
            PlanoRepository planoRepository,
            BlocoPlanoRepository blocoPlanoRepository,
            TarefaRepository tarefaRepository
    ) {
        this.capacidadeResolvidaService = capacidadeResolvidaService;
        this.planoRepository = planoRepository;
        this.blocoPlanoRepository = blocoPlanoRepository;
        this.tarefaRepository = tarefaRepository;
    }

    @Transactional(readOnly = true)
    public CalendarioResponse consultar(UUID usuarioId, LocalDate dataDe, LocalDate dataAte) {
        CalendarioAgregador.validarIntervalo(dataDe, dataAte);

        Map<LocalDate, CapacidadeResolvidaResponse> capacidades = capacidadeResolvidaService
                .resolver(usuarioId, dataDe, dataAte)
                .stream()
                .collect(Collectors.toMap(CapacidadeResolvidaResponse::data, Function.identity()));

        List<Plano> planosAprovados = planoRepository
                .findByUsuarioIdAndStatusAndDataBetweenOrderByDataAscVersaoDesc(
                        usuarioId, StatusPlano.APROVADO, dataDe, dataAte);

        Map<LocalDate, Plano> planoPorDia = new HashMap<>();
        for (Plano plano : planosAprovados) {
            planoPorDia.putIfAbsent(plano.getData(), plano);
        }

        Map<UUID, List<BlocoPlano>> blocosPorPlano = carregarBlocos(planoPorDia.values().stream()
                .map(Plano::getId)
                .toList());

        Map<UUID, Tarefa> tarefasDeBlocos = carregarTarefas(
                blocosPorPlano.values().stream()
                        .flatMap(List::stream)
                        .map(BlocoPlano::getTarefaId)
                        .distinct()
                        .toList()
        );

        var inicioPeriodo = dataDe.atStartOfDay(PeriodoBRT.ZONE).toInstant();
        var fimPeriodoExclusivo = dataAte.plusDays(1).atStartOfDay(PeriodoBRT.ZONE).toInstant();
        List<Tarefa> tarefasComPrazo = tarefaRepository.findAtivasComPrazoNoPeriodo(
                usuarioId,
                StatusTarefa.FINALIZADA,
                inicioPeriodo,
                fimPeriodoExclusivo
        );

        List<CalendarioAgregador.TarefaPrazoInput> todasTarefas = tarefasComPrazo.stream()
                .map(t -> new CalendarioAgregador.TarefaPrazoInput(
                        t.getId(),
                        t.getTitulo(),
                        t.getPrazo(),
                        t.getStatus(),
                        t.getPrioridade(),
                        t.getDuracaoMinutos(),
                        t.getDeletedAt()
                ))
                .toList();

        List<CalendarioDiaResponse> dias = new ArrayList<>();
        for (LocalDate data = dataDe; !data.isAfter(dataAte); data = data.plusDays(1)) {
            CapacidadeResolvidaResponse capacidade = capacidades.get(data);
            int capacidadeMinutos = capacidade == null || capacidade.horas() == null
                    ? 0
                    : capacidade.horas() * 60;
            OrigemCapacidade origem = capacidade == null ? OrigemCapacidade.VAZIO : capacidade.origem();

            Plano plano = planoPorDia.get(data);
            List<CalendarioAgregador.PlanoCandidato> candidatos = List.of();
            if (plano != null) {
                List<BlocoPlano> blocos = blocosPorPlano.getOrDefault(plano.getId(), List.of());
                List<CalendarioAgregador.BlocoInput> blocosInput = blocos.stream()
                        .map(bloco -> {
                            Tarefa tarefa = tarefasDeBlocos.get(bloco.getTarefaId());
                            String titulo = tarefa != null ? tarefa.getTitulo() : "";
                            var prioridade = tarefa != null ? tarefa.getPrioridade() : null;
                            return new CalendarioAgregador.BlocoInput(
                                    bloco.getId(),
                                    bloco.getTarefaId(),
                                    titulo,
                                    bloco.getDuracaoMinutos(),
                                    bloco.getOrdem(),
                                    prioridade
                            );
                        })
                        .toList();
                candidatos = List.of(new CalendarioAgregador.PlanoCandidato(
                        plano.getId(),
                        plano.getStatus(),
                        plano.getVersao(),
                        plano.isTemConflito(),
                        blocosInput
                ));
            }

            CalendarioAgregador.DiaMontado montado = CalendarioAgregador.montarDia(
                    data,
                    capacidadeMinutos,
                    origem,
                    candidatos,
                    todasTarefas
            );
            dias.add(CalendarioDiaResponse.from(montado));
        }

        return new CalendarioResponse(new PeriodoCalendarioResponse(dataDe, dataAte), dias);
    }

    private Map<UUID, List<BlocoPlano>> carregarBlocos(List<UUID> planoIds) {
        if (planoIds.isEmpty()) {
            return Map.of();
        }
        return blocoPlanoRepository.findByPlanoIdInOrderByOrdemAsc(planoIds).stream()
                .collect(Collectors.groupingBy(BlocoPlano::getPlanoId));
    }

    private Map<UUID, Tarefa> carregarTarefas(List<UUID> tarefaIds) {
        if (tarefaIds.isEmpty()) {
            return Map.of();
        }
        return tarefaRepository.findAllById(tarefaIds).stream()
                .collect(Collectors.toMap(Tarefa::getId, Function.identity()));
    }
}
