package com.controlerotina.habito.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.controlerotina.execucao.entity.Execucao;
import com.controlerotina.execucao.entity.StatusExecucao;
import com.controlerotina.execucao.dto.ResultadoConclusao;
import com.controlerotina.execucao.dto.ResultadoInicio;
import com.controlerotina.execucao.repository.ExecucaoRepository;
import com.controlerotina.execucao.service.ExecucaoService;
import com.controlerotina.habito.entity.Habito;
import com.controlerotina.habito.repository.HabitoRepository;
import com.controlerotina.historico.PeriodoBRT;
import com.controlerotina.planner.entity.BlocoPlano;
import com.controlerotina.planner.entity.Plano;
import com.controlerotina.planner.entity.StatusPlano;
import com.controlerotina.planner.repository.BlocoPlanoRepository;
import com.controlerotina.planner.repository.PlanoRepository;
import com.controlerotina.shared.exception.HabitoNaoEncontradoException;
import com.controlerotina.shared.exception.MinutosInformadosInvalidosException;

@Service
public class HabitoExecucaoAvulsaService {

    private final HabitoSlotAvulsoService slotAvulsoService;
    private final ExecucaoService execucaoService;
    private final PlanoRepository planoRepository;
    private final BlocoPlanoRepository blocoPlanoRepository;
    private final HabitoRepository habitoRepository;
    private final ExecucaoRepository execucaoRepository;
    private final Clock clock;

    public HabitoExecucaoAvulsaService(
            HabitoSlotAvulsoService slotAvulsoService,
            ExecucaoService execucaoService,
            PlanoRepository planoRepository,
            BlocoPlanoRepository blocoPlanoRepository,
            HabitoRepository habitoRepository,
            ExecucaoRepository execucaoRepository,
            Clock clock
    ) {
        this.slotAvulsoService = slotAvulsoService;
        this.execucaoService = execucaoService;
        this.planoRepository = planoRepository;
        this.blocoPlanoRepository = blocoPlanoRepository;
        this.habitoRepository = habitoRepository;
        this.execucaoRepository = execucaoRepository;
        this.clock = clock;
    }

    @Transactional
    public ResultadoInicio iniciar(UUID usuarioId, UUID habitoId, LocalDate data) {
        LocalDate dia = data != null ? data : LocalDate.now(clock.withZone(PeriodoBRT.ZONE));
        Optional<BlocoNoPlano> noPlano = buscarBlocoNoPlanoAberto(usuarioId, habitoId, dia);
        if (noPlano.isPresent()) {
            return execucaoService.iniciar(usuarioId, noPlano.get().bloco().getId());
        }
        HabitoSlotAvulsoService.SlotAvulso slot = slotAvulsoService.materializar(usuarioId, habitoId, dia);
        return execucaoService.iniciar(usuarioId, slot.bloco().getId());
    }

    @Transactional
    public ResultadoConclusao concluirDireto(UUID usuarioId, UUID habitoId, LocalDate data, Integer minutos) {
        validarMinutos(minutos);
        LocalDate dia = data != null ? data : LocalDate.now(clock.withZone(PeriodoBRT.ZONE));
        Optional<BlocoNoPlano> noPlano = buscarBlocoNoPlanoAberto(usuarioId, habitoId, dia);
        if (noPlano.isPresent()) {
            Habito habito = habitoRepository.findByIdAndUsuarioId(habitoId, usuarioId)
                    .orElseThrow(HabitoNaoEncontradoException::new);
            return execucaoService.concluirHabitoDiretoNoBloco(
                    usuarioId,
                    noPlano.get().bloco(),
                    noPlano.get().plano(),
                    habito,
                    minutos
            );
        }
        HabitoSlotAvulsoService.SlotAvulso slot = slotAvulsoService.materializar(usuarioId, habitoId, dia);
        return execucaoService.concluirHabitoDiretoNoBloco(
                usuarioId,
                slot.bloco(),
                slot.plano(),
                slot.habito(),
                minutos
        );
    }

    private static void validarMinutos(Integer minutos) {
        if (minutos != null
                && (minutos < 1 || minutos > MinutosInformadosInvalidosException.MINUTOS_MAXIMO)) {
            throw new MinutosInformadosInvalidosException();
        }
    }

    /**
     * Bloco do plano aprovado só é preferido se ainda não estiver {@code CONCLUIDO}
     * (permite cumprimento avulso adicional no mesmo dia).
     */
    private Optional<BlocoNoPlano> buscarBlocoNoPlanoAberto(
            UUID usuarioId,
            UUID habitoId,
            LocalDate dia
    ) {
        return planoRepository
                .findFirstByUsuarioIdAndDataAndStatusOrderByVersaoDesc(
                        usuarioId, dia, StatusPlano.APROVADO)
                .flatMap(plano -> blocoPlanoRepository
                        .findByPlanoIdAndHabitoId(plano.getId(), habitoId)
                        .filter(bloco -> !estaConcluido(bloco.getId()))
                        .map(bloco -> new BlocoNoPlano(plano, bloco)));
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
