package com.controlerotina.planner.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.controlerotina.capacidade.dto.CapacidadeResolvidaResponse;
import com.controlerotina.capacidade.entity.DiaSemana;
import com.controlerotina.capacidade.service.CapacidadeResolvidaService;
import com.controlerotina.execucao.entity.StatusExecucao;
import com.controlerotina.execucao.repository.ExecucaoRepository;
import com.controlerotina.habito.entity.Habito;
import com.controlerotina.habito.entity.StatusHabito;
import com.controlerotina.habito.repository.HabitoRepository;
import com.controlerotina.planner.dto.AdicionarBlocoRequest;
import com.controlerotina.planner.dto.PlanoResponse;
import com.controlerotina.planner.entity.BlocoPlano;
import com.controlerotina.planner.entity.Plano;
import com.controlerotina.planner.entity.StatusPlano;
import com.controlerotina.planner.motor.ItemNaoAlocado;
import com.controlerotina.planner.motor.MotivoNaoAlocado;
import com.controlerotina.planner.motor.MotorAlocacao;
import com.controlerotina.planner.motor.TipoOrigemAlocacao;
import com.controlerotina.planner.repository.BlocoPlanoRepository;
import com.controlerotina.planner.repository.PlanoRepository;
import com.controlerotina.shared.exception.BlocoPlanoNaoEncontradoException;
import com.controlerotina.shared.exception.HabitoJaNoPlanoException;
import com.controlerotina.shared.exception.HabitoNaoElegivelException;
import com.controlerotina.shared.exception.HabitoNaoEncontradoException;
import com.controlerotina.shared.exception.OrigemBlocoInvalidaException;
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
    private final HabitoRepository habitoRepository;
    private final CapacidadeResolvidaService capacidadeResolvidaService;
    private final PlanoConsultaService planoConsultaService;
    private final ExecucaoRepository execucaoRepository;
    private final Clock clock;

    public PlanoAjusteService(
            PlanoRepository planoRepository,
            BlocoPlanoRepository blocoPlanoRepository,
            TarefaRepository tarefaRepository,
            HabitoRepository habitoRepository,
            CapacidadeResolvidaService capacidadeResolvidaService,
            PlanoConsultaService planoConsultaService,
            ExecucaoRepository execucaoRepository,
            Clock clock
    ) {
        this.planoRepository = planoRepository;
        this.blocoPlanoRepository = blocoPlanoRepository;
        this.tarefaRepository = tarefaRepository;
        this.habitoRepository = habitoRepository;
        this.capacidadeResolvidaService = capacidadeResolvidaService;
        this.planoConsultaService = planoConsultaService;
        this.execucaoRepository = execucaoRepository;
        this.clock = clock;
    }

    @Transactional
    public PlanoResponse adicionarBloco(UUID usuarioId, UUID planoId, AdicionarBlocoRequest request) {
        if (request == null || !request.origemValida()) {
            throw new OrigemBlocoInvalidaException();
        }
        if (request.habitoId() != null) {
            return adicionarBlocoHabito(usuarioId, planoId, request.habitoId());
        }
        return adicionarBlocoTarefa(usuarioId, planoId, request.tarefaId());
    }

    /** Compatível com callers legados que passam só tarefaId. */
    @Transactional
    public PlanoResponse adicionarBloco(UUID usuarioId, UUID planoId, UUID tarefaId) {
        return adicionarBloco(usuarioId, planoId, new AdicionarBlocoRequest(tarefaId, null));
    }

    private PlanoResponse adicionarBlocoTarefa(UUID usuarioId, UUID planoId, UUID tarefaId) {
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
        int proximaOrdem = proximaOrdem(blocosAtuais);
        int duracaoMinutos = tarefa.getDuracaoMinutos();

        blocoPlanoRepository.save(new BlocoPlano(
                UUID.randomUUID(),
                plano.getId(),
                tarefa.getId(),
                null,
                duracaoMinutos,
                proximaOrdem,
                agora,
                agora
        ));

        atualizarConflitoEOmitidos(plano, usuarioId, agora);
        return planoConsultaService.buscarPorId(usuarioId, plano.getId())
                .orElseThrow(PlanoNaoEncontradoException::new);
    }

    private PlanoResponse adicionarBlocoHabito(UUID usuarioId, UUID planoId, UUID habitoId) {
        Plano plano = planoRepository.findByIdAndUsuarioId(planoId, usuarioId)
                .orElseThrow(PlanoNaoEncontradoException::new);
        garantirRascunhoCorrente(plano);

        Habito habito = habitoRepository.findByIdAndUsuarioId(habitoId, usuarioId)
                .orElseThrow(HabitoNaoEncontradoException::new);
        garantirHabitoElegivel(habito, plano);

        if (blocoPlanoRepository.existsByPlanoIdAndHabitoId(plano.getId(), habito.getId())) {
            throw new HabitoJaNoPlanoException(habito.getId());
        }

        Instant agora = clock.instant();
        List<BlocoPlano> blocosAtuais = blocoPlanoRepository.findByPlanoIdOrderByOrdemAsc(plano.getId());
        int proximaOrdem = proximaOrdem(blocosAtuais);
        int duracaoMinutos = habito.getDuracaoMinutos();

        blocoPlanoRepository.save(new BlocoPlano(
                UUID.randomUUID(),
                plano.getId(),
                null,
                habito.getId(),
                duracaoMinutos,
                proximaOrdem,
                agora,
                agora
        ));

        atualizarConflitoEOmitidos(plano, usuarioId, agora);
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
        atualizarConflitoEOmitidos(plano, usuarioId, agora);
    }

    /**
     * Snapshot pós-ajuste: hábitos do weekday fora do plano ∪ tarefas com saldo fora do plano
     * somente se capacidade estourada / conflito.
     */
    private void atualizarConflitoEOmitidos(Plano plano, UUID usuarioId, Instant agora) {
        List<BlocoPlano> blocos = blocoPlanoRepository.findByPlanoIdOrderByOrdemAsc(plano.getId());
        int somaMinutos = blocos.stream().mapToInt(BlocoPlano::getDuracaoMinutos).sum();
        int capacidadeMinutos = resolverCapacidadeMinutos(usuarioId, plano);
        boolean estourou = somaMinutos > capacidadeMinutos;

        Set<UUID> tarefaIdsNoPlano = blocos.stream()
                .map(BlocoPlano::getTarefaId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        Set<UUID> habitoIdsNoPlano = blocos.stream()
                .map(BlocoPlano::getHabitoId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));

        List<ItemNaoAlocado> omitidos = new ArrayList<>();
        DiaSemana dia = DiaSemana.de(plano.getData());
        for (Habito habito : habitoRepository.findByUsuarioIdAndStatusAndDeletedAtIsNull(
                usuarioId, StatusHabito.ATIVO)) {
            if (habitoIdsNoPlano.contains(habito.getId())) {
                continue;
            }
            if (!habito.getDiasSemana().contains(dia)) {
                continue;
            }
            omitidos.add(new ItemNaoAlocado(
                    TipoOrigemAlocacao.HABITO,
                    habito.getId(),
                    habito.getTitulo(),
                    habito.getDuracaoMinutos(),
                    MotivoNaoAlocado.CAPACIDADE
            ));
        }

        // Tarefas residual só quando o dia estourou; evita listar em D o que cabe em D+1.
        if (estourou) {
            for (Tarefa tarefa : tarefaRepository.findByUsuarioIdAndDeletedAtIsNullAndStatusIn(
                    usuarioId, List.copyOf(STATUS_ELEGIVEIS))) {
                if (tarefaIdsNoPlano.contains(tarefa.getId())) {
                    continue;
                }
                long minutosConcluidos = execucaoRepository.somarDuracaoMinutosPorTarefaEStatus(
                        tarefa.getId(), usuarioId, StatusExecucao.CONCLUIDO);
                int saldo = MotorAlocacao.calcularSaldoMinutos(tarefa.getDuracaoMinutos(), minutosConcluidos);
                if (saldo <= 0) {
                    continue;
                }
                omitidos.add(new ItemNaoAlocado(
                        TipoOrigemAlocacao.TAREFA,
                        tarefa.getId(),
                        tarefa.getTitulo(),
                        tarefa.getDuracaoMinutos(),
                        MotivoNaoAlocado.CAPACIDADE
                ));
            }
        }

        boolean temConflito = estourou || !omitidos.isEmpty();
        String mensagem = estourou
                ? MENSAGEM_ESTOURO
                : (temConflito ? MotorAlocacao.MENSAGEM_HABITO_OMITIDO : null);
        plano.atualizarConflitoEOmitidos(temConflito, mensagem, omitidos, agora);
        planoRepository.save(plano);
    }

    private static int proximaOrdem(List<BlocoPlano> blocosAtuais) {
        return blocosAtuais.stream()
                .mapToInt(BlocoPlano::getOrdem)
                .max()
                .orElse(0) + 1;
    }

    private void garantirRascunhoCorrente(Plano plano) {
        if (plano.getStatus() != StatusPlano.RASCUNHO) {
            throw new PlanoNaoAjustavelException();
        }
        Plano corrente = planoRepository
                .findFirstByUsuarioIdAndDataAndStatusInOrderByVersaoDesc(
                        plano.getUsuarioId(),
                        plano.getData(),
                        java.util.List.of(StatusPlano.RASCUNHO, StatusPlano.APROVADO))
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

    private void garantirHabitoElegivel(Habito habito, Plano plano) {
        if (habito.isDeletado() || habito.getStatus() != StatusHabito.ATIVO) {
            throw new HabitoNaoElegivelException(habito.getId());
        }
        DiaSemana dia = DiaSemana.de(plano.getData());
        if (!habito.getDiasSemana().contains(dia)) {
            throw new HabitoNaoElegivelException(habito.getId());
        }
    }

    private int resolverCapacidadeMinutos(UUID usuarioId, Plano plano) {
        CapacidadeResolvidaResponse capacidade = capacidadeResolvidaService
                .resolver(usuarioId, plano.getData(), plano.getData())
                .getFirst();
        return MotorAlocacao.capacidadeMinutos(capacidade.horas());
    }
}
