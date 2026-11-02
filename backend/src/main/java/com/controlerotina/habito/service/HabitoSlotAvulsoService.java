package com.controlerotina.habito.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.controlerotina.capacidade.entity.DiaSemana;
import com.controlerotina.execucao.entity.Execucao;
import com.controlerotina.execucao.repository.ExecucaoRepository;
import com.controlerotina.habito.entity.Habito;
import com.controlerotina.habito.entity.StatusHabito;
import com.controlerotina.habito.repository.HabitoRepository;
import com.controlerotina.historico.PeriodoBRT;
import com.controlerotina.planner.entity.BlocoPlano;
import com.controlerotina.planner.entity.Plano;
import com.controlerotina.planner.entity.StatusPlano;
import com.controlerotina.planner.repository.BlocoPlanoRepository;
import com.controlerotina.planner.repository.PlanoRepository;
import com.controlerotina.shared.exception.HabitoNaoElegivelException;
import com.controlerotina.shared.exception.HabitoNaoEncontradoException;

@Service
public class HabitoSlotAvulsoService {

    private final HabitoRepository habitoRepository;
    private final PlanoRepository planoRepository;
    private final BlocoPlanoRepository blocoPlanoRepository;
    private final ExecucaoRepository execucaoRepository;
    private final Clock clock;

    public HabitoSlotAvulsoService(
            HabitoRepository habitoRepository,
            PlanoRepository planoRepository,
            BlocoPlanoRepository blocoPlanoRepository,
            ExecucaoRepository execucaoRepository,
            Clock clock
    ) {
        this.habitoRepository = habitoRepository;
        this.planoRepository = planoRepository;
        this.blocoPlanoRepository = blocoPlanoRepository;
        this.execucaoRepository = execucaoRepository;
        this.clock = clock;
    }

    @Transactional
    public SlotAvulso materializar(UUID usuarioId, UUID habitoId, LocalDate data) {
        LocalDate dia = data != null ? data : LocalDate.now(clock.withZone(PeriodoBRT.ZONE));
        Habito habito = carregarHabitoElegivel(usuarioId, habitoId, dia);

        Plano plano = planoRepository
                .findByUsuarioIdAndDataAndStatus(usuarioId, dia, StatusPlano.HABITO_AVULSO)
                .orElseGet(() -> criarPlanoAvulso(usuarioId, dia));

        BlocoPlano bloco = blocoPlanoRepository
                .findByPlanoIdAndHabitoId(plano.getId(), habito.getId())
                .orElseGet(() -> criarBloco(plano.getId(), habito));

        Execucao execucao = execucaoRepository.findByBlocoPlanoId(bloco.getId()).orElse(null);
        return new SlotAvulso(habito, plano, bloco, execucao);
    }

    private Habito carregarHabitoElegivel(UUID usuarioId, UUID habitoId, LocalDate dia) {
        Habito habito = habitoRepository.findByIdAndUsuarioId(habitoId, usuarioId)
                .orElseThrow(HabitoNaoEncontradoException::new);
        if (habito.getDeletedAt() != null) {
            throw new HabitoNaoEncontradoException();
        }
        if (habito.getStatus() != StatusHabito.ATIVO) {
            throw new HabitoNaoElegivelException(habitoId);
        }
        if (!habito.getDiasSemana().contains(DiaSemana.de(dia))) {
            throw new HabitoNaoElegivelException(habitoId);
        }
        return habito;
    }

    private Plano criarPlanoAvulso(UUID usuarioId, LocalDate dia) {
        Instant agora = clock.instant();
        int versao = planoRepository.findMaxVersaoByUsuarioIdAndData(usuarioId, dia) + 1;
        Plano plano = new Plano(
                UUID.randomUUID(),
                usuarioId,
                dia,
                versao,
                StatusPlano.HABITO_AVULSO,
                false,
                null,
                null,
                agora,
                agora,
                agora
        );
        return planoRepository.save(plano);
    }

    private BlocoPlano criarBloco(UUID planoId, Habito habito) {
        Instant agora = clock.instant();
        int ordem = blocoPlanoRepository.findMaxOrdemByPlanoId(planoId) + 1;
        BlocoPlano bloco = new BlocoPlano(
                UUID.randomUUID(),
                planoId,
                null,
                habito.getId(),
                habito.getDuracaoMinutos(),
                Math.max(ordem, 1),
                agora,
                agora
        );
        return blocoPlanoRepository.save(bloco);
    }

    public record SlotAvulso(Habito habito, Plano plano, BlocoPlano bloco, Execucao execucao) {
    }
}
