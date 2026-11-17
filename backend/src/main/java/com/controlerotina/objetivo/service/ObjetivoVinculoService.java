package com.controlerotina.objetivo.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.controlerotina.habito.entity.Habito;
import com.controlerotina.habito.repository.HabitoRepository;
import com.controlerotina.objetivo.dto.ObjetivoItemHabitoResponse;
import com.controlerotina.objetivo.dto.ObjetivoItemProjetoResponse;
import com.controlerotina.objetivo.dto.ObjetivoItemTarefaResponse;
import com.controlerotina.objetivo.dto.ObjetivoItensResponse;
import com.controlerotina.objetivo.dto.ObjetivoResumoResponse;
import com.controlerotina.objetivo.dto.OrigemVinculoObjetivo;
import com.controlerotina.objetivo.entity.Objetivo;
import com.controlerotina.objetivo.entity.ObjetivoHabito;
import com.controlerotina.objetivo.entity.ObjetivoProjeto;
import com.controlerotina.objetivo.entity.ObjetivoTarefa;
import com.controlerotina.objetivo.entity.StatusObjetivo;
import com.controlerotina.objetivo.repository.ObjetivoHabitoRepository;
import com.controlerotina.objetivo.repository.ObjetivoProjetoRepository;
import com.controlerotina.objetivo.repository.ObjetivoRepository;
import com.controlerotina.objetivo.repository.ObjetivoTarefaRepository;
import com.controlerotina.projeto.entity.Projeto;
import com.controlerotina.projeto.repository.ProjetoRepository;
import com.controlerotina.shared.exception.ObjetivoNaoAtivoException;
import com.controlerotina.shared.exception.ObjetivoNaoEncontradoException;
import com.controlerotina.tarefa.entity.Tarefa;
import com.controlerotina.tarefa.repository.TarefaRepository;

@Service
public class ObjetivoVinculoService {

    private static final Comparator<Tarefa> POR_CREATED_AT_TAREFA =
            Comparator.comparing(Tarefa::getCreatedAt, Comparator.nullsLast(Instant::compareTo));
    private static final Comparator<Habito> POR_CREATED_AT_HABITO =
            Comparator.comparing(Habito::getCreatedAt, Comparator.nullsLast(Instant::compareTo));
    private static final Comparator<Projeto> POR_CREATED_AT_PROJETO =
            Comparator.comparing(Projeto::getCreatedAt, Comparator.nullsLast(Instant::compareTo));

    private final ObjetivoRepository objetivoRepository;
    private final ObjetivoTarefaRepository objetivoTarefaRepository;
    private final ObjetivoHabitoRepository objetivoHabitoRepository;
    private final ObjetivoProjetoRepository objetivoProjetoRepository;
    private final TarefaRepository tarefaRepository;
    private final HabitoRepository habitoRepository;
    private final ProjetoRepository projetoRepository;

    public ObjetivoVinculoService(
            ObjetivoRepository objetivoRepository,
            ObjetivoTarefaRepository objetivoTarefaRepository,
            ObjetivoHabitoRepository objetivoHabitoRepository,
            ObjetivoProjetoRepository objetivoProjetoRepository,
            TarefaRepository tarefaRepository,
            HabitoRepository habitoRepository,
            ProjetoRepository projetoRepository
    ) {
        this.objetivoRepository = objetivoRepository;
        this.objetivoTarefaRepository = objetivoTarefaRepository;
        this.objetivoHabitoRepository = objetivoHabitoRepository;
        this.objetivoProjetoRepository = objetivoProjetoRepository;
        this.tarefaRepository = tarefaRepository;
        this.habitoRepository = habitoRepository;
        this.projetoRepository = projetoRepository;
    }

    @Transactional
    public List<ObjetivoResumoResponse> substituirObjetivosTarefa(
            UUID usuarioId,
            UUID tarefaId,
            List<UUID> objetivoIds
    ) {
        Set<UUID> atuais = objetivoTarefaRepository.findByTarefaId(tarefaId).stream()
                .map(ObjetivoTarefa::getObjetivoId)
                .collect(Collectors.toSet());
        List<Objetivo> objetivos = validarObjetivosDoUsuario(usuarioId, objetivoIds, atuais);
        objetivoTarefaRepository.deleteAllByTarefaId(tarefaId);
        if (!objetivos.isEmpty()) {
            objetivoTarefaRepository.saveAll(objetivos.stream()
                    .map(objetivo -> new ObjetivoTarefa(objetivo.getId(), tarefaId))
                    .toList());
        }
        return resumir(objetivos);
    }

