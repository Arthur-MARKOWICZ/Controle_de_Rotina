package com.controlerotina.planner.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.controlerotina.execucao.entity.Execucao;
import com.controlerotina.execucao.entity.StatusExecucao;
import com.controlerotina.execucao.repository.ExecucaoRepository;
import com.controlerotina.planner.dto.PlanoResponse;
import com.controlerotina.planner.entity.BlocoPlano;
import com.controlerotina.planner.entity.Plano;
import com.controlerotina.planner.entity.StatusPlano;
import com.controlerotina.planner.repository.BlocoPlanoRepository;
import com.controlerotina.planner.repository.PlanoRepository;
import com.controlerotina.shared.exception.PropostaInvalidaException;

@Service
public class PropostaService {

    private final PlanoRepository planoRepository;
    private final BlocoPlanoRepository blocoPlanoRepository;
    private final ExecucaoRepository execucaoRepository;
    private final PlanoConsultaService planoConsultaService;
    private final Clock clock;

    public PropostaService(
            PlanoRepository planoRepository,
            BlocoPlanoRepository blocoPlanoRepository,
            ExecucaoRepository execucaoRepository,
            PlanoConsultaService planoConsultaService,
            Clock clock
    ) {
        this.planoRepository = planoRepository;
        this.blocoPlanoRepository = blocoPlanoRepository;
        this.execucaoRepository = execucaoRepository;
        this.planoConsultaService = planoConsultaService;
        this.clock = clock;
    }

    @Transactional
    public PlanoResponse aceitar(UUID usuarioId, UUID propostaId) {
        List<Plano> rascunhos = planosRascunhoDaProposta(usuarioId, propostaId);
        Instant agora = clock.instant();
        Plano primeiroDoDiaMaisCedo = null;

        for (Plano propostaDia : rascunhos) {
            preservarBlocosConcluidos(usuarioId, propostaDia, agora);
            propostaDia.aprovar(agora);
            planoRepository.save(propostaDia);
            if (primeiroDoDiaMaisCedo == null
                    || propostaDia.getData().isBefore(primeiroDoDiaMaisCedo.getData())) {
                primeiroDoDiaMaisCedo = propostaDia;
            }
        }

        return planoConsultaService.buscarPorId(usuarioId, primeiroDoDiaMaisCedo.getId())
                .orElseThrow(PropostaInvalidaException::new);
    }

    @Transactional
    public void descartar(UUID usuarioId, UUID propostaId) {
        List<Plano> rascunhos = planosRascunhoDaProposta(usuarioId, propostaId);
        Instant agora = clock.instant();
        for (Plano plano : rascunhos) {
            plano.descartar(agora);
            planoRepository.save(plano);
        }
    }

    private List<Plano> planosRascunhoDaProposta(UUID usuarioId, UUID propostaId) {
        List<Plano> rascunhos = planoRepository.findByUsuarioIdAndPropostaIdAndStatus(
                usuarioId, propostaId, StatusPlano.RASCUNHO);
        if (rascunhos.isEmpty()) {
            throw new PropostaInvalidaException();
        }
        return rascunhos;
    }

    private void preservarBlocosConcluidos(UUID usuarioId, Plano propostaDia, Instant agora) {
        Optional<Plano> vigente = planoRepository.findFirstByUsuarioIdAndDataAndStatusOrderByVersaoDesc(
                usuarioId, propostaDia.getData(), StatusPlano.APROVADO);
        if (vigente.isEmpty()) {
            return;
        }

        List<BlocoPlano> blocosVigente = blocoPlanoRepository.findByPlanoIdOrderByOrdemAsc(vigente.get().getId());
        List<BlocoPlano> concluidos = new ArrayList<>();
        for (BlocoPlano bloco : blocosVigente) {
            Optional<Execucao> execucao = execucaoRepository.findByBlocoPlanoId(bloco.getId());
            if (execucao.isPresent() && execucao.get().getStatus() == StatusExecucao.CONCLUIDO) {
                concluidos.add(bloco);
            }
        }
        if (concluidos.isEmpty()) {
            return;
        }

        Set<UUID> tarefasConcluidas = new HashSet<>();
        for (BlocoPlano concluido : concluidos) {
            tarefasConcluidas.add(concluido.getTarefaId());
        }

        List<BlocoPlano> blocosProposta = new ArrayList<>(
                blocoPlanoRepository.findByPlanoIdOrderByOrdemAsc(propostaDia.getId()));
        for (BlocoPlano blocoProposta : blocosProposta) {
            if (tarefasConcluidas.contains(blocoProposta.getTarefaId())) {
                blocoPlanoRepository.delete(blocoProposta);
            }
        }
        blocoPlanoRepository.flush();

        List<BlocoPlano> restantes = new ArrayList<>(
                blocoPlanoRepository.findByPlanoIdOrderByOrdemAsc(propostaDia.getId()));
        int ordem = 1;
        for (BlocoPlano concluido : concluidos.stream()
                .sorted(Comparator.comparingInt(BlocoPlano::getOrdem))
                .toList()) {
            BlocoPlano novo = blocoPlanoRepository.saveAndFlush(new BlocoPlano(
                    UUID.randomUUID(),
                    propostaDia.getId(),
                    concluido.getTarefaId(),
                    concluido.getDuracaoMinutos(),
                    ordem++,
                    agora,
                    agora
            ));
            Execucao execucao = execucaoRepository.findByBlocoPlanoId(concluido.getId())
                    .orElseThrow();
            execucao.reatribuirBloco(novo.getId(), agora);
            execucaoRepository.save(execucao);
        }
        for (BlocoPlano restante : restantes) {
            restante.atualizarOrdem(ordem++, agora);
            blocoPlanoRepository.save(restante);
        }
    }
}
