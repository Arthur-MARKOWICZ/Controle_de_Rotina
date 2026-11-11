package com.controlerotina.tag.service;

import java.util.ArrayList;
import java.util.Collection;
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

import com.controlerotina.shared.exception.TagNaoEncontradaException;
import com.controlerotina.tag.dto.TagResumoResponse;
import com.controlerotina.tag.entity.DecidirTag;
import com.controlerotina.tag.entity.HabitoTag;
import com.controlerotina.tag.entity.ProjetoTag;
import com.controlerotina.tag.entity.Tag;
import com.controlerotina.tag.entity.TarefaTag;
import com.controlerotina.tag.repository.DecidirTagRepository;
import com.controlerotina.tag.repository.HabitoTagRepository;
import com.controlerotina.tag.repository.ProjetoTagRepository;
import com.controlerotina.tag.repository.TagRepository;
import com.controlerotina.tag.repository.TarefaTagRepository;

@Service
public class TagVinculoService {

    private final TagRepository tagRepository;
    private final TarefaTagRepository tarefaTagRepository;
    private final HabitoTagRepository habitoTagRepository;
    private final ProjetoTagRepository projetoTagRepository;
    private final DecidirTagRepository decidirTagRepository;

    public TagVinculoService(
            TagRepository tagRepository,
            TarefaTagRepository tarefaTagRepository,
            HabitoTagRepository habitoTagRepository,
            ProjetoTagRepository projetoTagRepository,
            DecidirTagRepository decidirTagRepository
    ) {
        this.tagRepository = tagRepository;
        this.tarefaTagRepository = tarefaTagRepository;
        this.habitoTagRepository = habitoTagRepository;
        this.projetoTagRepository = projetoTagRepository;
        this.decidirTagRepository = decidirTagRepository;
    }

    @Transactional
    public List<TagResumoResponse> substituirTagsTarefa(UUID usuarioId, UUID tarefaId, List<UUID> tagIds) {
        List<Tag> tags = validarTagsDoUsuario(usuarioId, tagIds);
        tarefaTagRepository.deleteAllByTarefaId(tarefaId);
        if (!tags.isEmpty()) {
            tarefaTagRepository.saveAll(tags.stream()
                    .map(tag -> new TarefaTag(tarefaId, tag.getId()))
                    .toList());
        }
        return tags.stream().map(TagResumoResponse::from).toList();
    }

    @Transactional
    public List<TagResumoResponse> substituirTagsHabito(UUID usuarioId, UUID habitoId, List<UUID> tagIds) {
        List<Tag> tags = validarTagsDoUsuario(usuarioId, tagIds);
        habitoTagRepository.deleteAllByHabitoId(habitoId);
        if (!tags.isEmpty()) {
            habitoTagRepository.saveAll(tags.stream()
                    .map(tag -> new HabitoTag(habitoId, tag.getId()))
                    .toList());
        }
        return tags.stream().map(TagResumoResponse::from).toList();
    }

    @Transactional
    public List<TagResumoResponse> substituirTagsProjeto(UUID usuarioId, UUID projetoId, List<UUID> tagIds) {
        List<Tag> tags = validarTagsDoUsuario(usuarioId, tagIds);
        projetoTagRepository.deleteAllByProjetoId(projetoId);
        if (!tags.isEmpty()) {
            projetoTagRepository.saveAll(tags.stream()
                    .map(tag -> new ProjetoTag(projetoId, tag.getId()))
                    .toList());
        }
        return tags.stream().map(TagResumoResponse::from).toList();
    }

