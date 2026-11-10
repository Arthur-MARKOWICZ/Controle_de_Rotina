package com.controlerotina.projeto.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.controlerotina.projeto.dto.AtualizarProjetoRequest;
import com.controlerotina.projeto.dto.CriarProjetoRequest;
import com.controlerotina.projeto.dto.ProjetoResponse;
import com.controlerotina.projeto.entity.Projeto;
import com.controlerotina.projeto.entity.StatusProjeto;
import com.controlerotina.projeto.repository.ProjetoRepository;
import com.controlerotina.projeto.repository.ProjetoSpecs;
import com.controlerotina.shared.exception.ProjetoJaArquivadoException;
import com.controlerotina.shared.exception.ProjetoJaAtivoException;
import com.controlerotina.shared.exception.ProjetoNaoEncontradoException;
import com.controlerotina.tag.dto.TagResumoResponse;
import com.controlerotina.tag.service.TagVinculoService;

@Service
public class ProjetoService {

    private final ProjetoRepository projetoRepository;
    private final TagVinculoService tagVinculoService;
    private final Clock clock;

    public ProjetoService(
            ProjetoRepository projetoRepository,
            TagVinculoService tagVinculoService,
            Clock clock
    ) {
        this.projetoRepository = projetoRepository;
        this.tagVinculoService = tagVinculoService;
        this.clock = clock;
    }

    @Transactional
    public ProjetoResponse criar(UUID usuarioId, CriarProjetoRequest request) {
        Instant agora = clock.instant();
        Projeto projeto = new Projeto(
                UUID.randomUUID(),
                usuarioId,
                request.nome(),
                request.descricao(),
                StatusProjeto.ATIVO,
                agora,
                agora
        );
        Projeto salvo = projetoRepository.save(projeto);
        projetoRepository.flush();
        List<TagResumoResponse> tags = tagVinculoService.substituirTagsProjeto(
                usuarioId,
                salvo.getId(),
                request.tagIds()
        );
        return ProjetoResponse.from(salvo, tags, null);
    }

    @Transactional(readOnly = true)
    public Page<ProjetoResponse> listar(
            UUID usuarioId,
            StatusProjeto status,
            List<UUID> tagIds,
            Pageable pageable
    ) {
        List<UUID> filtroTagIds = tagIds == null
                ? List.of()
                : tagIds.stream().filter(Objects::nonNull).distinct().toList();
        StatusProjeto filtroStatus = status != null ? status : StatusProjeto.ATIVO;

        Specification<Projeto> spec = Specification.allOf(
                ProjetoSpecs.doUsuario(usuarioId),
                ProjetoSpecs.comStatus(filtroStatus),
                ProjetoSpecs.comAlgumaTag(filtroTagIds)
        );

        Page<Projeto> page = projetoRepository.findAll(spec, pageable);
        Map<UUID, List<TagResumoResponse>> tagsPorProjeto = tagVinculoService.mapearTagsPorProjeto(
                page.getContent().stream().map(Projeto::getId).toList()
        );

        List<ProjetoResponse> conteudo = new ArrayList<>();
        for (Projeto p : page.getContent()) {
            List<TagResumoResponse> tags = tagsPorProjeto.getOrDefault(p.getId(), List.of());
            Integer score = filtroTagIds.isEmpty()
                    ? null
                    : TagVinculoService.matchScore(
                            tags.stream().map(TagResumoResponse::id).toList(),
                            filtroTagIds
                    );
            conteudo.add(ProjetoResponse.from(p, tags, score));
        }

        if (filtroTagIds.size() >= 2) {
            conteudo.sort(Comparator
                    .comparing((ProjetoResponse r) -> r.tagMatchScore() == null ? 0 : r.tagMatchScore())
                    .reversed());
        }

        return new PageImpl<>(conteudo, pageable, page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ProjetoResponse buscarPorId(UUID usuarioId, UUID id) {
        Projeto projeto = buscarDoUsuario(usuarioId, id);
        return ProjetoResponse.from(
                projeto,
                tagVinculoService.listarTagsProjeto(projeto.getId()),
                null
        );
    }

    @Transactional
    public ProjetoResponse atualizar(UUID usuarioId, UUID id, AtualizarProjetoRequest request) {
        Projeto projeto = buscarDoUsuario(usuarioId, id);
        projeto.atualizar(request.nome(), request.descricao(), clock.instant());
        List<TagResumoResponse> tags = request.tagIdsPresente()
                ? tagVinculoService.substituirTagsProjeto(usuarioId, projeto.getId(), request.tagIds())
                : tagVinculoService.listarTagsProjeto(projeto.getId());
        return ProjetoResponse.from(projeto, tags, null);
    }

    @Transactional
    public ProjetoResponse arquivar(UUID usuarioId, UUID id) {
        Projeto projeto = buscarDoUsuario(usuarioId, id);
        if (projeto.getStatus() == StatusProjeto.ARQUIVADO) {
            throw new ProjetoJaArquivadoException();
        }
        projeto.arquivar(clock.instant());
        return ProjetoResponse.from(
                projeto,
                tagVinculoService.listarTagsProjeto(projeto.getId()),
                null
        );
    }

    @Transactional
    public ProjetoResponse reativar(UUID usuarioId, UUID id) {
        Projeto projeto = buscarDoUsuario(usuarioId, id);
        if (projeto.getStatus() == StatusProjeto.ATIVO) {
            throw new ProjetoJaAtivoException();
        }
        projeto.reativar(clock.instant());
        return ProjetoResponse.from(
                projeto,
                tagVinculoService.listarTagsProjeto(projeto.getId()),
                null
        );
    }

    private Projeto buscarDoUsuario(UUID usuarioId, UUID id) {
        return projetoRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(ProjetoNaoEncontradoException::new);
    }
}
