package com.controlerotina.decidir.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.controlerotina.decidir.dto.AtualizarDecidirRequest;
import com.controlerotina.decidir.dto.CriarDecidirRequest;
import com.controlerotina.decidir.dto.DecidirResponse;
import com.controlerotina.decidir.dto.PromoverDecidirRequest;
import com.controlerotina.decidir.entity.ItemDecidir;
import com.controlerotina.decidir.repository.ItemDecidirRepository;
import com.controlerotina.decidir.repository.ItemDecidirSpecs;
import com.controlerotina.projeto.entity.Projeto;
import com.controlerotina.projeto.entity.StatusProjeto;
import com.controlerotina.projeto.repository.ProjetoRepository;
import com.controlerotina.shared.exception.ItemDecidirJaPromovidoException;
import com.controlerotina.shared.exception.ItemDecidirNaoEncontradoException;
import com.controlerotina.shared.exception.ProjetoArquivadoException;
import com.controlerotina.shared.exception.ProjetoNaoEncontradoException;
import com.controlerotina.tag.dto.TagResumoResponse;
import com.controlerotina.tag.service.TagVinculoService;
import com.controlerotina.tarefa.dto.CriarTarefaRequest;
import com.controlerotina.tarefa.dto.TarefaResponse;
import com.controlerotina.tarefa.service.TarefaService;

@Service
public class DecidirService {

    private final ItemDecidirRepository itemDecidirRepository;
    private final ProjetoRepository projetoRepository;
    private final TagVinculoService tagVinculoService;
    private final TarefaService tarefaService;
    private final Clock clock;

    public DecidirService(
            ItemDecidirRepository itemDecidirRepository,
            ProjetoRepository projetoRepository,
            TagVinculoService tagVinculoService,
            TarefaService tarefaService,
            Clock clock
    ) {
        this.itemDecidirRepository = itemDecidirRepository;
        this.projetoRepository = projetoRepository;
        this.tagVinculoService = tagVinculoService;
        this.tarefaService = tarefaService;
        this.clock = clock;
    }

    @Transactional
    public DecidirResponse criar(UUID usuarioId, CriarDecidirRequest request) {
        if (request.projetoId() != null) {
            validarProjetoAtivoParaVinculo(usuarioId, request.projetoId());
        }
        Instant agora = clock.instant();
        ItemDecidir item = new ItemDecidir(
                UUID.randomUUID(),
                usuarioId,
                request.titulo(),
                request.descricao(),
                request.decidirAte(),
                request.projetoId(),
                agora,
                agora
        );
        ItemDecidir salvo = itemDecidirRepository.save(item);
        itemDecidirRepository.flush();
        List<TagResumoResponse> tags = tagVinculoService.substituirTagsDecidir(
                usuarioId,
                salvo.getId(),
                request.tagIds()
        );
        return DecidirResponse.from(salvo, tags, null);
    }

    @Transactional(readOnly = true)
    public Page<DecidirResponse> listar(
            UUID usuarioId,
            boolean promovidos,
            List<UUID> tagIds,
            Pageable pageable
    ) {
        List<UUID> filtroTagIds = tagIds == null
                ? List.of()
                : tagIds.stream().filter(Objects::nonNull).distinct().toList();

        Specification<ItemDecidir> spec = Specification.allOf(
                ItemDecidirSpecs.doUsuario(usuarioId),
                promovidos ? ItemDecidirSpecs.promovidos() : ItemDecidirSpecs.abertos(),
                ItemDecidirSpecs.comAlgumaTag(filtroTagIds)
        );

        Page<ItemDecidir> pagina = itemDecidirRepository.findAll(spec, pageable);
        Map<UUID, List<TagResumoResponse>> tagsPorItem = tagVinculoService.mapearTagsPorDecidir(
                pagina.getContent().stream().map(ItemDecidir::getId).toList()
        );

        List<DecidirResponse> conteudo = new ArrayList<>();
        for (ItemDecidir item : pagina.getContent()) {
            List<TagResumoResponse> tags = tagsPorItem.getOrDefault(item.getId(), List.of());
            Integer score = filtroTagIds.isEmpty()
                    ? null
                    : TagVinculoService.matchScore(
                            tags.stream().map(TagResumoResponse::id).toList(),
                            filtroTagIds
                    );
            conteudo.add(DecidirResponse.from(item, tags, score));
        }

        if (filtroTagIds.size() >= 2) {
            conteudo.sort(Comparator
                    .comparing((DecidirResponse r) -> r.tagMatchScore() == null ? 0 : r.tagMatchScore())
                    .reversed());
        }

        return new PageImpl<>(conteudo, pageable, pagina.getTotalElements());
    }

