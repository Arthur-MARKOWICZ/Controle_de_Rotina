package com.controlerotina.planner.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.controlerotina.planner.dto.PlanoResponse;
import com.controlerotina.planner.entity.Plano;
import com.controlerotina.planner.entity.StatusPlano;
import com.controlerotina.planner.repository.BlocoPlanoRepository;
import com.controlerotina.planner.repository.PlanoRepository;
import com.controlerotina.shared.exception.PlanoNaoAprovavelException;
import com.controlerotina.shared.exception.PlanoNaoEncontradoException;
import com.controlerotina.shared.exception.PlanoVazioException;

@Service
public class PlanoAprovacaoService {

    private final PlanoRepository planoRepository;
    private final BlocoPlanoRepository blocoPlanoRepository;
    private final PlanoConsultaService planoConsultaService;
    private final PropostaService propostaService;
    private final Clock clock;

    public PlanoAprovacaoService(
            PlanoRepository planoRepository,
            BlocoPlanoRepository blocoPlanoRepository,
            PlanoConsultaService planoConsultaService,
            PropostaService propostaService,
            Clock clock
    ) {
        this.planoRepository = planoRepository;
        this.blocoPlanoRepository = blocoPlanoRepository;
        this.planoConsultaService = planoConsultaService;
        this.propostaService = propostaService;
        this.clock = clock;
    }

    @Transactional
    public PlanoResponse aprovar(UUID usuarioId, UUID planoId) {
        Plano plano = planoRepository.findByIdAndUsuarioId(planoId, usuarioId)
                .orElseThrow(PlanoNaoEncontradoException::new);
        garantirRascunhoCorrente(plano);
        if (!blocoPlanoRepository.existsByPlanoId(plano.getId())) {
            throw new PlanoVazioException();
        }

        // Rascunho de proposta: /aprovar equivale a aceitar (preserva concluídos).
        if (plano.getPropostaId() != null) {
            return propostaService.aceitar(usuarioId, plano.getPropostaId());
        }

        Instant agora = clock.instant();
        plano.aprovar(agora);
        planoRepository.save(plano);

        return planoConsultaService.buscarPorId(usuarioId, plano.getId())
                .orElseThrow(PlanoNaoEncontradoException::new);
    }

    private void garantirRascunhoCorrente(Plano plano) {
        if (plano.getStatus() != StatusPlano.RASCUNHO) {
            throw new PlanoNaoAprovavelException();
        }
        Plano corrente = planoRepository
                .findFirstByUsuarioIdAndDataAndStatusInOrderByVersaoDesc(
                        plano.getUsuarioId(),
                        plano.getData(),
                        java.util.List.of(StatusPlano.RASCUNHO, StatusPlano.APROVADO))
                .orElseThrow(PlanoNaoEncontradoException::new);
        if (!corrente.getId().equals(plano.getId())) {
            throw new PlanoNaoAprovavelException();
        }
    }
}
