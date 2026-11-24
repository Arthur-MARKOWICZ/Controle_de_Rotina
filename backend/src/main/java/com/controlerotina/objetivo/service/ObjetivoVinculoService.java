package com.controlerotina.objetivo.service;

import java.time.Clock;
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
    private final Clock clock;

    public ObjetivoVinculoService(
            ObjetivoRepository objetivoRepository,
            ObjetivoTarefaRepository objetivoTarefaRepository,
            ObjetivoHabitoRepository objetivoHabitoRepository,
            ObjetivoProjetoRepository objetivoProjetoRepository,
            TarefaRepository tarefaRepository,
            HabitoRepository habitoRepository,
            ProjetoRepository projetoRepository,
            Clock clock
    ) {
        this.objetivoRepository = objetivoRepository;
        this.objetivoTarefaRepository = objetivoTarefaRepository;
        this.objetivoHabitoRepository = objetivoHabitoRepository;
        this.objetivoProjetoRepository = objetivoProjetoRepository;
        this.tarefaRepository = tarefaRepository;
        this.habitoRepository = habitoRepository;
        this.projetoRepository = projetoRepository;
        this.clock = clock;
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
        Set<UUID> incoming = objetivos.stream()
                .map(Objetivo::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<UUID> aRemover = new HashSet<>(atuais);
        aRemover.removeAll(incoming);
        if (!aRemover.isEmpty()) {
            objetivoHabitoRepository.deleteByHabitoIdAndObjetivoIdIn(habitoId, aRemover);
        }

        Set<UUID> aInserir = new LinkedHashSet<>(incoming);
        aInserir.removeAll(atuais);
        if (!aInserir.isEmpty()) {
            Instant agora = clock.instant();
            objetivoHabitoRepository.saveAll(aInserir.stream()
                    .map(objetivoId -> new ObjetivoHabito(objetivoId, habitoId, agora))
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
        return toItens(resolverConjunto(objetivoId));
    }

    @Transactional(readOnly = true)
    public ObjetivoConjunto resolverConjunto(UUID objetivoId) {
        return resolverConjuntos(List.of(objetivoId)).getOrDefault(objetivoId, ObjetivoConjunto.vazio());
    }

    @Transactional(readOnly = true)
    public Map<UUID, ObjetivoConjunto> resolverConjuntos(Collection<UUID> objetivoIds) {
        if (objetivoIds == null || objetivoIds.isEmpty()) {
            return Map.of();
        }
        List<ObjetivoTarefa> vinculosTarefa = objetivoTarefaRepository.findByObjetivoIdIn(objetivoIds);
        List<ObjetivoHabito> vinculosHabito = objetivoHabitoRepository.findByObjetivoIdIn(objetivoIds);
        List<ObjetivoProjeto> vinculosProjeto = objetivoProjetoRepository.findByObjetivoIdIn(objetivoIds);

        Map<UUID, List<UUID>> tarefaIdsPorObjetivo = agruparIds(vinculosTarefa, ObjetivoTarefa::getObjetivoId, ObjetivoTarefa::getTarefaId);
        Map<UUID, List<ObjetivoHabito>> habitosPorObjetivo = agrupar(vinculosHabito, ObjetivoHabito::getObjetivoId);
        Map<UUID, List<UUID>> projetoIdsPorObjetivo = agruparIds(vinculosProjeto, ObjetivoProjeto::getObjetivoId, ObjetivoProjeto::getProjetoId);

        Set<UUID> todosTarefaIds = vinculosTarefa.stream().map(ObjetivoTarefa::getTarefaId).collect(Collectors.toSet());
        Set<UUID> todosHabitoIds = vinculosHabito.stream().map(ObjetivoHabito::getHabitoId).collect(Collectors.toSet());
        Set<UUID> todosProjetoIds = vinculosProjeto.stream().map(ObjetivoProjeto::getProjetoId).collect(Collectors.toSet());

        Map<UUID, Tarefa> tarefasDiretasPorId = carregarTarefasRaizAtivas(todosTarefaIds).stream()
                .collect(Collectors.toMap(Tarefa::getId, Function.identity()));
        Map<UUID, Habito> habitosPorId = carregarHabitosAtivos(todosHabitoIds).stream()
                .collect(Collectors.toMap(Habito::getId, Function.identity()));
        Map<UUID, Projeto> projetosPorId = new HashMap<>();
        for (Projeto projeto : carregarProjetos(todosProjetoIds)) {
            projetosPorId.put(projeto.getId(), projeto);
        }

        Map<UUID, List<Tarefa>> herdadasPorProjeto = new HashMap<>();
        for (Tarefa herdada : carregarHerdadas(todosProjetoIds, Set.of())) {
            if (herdada.getProjetoId() == null) {
                continue;
            }
            herdadasPorProjeto.computeIfAbsent(herdada.getProjetoId(), ignored -> new ArrayList<>()).add(herdada);
        }

        Set<UUID> projetosNasTarefas = new HashSet<>();
        for (Tarefa tarefa : tarefasDiretasPorId.values()) {
            if (tarefa.getProjetoId() != null) {
                projetosNasTarefas.add(tarefa.getProjetoId());
            }
        }
        for (List<Tarefa> herdadas : herdadasPorProjeto.values()) {
            for (Tarefa tarefa : herdadas) {
                if (tarefa.getProjetoId() != null) {
                    projetosNasTarefas.add(tarefa.getProjetoId());
                }
            }
        }
        projetosNasTarefas.removeAll(projetosPorId.keySet());
        for (Projeto extra : carregarProjetos(projetosNasTarefas)) {
            projetosPorId.put(extra.getId(), extra);
        }

        Map<UUID, ObjetivoConjunto> resultado = new HashMap<>();
        for (UUID objetivoId : objetivoIds) {
            if (objetivoId == null) {
                continue;
            }
            resultado.put(objetivoId, montarConjunto(
                    tarefaIdsPorObjetivo.getOrDefault(objetivoId, List.of()),
                    habitosPorObjetivo.getOrDefault(objetivoId, List.of()),
                    projetoIdsPorObjetivo.getOrDefault(objetivoId, List.of()),
                    tarefasDiretasPorId,
                    habitosPorId,
                    projetosPorId,
                    herdadasPorProjeto
            ));
        }
        return resultado;
    }

    private ObjetivoConjunto montarConjunto(
            List<UUID> tarefaIdsDiretos,
            List<ObjetivoHabito> vinculosHabito,
            List<UUID> projetoIds,
            Map<UUID, Tarefa> tarefasDiretasPorId,
            Map<UUID, Habito> habitosPorId,
            Map<UUID, Projeto> projetosPorId,
            Map<UUID, List<Tarefa>> herdadasPorProjeto
    ) {
        List<Tarefa> tarefasDiretas = tarefaIdsDiretos.stream()
                .map(tarefasDiretasPorId::get)
                .filter(Objects::nonNull)
                .sorted(POR_CREATED_AT_TAREFA)
                .toList();
        Set<UUID> idsDiretos = tarefasDiretas.stream().map(Tarefa::getId).collect(Collectors.toSet());

        List<Projeto> projetos = projetoIds.stream()
                .map(projetosPorId::get)
                .filter(Objects::nonNull)
                .sorted(POR_CREATED_AT_PROJETO)
                .toList();

        List<Tarefa> herdadas = projetoIds.stream()
                .flatMap(projetoId -> herdadasPorProjeto.getOrDefault(projetoId, List.of()).stream())
                .filter(tarefa -> !idsDiretos.contains(tarefa.getId()))
                .sorted(POR_CREATED_AT_TAREFA)
                .toList();

        List<ObjetivoConjunto.TarefaRaiz> tarefasRaiz = new ArrayList<>();
        for (Tarefa tarefa : tarefasDiretas) {
            tarefasRaiz.add(tarefaRaiz(tarefa, OrigemVinculoObjetivo.DIRETO, projetosPorId));
        }
        for (Tarefa tarefa : herdadas) {
            tarefasRaiz.add(tarefaRaiz(tarefa, OrigemVinculoObjetivo.VIA_PROJETO, projetosPorId));
        }

        List<ObjetivoConjunto.HabitoVinculado> habitos = vinculosHabito.stream()
                .map(vinculo -> {
                    Habito habito = habitosPorId.get(vinculo.getHabitoId());
                    return habito == null ? null : new ObjetivoConjunto.HabitoVinculado(habito, vinculo.getCreatedAt());
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(h -> h.habito().getCreatedAt(), Comparator.nullsLast(Instant::compareTo)))
                .toList();

        return new ObjetivoConjunto(tarefasRaiz, habitos, projetos);
    }

    private ObjetivoItensResponse toItens(ObjetivoConjunto conjunto) {
        List<ObjetivoItemTarefaResponse> tarefas = conjunto.tarefasRaiz().stream()
                .map(item -> new ObjetivoItemTarefaResponse(
                        item.tarefa().getId(),
                        item.tarefa().getTitulo(),
                        item.tarefa().getStatus(),
                        item.origem(),
                        item.tarefa().getProjetoId(),
                        item.projetoNome()
                ))
                .toList();
        List<ObjetivoItemHabitoResponse> habitos = conjunto.habitos().stream()
                .map(item -> new ObjetivoItemHabitoResponse(
                        item.habito().getId(),
                        item.habito().getTitulo(),
                        item.habito().getStatus(),
                        OrigemVinculoObjetivo.DIRETO
                ))
                .toList();
        List<ObjetivoItemProjetoResponse> projetos = conjunto.projetos().stream()
                .map(projeto -> new ObjetivoItemProjetoResponse(
                        projeto.getId(),
                        projeto.getNome(),
                        projeto.getStatus(),
                        OrigemVinculoObjetivo.DIRETO
                ))
                .toList();
        return new ObjetivoItensResponse(tarefas, habitos, projetos);
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

    private static ObjetivoConjunto.TarefaRaiz tarefaRaiz(
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
        return new ObjetivoConjunto.TarefaRaiz(tarefa, origem, projetoNome);
    }

    private static <V> Map<UUID, List<UUID>> agruparIds(
            List<V> vinculos,
            Function<V, UUID> objetivoId,
            Function<V, UUID> entidadeId
    ) {
        Map<UUID, List<UUID>> ids = new HashMap<>();
        for (V vinculo : vinculos) {
            ids.computeIfAbsent(objetivoId.apply(vinculo), ignored -> new ArrayList<>())
                    .add(entidadeId.apply(vinculo));
        }
        return ids;
    }

    private static <V> Map<UUID, List<V>> agrupar(List<V> vinculos, Function<V, UUID> objetivoId) {
        Map<UUID, List<V>> grupos = new HashMap<>();
        for (V vinculo : vinculos) {
            grupos.computeIfAbsent(objetivoId.apply(vinculo), ignored -> new ArrayList<>()).add(vinculo);
        }
        return grupos;
    }
}