    @Transactional
    public List<TagResumoResponse> substituirTagsDecidir(UUID usuarioId, UUID itemDecidirId, List<UUID> tagIds) {
        List<Tag> tags = validarTagsDoUsuario(usuarioId, tagIds);
        decidirTagRepository.deleteAllByItemDecidirId(itemDecidirId);
        if (!tags.isEmpty()) {
            decidirTagRepository.saveAll(tags.stream()
                    .map(tag -> new DecidirTag(itemDecidirId, tag.getId()))
                    .toList());
        }
        return tags.stream().map(TagResumoResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<TagResumoResponse> listarTagsTarefa(UUID tarefaId) {
        return resumir(tarefaTagRepository.findByTarefaId(tarefaId).stream()
                .map(TarefaTag::getTagId)
                .toList());
    }

    @Transactional(readOnly = true)
    public List<TagResumoResponse> listarTagsHabito(UUID habitoId) {
        return resumir(habitoTagRepository.findByHabitoId(habitoId).stream()
                .map(HabitoTag::getTagId)
                .toList());
    }

    @Transactional(readOnly = true)
    public List<TagResumoResponse> listarTagsProjeto(UUID projetoId) {
        return resumir(projetoTagRepository.findByProjetoId(projetoId).stream()
                .map(ProjetoTag::getTagId)
                .toList());
    }

    @Transactional(readOnly = true)
    public List<TagResumoResponse> listarTagsDecidir(UUID itemDecidirId) {
        return resumir(decidirTagRepository.findByItemDecidirId(itemDecidirId).stream()
                .map(DecidirTag::getTagId)
                .toList());
    }

    @Transactional(readOnly = true)
    public Map<UUID, List<TagResumoResponse>> mapearTagsPorTarefa(Collection<UUID> tarefaIds) {
        if (tarefaIds == null || tarefaIds.isEmpty()) {
            return Map.of();
        }
        List<TarefaTag> vinculos = tarefaTagRepository.findByTarefaIdIn(tarefaIds);
        return mapear(vinculos, TarefaTag::getTarefaId, TarefaTag::getTagId);
    }

    @Transactional(readOnly = true)
    public Map<UUID, List<TagResumoResponse>> mapearTagsPorHabito(Collection<UUID> habitoIds) {
        if (habitoIds == null || habitoIds.isEmpty()) {
            return Map.of();
        }
        List<HabitoTag> vinculos = habitoTagRepository.findByHabitoIdIn(habitoIds);
        return mapear(vinculos, HabitoTag::getHabitoId, HabitoTag::getTagId);
    }

    @Transactional(readOnly = true)
    public Map<UUID, List<TagResumoResponse>> mapearTagsPorProjeto(Collection<UUID> projetoIds) {
        if (projetoIds == null || projetoIds.isEmpty()) {
            return Map.of();
        }
        List<ProjetoTag> vinculos = projetoTagRepository.findByProjetoIdIn(projetoIds);
        return mapear(vinculos, ProjetoTag::getProjetoId, ProjetoTag::getTagId);
    }

    @Transactional(readOnly = true)
    public Map<UUID, List<TagResumoResponse>> mapearTagsPorDecidir(Collection<UUID> itemDecidirIds) {
        if (itemDecidirIds == null || itemDecidirIds.isEmpty()) {
            return Map.of();
        }
        List<DecidirTag> vinculos = decidirTagRepository.findByItemDecidirIdIn(itemDecidirIds);
        return mapear(vinculos, DecidirTag::getItemDecidirId, DecidirTag::getTagId);
    }

    @Transactional(readOnly = true)
    public Set<UUID> tarefaIdsComAlgumaTag(Collection<UUID> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(tarefaTagRepository.findTarefaIdsComAlgumaTag(tagIds));
    }

    @Transactional(readOnly = true)
    public Set<UUID> habitoIdsComAlgumaTag(Collection<UUID> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(habitoTagRepository.findHabitoIdsComAlgumaTag(tagIds));
    }

    @Transactional(readOnly = true)
    public Set<UUID> projetoIdsComAlgumaTag(Collection<UUID> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(projetoTagRepository.findProjetoIdsComAlgumaTag(tagIds));
    }

    @Transactional(readOnly = true)
    public Set<UUID> decidirIdsComAlgumaTag(Collection<UUID> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(decidirTagRepository.findItemDecidirIdsComAlgumaTag(tagIds));
    }

    public static int matchScore(Collection<UUID> tagsDoItem, Collection<UUID> filtro) {
        if (filtro == null || filtro.isEmpty()) {
            return 0;
        }
        if (filtro.size() == 1) {
            return tagsDoItem != null && tagsDoItem.contains(filtro.iterator().next()) ? 1 : 0;
        }
        Set<UUID> filtroSet = new HashSet<>(filtro);
        long hits = tagsDoItem == null ? 0 : tagsDoItem.stream().filter(filtroSet::contains).count();
        if (hits == 0) {
            return 0;
        }
        return hits == filtroSet.size() ? 2 : 1;
    }

    private List<Tag> validarTagsDoUsuario(UUID usuarioId, List<UUID> tagIds) {
        LinkedHashSet<UUID> unicos = new LinkedHashSet<>();
        if (tagIds != null) {
            for (UUID id : tagIds) {
                if (id != null) {
                    unicos.add(id);
                }
            }
        }
        if (unicos.isEmpty()) {
            return List.of();
        }
        List<Tag> encontradas = tagRepository.findByUsuarioIdAndIdIn(usuarioId, unicos);
        if (encontradas.size() != unicos.size()) {
            throw new TagNaoEncontradaException();
        }
        Map<UUID, Tag> porId = encontradas.stream()
                .collect(Collectors.toMap(Tag::getId, Function.identity()));
        return unicos.stream().map(porId::get).filter(Objects::nonNull).toList();
    }

    private List<TagResumoResponse> resumir(List<UUID> tagIds) {
        if (tagIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, Tag> porId = tagRepository.findAllById(tagIds).stream()
                .collect(Collectors.toMap(Tag::getId, Function.identity()));
        return tagIds.stream()
                .map(porId::get)
                .filter(Objects::nonNull)
                .map(TagResumoResponse::from)
                .toList();
    }

    private <V> Map<UUID, List<TagResumoResponse>> mapear(
            List<V> vinculos,
            Function<V, UUID> entidadeId,
            Function<V, UUID> tagId
    ) {
        Map<UUID, List<UUID>> idsPorEntidade = new HashMap<>();
        Set<UUID> todosTagIds = new HashSet<>();
        for (V vinculo : vinculos) {
            UUID eId = entidadeId.apply(vinculo);
            UUID tId = tagId.apply(vinculo);
            idsPorEntidade.computeIfAbsent(eId, ignored -> new ArrayList<>()).add(tId);
            todosTagIds.add(tId);
        }
        Map<UUID, Tag> tags = tagRepository.findAllById(todosTagIds).stream()
                .collect(Collectors.toMap(Tag::getId, Function.identity()));
        Map<UUID, List<TagResumoResponse>> resultado = new HashMap<>();
        for (Map.Entry<UUID, List<UUID>> entry : idsPorEntidade.entrySet()) {
            List<TagResumoResponse> lista = entry.getValue().stream()
                    .map(tags::get)
                    .filter(Objects::nonNull)
                    .map(TagResumoResponse::from)
                    .toList();
            resultado.put(entry.getKey(), lista);
        }
        return resultado;
    }
}
