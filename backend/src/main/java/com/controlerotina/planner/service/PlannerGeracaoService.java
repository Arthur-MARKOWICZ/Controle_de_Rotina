package com.controlerotina.planner.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.controlerotina.capacidade.dto.CapacidadeResolvidaResponse;
import com.controlerotina.capacidade.service.CapacidadeResolvidaService;
import com.controlerotina.planner.entity.BlocoPlano;
import com.controlerotina.planner.entity.Plano;
import com.controlerotina.planner.entity.StatusPlano;
import com.controlerotina.planner.motor.BlocoAlocado;
import com.controlerotina.planner.motor.MotorAlocacao;
import com.controlerotina.planner.motor.ResultadoAlocacao;
import com.controlerotina.planner.repository.BlocoPlanoRepository;
import com.controlerotina.planner.repository.PlanoRepository;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;
import com.controlerotina.tarefa.repository.TarefaRepository;

@Service
public class PlannerGeracaoService {

    private static final List<StatusTarefa> STATUS_ELEGIVEIS = List.of(
            StatusTarefa.PENDENTE,
            StatusTarefa.EM_PROGRESSO,
            StatusTarefa.ATRASADA
    );

    private final CapacidadeResolvidaService capacidadeResolvidaService;
    private final TarefaRepository tarefaRepository;
    private final PlanoRepository planoRepository;
    private final BlocoPlanoRepository blocoPlanoRepository;
    private final Clock clock;

    public PlannerGeracaoService(
            CapacidadeResolvidaService capacidadeResolvidaService,
            TarefaRepository tarefaRepository,
            PlanoRepository planoRepository,
            BlocoPlanoRepository blocoPlanoRepository,
            Clock clock
    ) {
        this.capacidadeResolvidaService = capacidadeResolvidaService;
        this.tarefaRepository = tarefaRepository;
        this.planoRepository = planoRepository;
        this.blocoPlanoRepository = blocoPlanoRepository;
        this.clock = clock;
    }

    @Transactional
    public Plano gerar(UUID usuarioId, LocalDate data) {
        CapacidadeResolvidaResponse capacidade = capacidadeResolvidaService
                .resolver(usuarioId, data, data)
                .getFirst();
        int capacidadeMinutos = MotorAlocacao.capacidadeMinutos(capacidade.horas());

        List<Tarefa> elegiveis = tarefaRepository.findByUsuarioIdAndDeletedAtIsNullAndStatusIn(
                usuarioId,
                STATUS_ELEGIVEIS
        );

        ResultadoAlocacao alocacao = MotorAlocacao.alocar(elegiveis, capacidadeMinutos, data);

        Instant agora = clock.instant();
        int proximaVersao = planoRepository.findMaxVersaoByUsuarioIdAndData(usuarioId, data) + 1;

        Plano plano = planoRepository.save(new Plano(
                UUID.randomUUID(),
                usuarioId,
                data,
                proximaVersao,
                StatusPlano.RASCUNHO,
                alocacao.temConflito(),
                alocacao.mensagem(),
                agora,
                agora,
                agora
        ));

        for (BlocoAlocado bloco : alocacao.blocos()) {
            blocoPlanoRepository.save(new BlocoPlano(
                    UUID.randomUUID(),
                    plano.getId(),
                    bloco.tarefaId(),
                    bloco.duracaoMinutos(),
                    bloco.ordem(),
                    agora,
                    agora
            ));
        }

        return plano;
    }
}
