package com.controlerotina.planner.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.controlerotina.capacidade.dto.CapacidadeResolvidaResponse;
import com.controlerotina.capacidade.service.CapacidadeResolvidaService;
import com.controlerotina.planner.dto.PlanoResponse;
import com.controlerotina.planner.entity.BlocoPlano;
import com.controlerotina.planner.entity.Plano;
import com.controlerotina.planner.entity.StatusPlano;
import com.controlerotina.planner.motor.MotorAlocacao;
import com.controlerotina.planner.repository.BlocoPlanoRepository;
import com.controlerotina.planner.repository.PlanoRepository;
import com.controlerotina.shared.exception.BlocoPlanoNaoEncontradoException;
import com.controlerotina.shared.exception.PlanoNaoAjustavelException;
import com.controlerotina.shared.exception.PlanoNaoEncontradoException;
import com.controlerotina.shared.exception.TarefaJaNoPlanoException;
import com.controlerotina.shared.exception.TarefaNaoElegivelException;
import com.controlerotina.shared.exception.TarefaNaoEncontradaException;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;
import com.controlerotina.tarefa.repository.TarefaRepository;

@Service
public class PlanoAjusteService {

    public static final String MENSAGEM_ESTOURO =
            "Capacidade do dia excedida pela soma dos blocos.";

    private static final Set<StatusTarefa> STATUS_ELEGIVEIS = Set.of(
            StatusTarefa.PENDENTE,
            StatusTarefa.EM_PROGRESSO,
            StatusTarefa.ATRASADA
    );

    private final PlanoRepository planoRepository;
    private final BlocoPlanoRepository blocoPlanoRepository;
    private final TarefaRepository tarefaRepository;
    private final CapacidadeResolvidaService capacidadeResolvidaService;
    private final PlanoConsultaService planoConsultaService;
    private final Clock clock;

    public PlanoAjusteService(
            PlanoRepository planoRepository,
            BlocoPlanoRepository blocoPlanoRepository,
            TarefaRepository tarefaRepository,
            CapacidadeResolvidaService capacidadeResolvidaService,
            PlanoConsultaService planoConsultaService,
            Clock clock
    ) {
        this.planoRepository = planoRepository;
        this.blocoPlanoRepository = blocoPlanoRepository;
        this.tarefaRepository = tarefaRepository;
        this.capacidadeResolvidaService = capacidadeResolvidaService;
        this.planoConsultaService = planoConsultaService;
        this.clock = clock;
    }

    @Transactional
    public PlanoResponse adicionarBloco(UUID usuarioId, UUID planoId, UUID tarefaId) {
        Plano plano = planoRepository.findByIdAndUsuarioId(planoId, usuarioId)
                .orElseThrow(PlanoNaoEncontradoException::new);
        garantirRascunhoCorrente(plano);

        Tarefa tarefa = tarefaRepository.findByIdAndUsuarioId(tarefaId, usuarioId)
                .orElseThrow(TarefaNaoEncontradaException::new);
        garantirElegivel(tarefa);

        if (blocoPlanoRepository.existsByPlanoIdAndTarefaId(plano.getId(), tarefa.getId())) {
            throw new TarefaJaNoPlanoException(tarefa.getId());
        }

        Instant agora = clock.instant();
        List<BlocoPlano> blocosAtuais = blocoPlanoRepository.findByPlanoIdOrderByOrdemAsc(plano.getId());
        int proximaOrdem = blocosAtuais.stream()
                .mapToInt(BlocoPlano::getOrdem)
                .max()
                .orElse(0) + 1;
        int duracaoMinutos = tarefa.getDuracaoHoras() * 60;

        blocoPlanoRepository.save(new BlocoPlano(
                UUID.randomUUID(),
                plano.getId(),
                tarefa.getId(),
                duracaoMinutos,
                proximaOrdem,
                agora,
                agora
        ));

        int somaMinutos = blocosAtuais.stream().mapToInt(BlocoPlano::getDuracaoMinutos).sum() + duracaoMinutos;
        int capacidadeMinutos = resolverCapacidadeMinutos(usuarioId, plano);
        boolean temConflito = somaMinutos > capacidadeMinutos;
        plano.atualizarConflito(
                temConflito,
                temConflito ? MENSAGEM_ESTOURO : null,
                agora
        );
        planoRepository.save(plano);

        return planoConsultaService.buscarPorId(usuarioId, plano.getId())
                .orElseThrow(PlanoNaoEncontradoException::new);
    }

    @Transactional
    public void removerBloco(UUID usuarioId, UUID planoId, UUID blocoId) {
        Plano plano = planoRepository.findByIdAndUsuarioId(planoId, usuarioId)
                .orElseThrow(PlanoNaoEncontradoException::new);
        garantirRascunhoCorrente(plano);

        BlocoPlano bloco = blocoPlanoRepository.findByIdAndPlanoId(blocoId, plano.getId())
                .orElseThrow(BlocoPlanoNaoEncontradoException::new);

        blocoPlanoRepository.delete(bloco);

        Instant agora = clock.instant();
        List<BlocoPlano> restantes = blocoPlanoRepository.findByPlanoIdOrderByOrdemAsc(plano.getId());
        int somaMinutos = restantes.stream().mapToInt(BlocoPlano::getDuracaoMinutos).sum();
        int capacidadeMinutos = resolverCapacidadeMinutos(usuarioId, plano);
        boolean temConflito = somaMinutos > capacidadeMinutos;
        plano.atualizarConflito(
                temConflito,
                temConflito ? MENSAGEM_ESTOURO : null,
                agora
        );
        planoRepository.save(plano);
    }

    private void garantirRascunhoCorrente(Plano plano) {
        if (plano.getStatus() != StatusPlano.RASCUNHO) {
            throw new PlanoNaoAjustavelException();
        }
        Plano corrente = planoRepository
                .findFirstByUsuarioIdAndDataOrderByVersaoDesc(plano.getUsuarioId(), plano.getData())
                .orElseThrow(PlanoNaoEncontradoException::new);
        if (!corrente.getId().equals(plano.getId())) {
            throw new PlanoNaoAjustavelException();
        }
    }

    private void garantirElegivel(Tarefa tarefa) {
        if (tarefa.getDeletedAt() != null || !STATUS_ELEGIVEIS.contains(tarefa.getStatus())) {
            throw new TarefaNaoElegivelException(tarefa.getId());
        }
    }

    private int resolverCapacidadeMinutos(UUID usuarioId, Plano plano) {
        CapacidadeResolvidaResponse capacidade = capacidadeResolvidaService
                .resolver(usuarioId, plano.getData(), plano.getData())
                .getFirst();
        return MotorAlocacao.capacidadeMinutos(capacidade.horas());
    }
}
