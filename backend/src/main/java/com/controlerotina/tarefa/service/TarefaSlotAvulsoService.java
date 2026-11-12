package com.controlerotina.tarefa.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.controlerotina.execucao.entity.Execucao;
import com.controlerotina.execucao.entity.StatusExecucao;
import com.controlerotina.execucao.repository.ExecucaoRepository;
import com.controlerotina.historico.PeriodoBRT;
import com.controlerotina.pendencia.service.EsperaService;
import com.controlerotina.planner.entity.BlocoPlano;
import com.controlerotina.planner.entity.Plano;
import com.controlerotina.planner.entity.StatusPlano;
import com.controlerotina.planner.repository.BlocoPlanoRepository;
import com.controlerotina.planner.repository.PlanoRepository;
import com.controlerotina.shared.exception.TarefaNaoElegivelException;
import com.controlerotina.shared.exception.TarefaNaoEncontradaException;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;
import com.controlerotina.tarefa.repository.TarefaRepository;

@Service
public class TarefaSlotAvulsoService {

    private static final Set<StatusTarefa> STATUS_ELEGIVEIS = Set.of(
            StatusTarefa.PENDENTE,
            StatusTarefa.EM_PROGRESSO,
            StatusTarefa.ATRASADA
    );

    private final TarefaRepository tarefaRepository;
    private final PlanoRepository planoRepository;
    private final BlocoPlanoRepository blocoPlanoRepository;
    private final ExecucaoRepository execucaoRepository;
    private final EsperaService esperaService;
    private final Clock clock;

    public TarefaSlotAvulsoService(
            TarefaRepository tarefaRepository,
            PlanoRepository planoRepository,
            BlocoPlanoRepository blocoPlanoRepository,
            ExecucaoRepository execucaoRepository,
            EsperaService esperaService,
            Clock clock
    ) {
        this.tarefaRepository = tarefaRepository;
        this.planoRepository = planoRepository;
        this.blocoPlanoRepository = blocoPlanoRepository;
        this.execucaoRepository = execucaoRepository;
        this.esperaService = esperaService;
        this.clock = clock;
    }

    @Transactional
    public SlotAvulso materializar(UUID usuarioId, UUID tarefaId, LocalDate data, int duracaoMinutos) {
        LocalDate dia = data != null ? data : LocalDate.now(clock.withZone(PeriodoBRT.ZONE));
        Tarefa tarefa = carregarTarefaElegivel(usuarioId, tarefaId);

        Plano plano = planoRepository
                .findByUsuarioIdAndDataAndStatus(usuarioId, dia, StatusPlano.TAREFA_AVULSA)
                .orElseGet(() -> criarPlanoAvulso(usuarioId, dia));

        BlocoPlano blocoAberto = encontrarBlocoAberto(plano.getId(), tarefa.getId());
        BlocoPlano bloco;
        if (blocoAberto != null) {
            if (blocoAberto.getDuracaoMinutos() != duracaoMinutos) {
                blocoAberto.atualizarDuracao(duracaoMinutos, clock.instant());
                bloco = blocoPlanoRepository.save(blocoAberto);
            } else {
                bloco = blocoAberto;
            }
        } else {
            bloco = criarBloco(plano.getId(), tarefa.getId(), duracaoMinutos);
        }

        Execucao execucao = execucaoRepository.findByBlocoPlanoId(bloco.getId()).orElse(null);
        return new SlotAvulso(tarefa, plano, bloco, execucao);
    }

    public int restanteMinutos(UUID usuarioId, Tarefa tarefa) {
        long concluidos = execucaoRepository.somarDuracaoMinutosPorTarefaEStatus(
                tarefa.getId(),
                usuarioId,
                StatusExecucao.CONCLUIDO
        );
        return Math.max(0, tarefa.getDuracaoMinutos() - (int) concluidos);
    }

    private Tarefa carregarTarefaElegivel(UUID usuarioId, UUID tarefaId) {
        Tarefa tarefa = tarefaRepository.findByIdAndUsuarioId(tarefaId, usuarioId)
                .orElseThrow(TarefaNaoEncontradaException::new);
        if (tarefa.getDeletedAt() != null) {
            throw new TarefaNaoEncontradaException();
        }
        if (!STATUS_ELEGIVEIS.contains(tarefa.getStatus())) {
            throw new TarefaNaoElegivelException(tarefaId);
        }
        if (tarefaRepository.existsByParentIdAndDeletedAtIsNull(tarefaId)) {
            throw new TarefaNaoElegivelException(tarefaId);
        }
        esperaService.garantirTarefaNaoBloqueada(usuarioId, tarefa);
        return tarefa;
    }

    private BlocoPlano encontrarBlocoAberto(UUID planoId, UUID tarefaId) {
        List<BlocoPlano> blocos = blocoPlanoRepository
                .findByPlanoIdAndTarefaIdOrderByOrdemAsc(planoId, tarefaId);
        for (BlocoPlano bloco : blocos) {
            if (!estaConcluido(bloco.getId())) {
                return bloco;
            }
        }
        return null;
    }

    private boolean estaConcluido(UUID blocoId) {
        return execucaoRepository.findByBlocoPlanoId(blocoId)
                .map(Execucao::getStatus)
                .filter(status -> status == StatusExecucao.CONCLUIDO)
                .isPresent();
    }

    private Plano criarPlanoAvulso(UUID usuarioId, LocalDate dia) {
        Instant agora = clock.instant();
        int versao = planoRepository.findMaxVersaoByUsuarioIdAndData(usuarioId, dia) + 1;
        Plano plano = new Plano(
                UUID.randomUUID(),
                usuarioId,
                dia,
                versao,
                StatusPlano.TAREFA_AVULSA,
                false,
                null,
                null,
                agora,
                agora,
                agora
        );
        return planoRepository.save(plano);
    }

    private BlocoPlano criarBloco(UUID planoId, UUID tarefaId, int duracaoMinutos) {
        Instant agora = clock.instant();
        int ordem = blocoPlanoRepository.findMaxOrdemByPlanoId(planoId) + 1;
        BlocoPlano bloco = new BlocoPlano(
                UUID.randomUUID(),
                planoId,
                tarefaId,
                duracaoMinutos,
                Math.max(ordem, 1),
                agora,
                agora
        );
        return blocoPlanoRepository.save(bloco);
    }

    public record SlotAvulso(Tarefa tarefa, Plano plano, BlocoPlano bloco, Execucao execucao) {
    }
}
