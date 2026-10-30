package com.controlerotina.planner.service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.controlerotina.planner.dto.BlocoPlanoResponse;
import com.controlerotina.planner.dto.PlanoResponse;
import com.controlerotina.planner.entity.BlocoPlano;
import com.controlerotina.planner.entity.Plano;
import com.controlerotina.planner.entity.StatusPlano;
import com.controlerotina.planner.repository.BlocoPlanoRepository;
import com.controlerotina.planner.repository.PlanoRepository;
import com.controlerotina.projeto.entity.Projeto;
import com.controlerotina.projeto.repository.ProjetoRepository;
import com.controlerotina.shared.exception.PlanoNaoEncontradoException;
import com.controlerotina.tarefa.entity.Tarefa;
import com.controlerotina.tarefa.repository.TarefaRepository;

@Service
public class PlanoConsultaService {

    private static final List<StatusPlano> STATUS_CORRENTE = List.of(
            StatusPlano.RASCUNHO,
            StatusPlano.APROVADO
    );

    private final PlanoRepository planoRepository;
    private final BlocoPlanoRepository blocoPlanoRepository;
    private final TarefaRepository tarefaRepository;
    private final ProjetoRepository projetoRepository;

    public PlanoConsultaService(
            PlanoRepository planoRepository,
            BlocoPlanoRepository blocoPlanoRepository,
            TarefaRepository tarefaRepository,
            ProjetoRepository projetoRepository
    ) {
        this.planoRepository = planoRepository;
        this.blocoPlanoRepository = blocoPlanoRepository;
        this.tarefaRepository = tarefaRepository;
        this.projetoRepository = projetoRepository;
    }

    @Transactional(readOnly = true)
    public Optional<PlanoResponse> buscarPorData(UUID usuarioId, LocalDate data, Integer versao) {
        Optional<Plano> plano = versao == null
                ? planoRepository.findFirstByUsuarioIdAndDataAndStatusInOrderByVersaoDesc(
                        usuarioId, data, STATUS_CORRENTE)
                : planoRepository.findByUsuarioIdAndDataAndVersao(usuarioId, data, versao)
                        .filter(p -> p.getStatus() != StatusPlano.DESCARTADO);

        return plano.map(this::montarResposta);
    }

    @Transactional(readOnly = true)
    public Optional<PlanoResponse> buscarPorId(UUID usuarioId, UUID id) {
        return planoRepository.findByIdAndUsuarioId(id, usuarioId)
                .filter(p -> p.getStatus() != StatusPlano.DESCARTADO)
                .map(this::montarResposta);
    }

    private PlanoResponse montarResposta(Plano plano) {
        List<BlocoPlano> blocos = blocoPlanoRepository.findByPlanoIdOrderByOrdemAsc(plano.getId());
        Map<UUID, Tarefa> tarefas = tarefaRepository.findAllById(
                        blocos.stream().map(BlocoPlano::getTarefaId).distinct().toList()
                ).stream()
                .collect(Collectors.toMap(Tarefa::getId, Function.identity()));

        Map<UUID, String> nomesProjeto = nomesDosProjetos(tarefas.values());

        List<BlocoPlanoResponse> blocosResponse = blocos.stream()
                .map(bloco -> {
                    Tarefa tarefa = tarefas.get(bloco.getTarefaId());
                    if (tarefa == null) {
                        throw new PlanoNaoEncontradoException();
                    }
                    String projetoNome = tarefa.getProjetoId() == null
                            ? null
                            : nomesProjeto.get(tarefa.getProjetoId());
                    return BlocoPlanoResponse.from(bloco, tarefa, projetoNome);
                })
                .toList();

        boolean propostaPendente = plano.getStatus() == StatusPlano.RASCUNHO
                && plano.getPropostaId() != null
                && planoRepository.existsByUsuarioIdAndDataAndStatus(
                        plano.getUsuarioId(), plano.getData(), StatusPlano.APROVADO);

        return PlanoResponse.from(plano, blocosResponse, propostaPendente);
    }

    private Map<UUID, String> nomesDosProjetos(Collection<Tarefa> tarefas) {
        Set<UUID> ids = tarefas.stream()
                .map(Tarefa::getProjetoId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return projetoRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Projeto::getId, Projeto::getNome));
    }
}
