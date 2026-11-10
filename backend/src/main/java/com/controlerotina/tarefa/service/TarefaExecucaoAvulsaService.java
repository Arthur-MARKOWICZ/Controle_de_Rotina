package com.controlerotina.tarefa.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.controlerotina.execucao.dto.ResultadoConclusao;
import com.controlerotina.execucao.dto.ResultadoInicio;
import com.controlerotina.execucao.entity.Execucao;
import com.controlerotina.execucao.entity.StatusExecucao;
import com.controlerotina.execucao.repository.ExecucaoRepository;
import com.controlerotina.execucao.service.ExecucaoService;
import com.controlerotina.historico.PeriodoBRT;
import com.controlerotina.planner.entity.BlocoPlano;
import com.controlerotina.planner.entity.Plano;
import com.controlerotina.planner.entity.StatusPlano;
import com.controlerotina.planner.repository.BlocoPlanoRepository;
import com.controlerotina.planner.repository.PlanoRepository;
import com.controlerotina.shared.exception.BlocoPlanoNaoEncontradoException;
import com.controlerotina.shared.exception.DuracaoSessaoInvalidaException;
import com.controlerotina.shared.exception.TarefaNaoElegivelException;
import com.controlerotina.shared.exception.TarefaNaoEncontradaException;
import com.controlerotina.tarefa.entity.Tarefa;
import com.controlerotina.tarefa.repository.TarefaRepository;

@Service
public class TarefaExecucaoAvulsaService {

    private final TarefaSlotAvulsoService slotAvulsoService;
    private final ExecucaoService execucaoService;
    private final PlanoRepository planoRepository;
    private final BlocoPlanoRepository blocoPlanoRepository;
    private final TarefaRepository tarefaRepository;
    private final ExecucaoRepository execucaoRepository;
    private final Clock clock;

    public TarefaExecucaoAvulsaService(
            TarefaSlotAvulsoService slotAvulsoService,
            ExecucaoService execucaoService,
            PlanoRepository planoRepository,
            BlocoPlanoRepository blocoPlanoRepository,
            TarefaRepository tarefaRepository,
            ExecucaoRepository execucaoRepository,
            Clock clock
    ) {
        this.slotAvulsoService = slotAvulsoService;
        this.execucaoService = execucaoService;
        this.planoRepository = planoRepository;
        this.blocoPlanoRepository = blocoPlanoRepository;
        this.tarefaRepository = tarefaRepository;
        this.execucaoRepository = execucaoRepository;
        this.clock = clock;
    }

    @Transactional
    public ResultadoInicio iniciar(
            UUID usuarioId,
            UUID tarefaId,
            Integer duracaoMinutos,
            LocalDate data,
            UUID blocoId
    ) {
        LocalDate dia = data != null ? data : LocalDate.now(clock.withZone(PeriodoBRT.ZONE));
        Optional<BlocoNoPlano> noPlano = buscarBlocoNoPlanoAberto(usuarioId, tarefaId, dia, blocoId);
        if (noPlano.isPresent()) {
            return execucaoService.iniciar(usuarioId, noPlano.get().bloco().getId());
        }
        int duracao = exigirDuracaoValida(usuarioId, tarefaId, duracaoMinutos);
        TarefaSlotAvulsoService.SlotAvulso slot =
                slotAvulsoService.materializar(usuarioId, tarefaId, dia, duracao);
        return execucaoService.iniciar(usuarioId, slot.bloco().getId());
    }

    @Transactional
    public ResultadoConclusao concluirDireto(
            UUID usuarioId,
            UUID tarefaId,
            Integer duracaoMinutos,
            LocalDate data,
            UUID blocoId
    ) {
        LocalDate dia = data != null ? data : LocalDate.now(clock.withZone(PeriodoBRT.ZONE));
        Optional<BlocoNoPlano> noPlano = buscarBlocoNoPlanoAberto(usuarioId, tarefaId, dia, blocoId);
        if (noPlano.isPresent()) {
            Tarefa tarefa = tarefaRepository.findByIdAndUsuarioId(tarefaId, usuarioId)
                    .orElseThrow(TarefaNaoEncontradaException::new);
            return execucaoService.concluirTarefaDiretoNoBloco(
                    usuarioId,
                    noPlano.get().bloco(),
                    tarefa
            );
        }
        int duracao = exigirDuracaoValida(usuarioId, tarefaId, duracaoMinutos);
        TarefaSlotAvulsoService.SlotAvulso slot =
                slotAvulsoService.materializar(usuarioId, tarefaId, dia, duracao);
        return execucaoService.concluirTarefaDiretoNoBloco(
                usuarioId,
                slot.bloco(),
                slot.tarefa()
        );
    }

    private int exigirDuracaoValida(UUID usuarioId, UUID tarefaId, Integer duracaoMinutos) {
        Tarefa tarefa = tarefaRepository.findByIdAndUsuarioId(tarefaId, usuarioId)
                .orElseThrow(TarefaNaoEncontradaException::new);
        if (tarefa.getDeletedAt() != null) {
            throw new TarefaNaoEncontradaException();
        }
        int restante = slotAvulsoService.restanteMinutos(usuarioId, tarefa);
        if (restante <= 0) {
            throw new TarefaNaoElegivelException(tarefaId);
        }
        if (duracaoMinutos == null || duracaoMinutos < 1 || duracaoMinutos > restante) {
            throw new DuracaoSessaoInvalidaException(restante);
        }
        return duracaoMinutos;
    }

    /**
     * Bloco do plano aprovado só é preferido se ainda não estiver {@code CONCLUIDO}.
     * {@code blocoId} opcional desambigua quando há mais de um bloco aberto da tarefa.
     */
    private Optional<BlocoNoPlano> buscarBlocoNoPlanoAberto(
            UUID usuarioId,
            UUID tarefaId,
            LocalDate dia,
            UUID blocoId
    ) {
        return planoRepository
                .findFirstByUsuarioIdAndDataAndStatusOrderByVersaoDesc(
                        usuarioId, dia, StatusPlano.APROVADO)
                .flatMap(plano -> resolverBlocoAberto(plano, tarefaId, blocoId));
    }

    private Optional<BlocoNoPlano> resolverBlocoAberto(Plano plano, UUID tarefaId, UUID blocoId) {
        if (blocoId != null) {
            BlocoPlano bloco = blocoPlanoRepository.findByIdAndPlanoId(blocoId, plano.getId())
                    .orElseThrow(BlocoPlanoNaoEncontradoException::new);
            if (bloco.getTarefaId() == null || !bloco.getTarefaId().equals(tarefaId)) {
                throw new BlocoPlanoNaoEncontradoException();
            }
            if (estaConcluido(bloco.getId())) {
                return Optional.empty();
            }
            return Optional.of(new BlocoNoPlano(plano, bloco));
        }

        List<BlocoPlano> blocos = blocoPlanoRepository
                .findByPlanoIdAndTarefaIdOrderByOrdemAsc(plano.getId(), tarefaId);
        for (BlocoPlano bloco : blocos) {
            if (!estaConcluido(bloco.getId())) {
                return Optional.of(new BlocoNoPlano(plano, bloco));
            }
        }
        return Optional.empty();
    }

    private boolean estaConcluido(UUID blocoId) {
        return execucaoRepository.findByBlocoPlanoId(blocoId)
                .map(Execucao::getStatus)
                .filter(status -> status == StatusExecucao.CONCLUIDO)
                .isPresent();
    }

    private record BlocoNoPlano(Plano plano, BlocoPlano bloco) {
    }
}