    @Transactional(readOnly = true)
    public DecidirResponse buscarPorId(UUID usuarioId, UUID id) {
        ItemDecidir item = buscarDoUsuario(usuarioId, id);
        return DecidirResponse.from(
                item,
                tagVinculoService.listarTagsDecidir(item.getId()),
                null
        );
    }

    @Transactional
    public DecidirResponse atualizar(UUID usuarioId, UUID id, AtualizarDecidirRequest request) {
        ItemDecidir item = buscarDoUsuario(usuarioId, id);
        if (item.isPromovido()) {
            throw new ItemDecidirJaPromovidoException();
        }
        if (request.projetoIdPresente() && request.projetoId() != null) {
            validarProjetoAtivoParaVinculo(usuarioId, request.projetoId());
        }
        item.atualizar(
                request.titulo(),
                request.descricao(),
                request.descricaoPresente(),
                request.decidirAte(),
                request.decidirAtePresente(),
                request.projetoId(),
                request.projetoIdPresente(),
                clock.instant()
        );
        List<TagResumoResponse> tags = request.tagIdsPresente()
                ? tagVinculoService.substituirTagsDecidir(usuarioId, item.getId(), request.tagIds())
                : tagVinculoService.listarTagsDecidir(item.getId());
        return DecidirResponse.from(item, tags, null);
    }

    @Transactional
    public void excluir(UUID usuarioId, UUID id) {
        ItemDecidir item = buscarDoUsuario(usuarioId, id);
        itemDecidirRepository.delete(item);
    }

    @Transactional
    public DecidirResponse promover(UUID usuarioId, UUID id, PromoverDecidirRequest request) {
        ItemDecidir item = buscarDoUsuario(usuarioId, id);
        if (item.isPromovido()) {
            throw new ItemDecidirJaPromovidoException();
        }

        PromoverDecidirRequest body = request == null
                ? new PromoverDecidirRequest(null, null, null, null, null)
                : request;

        String titulo = body.titulo() != null ? body.titulo() : item.getTitulo();
        String descricao = body.descricao() != null
                ? body.descricao()
                : (item.getDescricao() == null ? "" : item.getDescricao());

        List<UUID> tagIds = tagVinculoService.listarTagsDecidir(item.getId()).stream()
                .map(TagResumoResponse::id)
                .collect(Collectors.toList());

        TarefaResponse tarefa = tarefaService.criar(
                usuarioId,
                new CriarTarefaRequest(
                        titulo,
                        descricao,
                        body.prioridade(),
                        body.prazo(),
                        body.duracaoMinutos(),
                        item.getProjetoId(),
                        tagIds,
                        null
                )
        );

        item.promover(tarefa.id(), clock.instant());
        // criar tarefa pode limpar o persistence context (deleteAll tags com clearAutomatically)
        ItemDecidir salvo = itemDecidirRepository.save(item);
        return DecidirResponse.from(
                salvo,
                tagVinculoService.listarTagsDecidir(salvo.getId()),
                null
        );
    }

    private ItemDecidir buscarDoUsuario(UUID usuarioId, UUID id) {
        return itemDecidirRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(ItemDecidirNaoEncontradoException::new);
    }

    private Projeto validarProjetoAtivoParaVinculo(UUID usuarioId, UUID projetoId) {
        Projeto projeto = projetoRepository.findByIdAndUsuarioId(projetoId, usuarioId)
                .orElseThrow(ProjetoNaoEncontradoException::new);
        if (projeto.getStatus() == StatusProjeto.ARQUIVADO) {
            throw new ProjetoArquivadoException();
        }
        return projeto;
    }
}