    @Transactional
    public List<ObjetivoResumoResponse> substituirObjetivosHabito(
            UUID usuarioId,
            UUID habitoId,
            List<UUID> objetivoIds
    ) {
        Set<UUID> atuais = objetivoHabitoRepository.findByHabitoId(habitoId).stream()
                .map(ObjetivoHabito::getObjetivoId)
                .collect(Collectors.toSet());
        List<Objetivo> objetivos = validarObjetivosDoUsuario(usuarioId, objetivoIds, atuais);
        objetivoHabitoRepository.deleteAllByHabitoId(habitoId);
        if (!objetivos.isEmpty()) {
            objetivoHabitoRepository.saveAll(objetivos.stream()
                    .map(objetivo -> new ObjetivoHabito(objetivo.getId(), habitoId))
                    .toList());
        }
        return resumir(objetivos);
    }

    @Transactional
    public List<ObjetivoResumoResponse> substituirObjetivosProjeto(
            UUID usuarioId,
            UUID projetoId,
            List<UUID> objetivoIds
    ) {
        Set<UUID> atuais = objetivoProjetoRepository.findByProjetoId(projetoId).stream()
                .map(ObjetivoProjeto::getObjetivoId)
                .collect(Collectors.toSet());
        List<Objetivo> objetivos = validarObjetivosDoUsuario(usuarioId, objetivoIds, atuais);
        objetivoProjetoRepository.deleteAllByProjetoId(projetoId);
        if (!objetivos.isEmpty()) {
            objetivoProjetoRepository.saveAll(objetivos.stream()
                    .map(objetivo -> new ObjetivoProjeto(objetivo.getId(), projetoId))
                    .toList());
        }
        return resumir(objetivos);
    }

    @Transactional(readOnly = true)
    public List<ObjetivoResumoResponse> listarObjetivosTarefa(UUID tarefaId) {
        return resumirIds(objetivoTarefaRepository.findByTarefaId(tarefaId).stream()
                .map(ObjetivoTarefa::getObjetivoId)
                .toList());
    }

    @Transactional(readOnly = true)
    public List<ObjetivoResumoResponse> listarObjetivosHabito(UUID habitoId) {
        return resumirIds(objetivoHabitoRepository.findByHabitoId(habitoId).stream()
                .map(ObjetivoHabito::getObjetivoId)
                .toList());
    }

    @Transactional(readOnly = true)
    public List<ObjetivoResumoResponse> listarObjetivosProjeto(UUID projetoId) {
        return resumirIds(objetivoProjetoRepository.findByProjetoId(projetoId).stream()
                .map(ObjetivoProjeto::getObjetivoId)
                .toList());
    }

    @Transactional(readOnly = true)
    public Map<UUID, List<ObjetivoResumoResponse>> mapearObjetivosPorTarefa(Collection<UUID> tarefaIds) {
        if (tarefaIds == null || tarefaIds.isEmpty()) {
            return Map.of();
        }
        return mapear(
                objetivoTarefaRepository.findByTarefaIdIn(tarefaIds),
                ObjetivoTarefa::getTarefaId,
                ObjetivoTarefa::getObjetivoId
        );
    }

    @Transactional(readOnly = true)
    public Map<UUID, List<ObjetivoResumoResponse>> mapearObjetivosPorHabito(Collection<UUID> habitoIds) {
        if (habitoIds == null || habitoIds.isEmpty()) {
            return Map.of();
        }
        return mapear(
                objetivoHabitoRepository.findByHabitoIdIn(habitoIds),
                ObjetivoHabito::getHabitoId,
                ObjetivoHabito::getObjetivoId
        );
    }

    @Transactional(readOnly = true)
    public Map<UUID, List<ObjetivoResumoResponse>> mapearObjetivosPorProjeto(Collection<UUID> projetoIds) {
        if (projetoIds == null || projetoIds.isEmpty()) {
            return Map.of();
        }
        return mapear(
                objetivoProjetoRepository.findByProjetoIdIn(projetoIds),
                ObjetivoProjeto::getProjetoId,
                ObjetivoProjeto::getObjetivoId
        );
    }

