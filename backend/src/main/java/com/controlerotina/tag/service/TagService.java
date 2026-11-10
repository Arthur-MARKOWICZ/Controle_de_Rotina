package com.controlerotina.tag.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.controlerotina.shared.exception.TagNomeDuplicadoException;
import com.controlerotina.shared.exception.TagNaoEncontradaException;
import com.controlerotina.tag.dto.AtualizarTagRequest;
import com.controlerotina.tag.dto.CriarTagRequest;
import com.controlerotina.tag.dto.TagResponse;
import com.controlerotina.tag.entity.Tag;
import com.controlerotina.tag.repository.HabitoTagRepository;
import com.controlerotina.tag.repository.ProjetoTagRepository;
import com.controlerotina.tag.repository.TagRepository;
import com.controlerotina.tag.repository.TarefaTagRepository;

@Service
public class TagService {

    private final TagRepository tagRepository;
    private final TarefaTagRepository tarefaTagRepository;
    private final HabitoTagRepository habitoTagRepository;
    private final ProjetoTagRepository projetoTagRepository;
    private final Clock clock;

    public TagService(
            TagRepository tagRepository,
            TarefaTagRepository tarefaTagRepository,
            HabitoTagRepository habitoTagRepository,
            ProjetoTagRepository projetoTagRepository,
            Clock clock
    ) {
        this.tagRepository = tagRepository;
        this.tarefaTagRepository = tarefaTagRepository;
        this.habitoTagRepository = habitoTagRepository;
        this.projetoTagRepository = projetoTagRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TagResponse> listar(UUID usuarioId) {
        return tagRepository.findByUsuarioIdOrderByNomeAsc(usuarioId).stream()
                .map(TagResponse::from)
                .toList();
    }

    @Transactional
    public TagResponse criar(UUID usuarioId, CriarTagRequest request) {
        String nomeNormalizado = normalizarNome(request.nome());
        if (tagRepository.existsByUsuarioIdAndNomeNormalizado(usuarioId, nomeNormalizado)) {
            throw new TagNomeDuplicadoException(request.nome());
        }
        Instant agora = clock.instant();
        Tag tag = new Tag(
                UUID.randomUUID(),
                usuarioId,
                request.nome(),
                nomeNormalizado,
                request.cor(),
                agora,
                agora
        );
        return TagResponse.from(tagRepository.save(tag));
    }

    @Transactional
    public TagResponse atualizar(UUID usuarioId, UUID id, AtualizarTagRequest request) {
        Tag tag = buscarDoUsuario(usuarioId, id);
        String nome = request.nome();
        String nomeNormalizado = null;
        if (nome != null) {
            nomeNormalizado = normalizarNome(nome);
            if (tagRepository.existsByUsuarioIdAndNomeNormalizadoAndIdNot(usuarioId, nomeNormalizado, id)) {
                throw new TagNomeDuplicadoException(nome);
            }
        }
        tag.atualizar(nome, nomeNormalizado, request.cor(), clock.instant());
        return TagResponse.from(tag);
    }

    @Transactional
    public void excluir(UUID usuarioId, UUID id) {
        Tag tag = buscarDoUsuario(usuarioId, id);
        tarefaTagRepository.deleteByTagId(tag.getId());
        habitoTagRepository.deleteByTagId(tag.getId());
        projetoTagRepository.deleteByTagId(tag.getId());
        tagRepository.delete(tag);
    }

    private Tag buscarDoUsuario(UUID usuarioId, UUID id) {
        return tagRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(TagNaoEncontradaException::new);
    }

    static String normalizarNome(String nome) {
        return nome.trim().toLowerCase(Locale.ROOT);
    }
}
