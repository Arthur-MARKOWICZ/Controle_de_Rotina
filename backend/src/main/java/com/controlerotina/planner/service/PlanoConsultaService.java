package com.controlerotina.planner.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.controlerotina.planner.dto.BlocoPlanoResponse;
import com.controlerotina.planner.dto.PlanoResponse;
import com.controlerotina.planner.entity.BlocoPlano;
import com.controlerotina.planner.entity.Plano;
import com.controlerotina.planner.repository.BlocoPlanoRepository;
import com.controlerotina.planner.repository.PlanoRepository;
import com.controlerotina.shared.exception.PlanoNaoEncontradoException;
import com.controlerotina.tarefa.entity.Tarefa;
import com.controlerotina.tarefa.repository.TarefaRepository;

@Service
public class PlanoConsultaService {

    private final PlanoRepository planoRepository;
    private final BlocoPlanoRepository blocoPlanoRepository;
    private final TarefaRepository tarefaRepository;

    public PlanoConsultaService(
            PlanoRepository planoRepository,
            BlocoPlanoRepository blocoPlanoRepository,
            TarefaRepository tarefaRepository
    ) {
        this.planoRepository = planoRepository;
        this.blocoPlanoRepository = blocoPlanoRepository;
        this.tarefaRepository = tarefaRepository;
    }

    @Transactional(readOnly = true)
    public Optional<PlanoResponse> buscarPorData(UUID usuarioId, LocalDate data, Integer versao) {
        Optional<Plano> plano = versao == null
                ? planoRepository.findFirstByUsuarioIdAndDataOrderByVersaoDesc(usuarioId, data)
                : planoRepository.findByUsuarioIdAndDataAndVersao(usuarioId, data, versao);

        return plano.map(this::montarResposta);
    }

    private PlanoResponse montarResposta(Plano plano) {
        List<BlocoPlano> blocos = blocoPlanoRepository.findByPlanoIdOrderByOrdemAsc(plano.getId());
        Map<UUID, Tarefa> tarefas = tarefaRepository.findAllById(
                        blocos.stream().map(BlocoPlano::getTarefaId).distinct().toList()
                ).stream()
                .collect(Collectors.toMap(Tarefa::getId, Function.identity()));

        List<BlocoPlanoResponse> blocosResponse = blocos.stream()
                .map(bloco -> {
                    Tarefa tarefa = tarefas.get(bloco.getTarefaId());
                    if (tarefa == null) {
                        throw new PlanoNaoEncontradoException();
                    }
                    return BlocoPlanoResponse.from(bloco, tarefa);
                })
                .toList();

        return PlanoResponse.from(plano, blocosResponse);
    }
}