    @Transactional(readOnly = true)
    public ObjetivoItensResponse listarItens(UUID usuarioId, UUID objetivoId) {
        objetivoRepository.findByIdAndUsuarioId(objetivoId, usuarioId)
                .orElseThrow(ObjetivoNaoEncontradoException::new);

        List<UUID> tarefaIdsDiretos = objetivoTarefaRepository.findByObjetivoId(objetivoId).stream()
                .map(ObjetivoTarefa::getTarefaId)
                .toList();
        List<UUID> habitoIds = objetivoHabitoRepository.findByObjetivoId(objetivoId).stream()
                .map(ObjetivoHabito::getHabitoId)
                .toList();
        List<UUID> projetoIds = objetivoProjetoRepository.findByObjetivoId(objetivoId).stream()
                .map(ObjetivoProjeto::getProjetoId)
                .toList();

        List<Tarefa> tarefasDiretas = carregarTarefasRaizAtivas(tarefaIdsDiretos);
        Set<UUID> idsDiretos = tarefasDiretas.stream().map(Tarefa::getId).collect(Collectors.toSet());

        List<Projeto> projetos = carregarProjetos(projetoIds);
        List<Tarefa> herdadas = carregarHerdadas(projetoIds, idsDiretos);
        List<Habito> habitos = carregarHabitosAtivos(habitoIds);

        Map<UUID, Projeto> projetosPorId = new HashMap<>();
        for (Projeto projeto : projetos) {
            projetosPorId.put(projeto.getId(), projeto);
        }
        Set<UUID> projetosNasTarefas = Stream.concat(tarefasDiretas.stream(), herdadas.stream())
                .map(Tarefa::getProjetoId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        projetosNasTarefas.removeAll(projetosPorId.keySet());
        for (Projeto extra : carregarProjetos(projetosNasTarefas)) {
            projetosPorId.put(extra.getId(), extra);
        }

        List<ObjetivoItemTarefaResponse> tarefas = new ArrayList<>();
        for (Tarefa tarefa : tarefasDiretas) {
            tarefas.add(itemTarefa(tarefa, OrigemVinculoObjetivo.DIRETO, projetosPorId));
        }
        for (Tarefa tarefa : herdadas) {
            tarefas.add(itemTarefa(tarefa, OrigemVinculoObjetivo.VIA_PROJETO, projetosPorId));
        }

        List<ObjetivoItemHabitoResponse> habitoItens = habitos.stream()
                .map(habito -> new ObjetivoItemHabitoResponse(
                        habito.getId(),
                        habito.getTitulo(),
                        habito.getStatus(),
                        OrigemVinculoObjetivo.DIRETO
                ))
                .toList();

        List<ObjetivoItemProjetoResponse> projetoItens = projetos.stream()
                .map(projeto -> new ObjetivoItemProjetoResponse(
                        projeto.getId(),
                        projeto.getNome(),
                        projeto.getStatus(),
                        OrigemVinculoObjetivo.DIRETO
                ))
                .toList();

        return new ObjetivoItensResponse(tarefas, habitoItens, projetoItens);
    }

    private List<Objetivo> validarObjetivosDoUsuario(
            UUID usuarioId,
            List<UUID> objetivoIds,
            Set<UUID> atuais
    ) {
        LinkedHashSet<UUID> unicos = new LinkedHashSet<>();
        if (objetivoIds != null) {
            for (UUID id : objetivoIds) {
                if (id != null) {
                    unicos.add(id);
                }
            }
        }
        if (unicos.isEmpty()) {
            return List.of();
        }
        List<Objetivo> encontradas = objetivoRepository.findByUsuarioIdAndIdIn(usuarioId, unicos);
        if (encontradas.size() != unicos.size()) {
            throw new ObjetivoNaoEncontradoException();
        }
        Map<UUID, Objetivo> porId = encontradas.stream()
                .collect(Collectors.toMap(Objetivo::getId, Function.identity()));
        List<Objetivo> ordenados = unicos.stream()
                .map(porId::get)
                .filter(Objects::nonNull)
                .toList();
        for (Objetivo objetivo : ordenados) {
            if (!atuais.contains(objetivo.getId()) && objetivo.getStatus() != StatusObjetivo.ATIVO) {
                throw new ObjetivoNaoAtivoException();
            }
        }
        return ordenados;
    }

    private List<ObjetivoResumoResponse> resumir(List<Objetivo> objetivos) {
        return objetivos.stream().map(ObjetivoResumoResponse::from).toList();
    }

    private List<ObjetivoResumoResponse> resumirIds(List<UUID> objetivoIds) {
        if (objetivoIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, Objetivo> porId = objetivoRepository.findAllById(objetivoIds).stream()
                .collect(Collectors.toMap(Objetivo::getId, Function.identity()));
        return objetivoIds.stream()
                .map(porId::get)
                .filter(Objects::nonNull)
                .map(ObjetivoResumoResponse::from)
                .toList();
    }

    private <V> Map<UUID, List<ObjetivoResumoResponse>> mapear(
            List<V> vinculos,
            Function<V, UUID> entidadeId,
            Function<V, UUID> objetivoId
    ) {
        Map<UUID, List<UUID>> idsPorEntidade = new HashMap<>();
        Set<UUID> todosObjetivoIds = new HashSet<>();
        for (V vinculo : vinculos) {
            UUID eId = entidadeId.apply(vinculo);
            UUID oId = objetivoId.apply(vinculo);
            idsPorEntidade.computeIfAbsent(eId, ignored -> new ArrayList<>()).add(oId);
            todosObjetivoIds.add(oId);
        }
        Map<UUID, Objetivo> objetivos = objetivoRepository.findAllById(todosObjetivoIds).stream()
                .collect(Collectors.toMap(Objetivo::getId, Function.identity()));
        Map<UUID, List<ObjetivoResumoResponse>> resultado = new HashMap<>();
        for (Map.Entry<UUID, List<UUID>> entry : idsPorEntidade.entrySet()) {
            List<ObjetivoResumoResponse> lista = entry.getValue().stream()
                    .map(objetivos::get)
                    .filter(Objects::nonNull)
                    .map(ObjetivoResumoResponse::from)
                    .toList();
            resultado.put(entry.getKey(), lista);
        }
        return resultado;
    }

    private List<Tarefa> carregarTarefasRaizAtivas(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return tarefaRepository.findByIdInAndDeletedAtIsNullAndParentIdIsNull(ids).stream()
                .filter(this::tarefaRaizAtiva)
                .sorted(POR_CREATED_AT_TAREFA)
                .toList();
    }

    private List<Tarefa> carregarHerdadas(Collection<UUID> projetoIds, Set<UUID> idsDiretos) {
        if (projetoIds == null || projetoIds.isEmpty()) {
            return List.of();
        }
        return tarefaRepository.findByProjetoIdInAndDeletedAtIsNullAndParentIdIsNull(projetoIds).stream()
                .filter(this::tarefaRaizAtiva)
                .filter(tarefa -> !idsDiretos.contains(tarefa.getId()))
                .sorted(POR_CREATED_AT_TAREFA)
                .toList();
    }

    private List<Habito> carregarHabitosAtivos(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return habitoRepository.findByIdInAndDeletedAtIsNull(ids).stream()
                .filter(habito -> habito.getDeletedAt() == null)
                .sorted(POR_CREATED_AT_HABITO)
                .toList();
    }

    private List<Projeto> carregarProjetos(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Projeto> projetos = new ArrayList<>();
        projetoRepository.findAllById(ids).forEach(projetos::add);
        projetos.sort(POR_CREATED_AT_PROJETO);
        return projetos;
    }

    private boolean tarefaRaizAtiva(Tarefa tarefa) {
        return tarefa.getDeletedAt() == null && tarefa.getParentId() == null;
    }

    private static ObjetivoItemTarefaResponse itemTarefa(
            Tarefa tarefa,
            OrigemVinculoObjetivo origem,
            Map<UUID, Projeto> projetosPorId
    ) {
        UUID projetoId = tarefa.getProjetoId();
        String projetoNome = null;
        if (projetoId != null) {
            Projeto projeto = projetosPorId.get(projetoId);
            projetoNome = projeto == null ? null : projeto.getNome();
        }
        return new ObjetivoItemTarefaResponse(
                tarefa.getId(),
                tarefa.getTitulo(),
                tarefa.getStatus(),
                origem,
                projetoId,
                projetoNome
        );
    }
}
