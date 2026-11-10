package com.controlerotina.habito.service;

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

import com.controlerotina.habito.dto.AtualizarHabitoRequest;
import com.controlerotina.habito.dto.CriarHabitoRequest;
import com.controlerotina.habito.dto.HabitoResponse;
import com.controlerotina.habito.entity.Habito;
import com.controlerotina.habito.entity.StatusHabito;
import com.controlerotina.habito.repository.HabitoRepository;
import com.controlerotina.habito.repository.HabitoSpecs;
import com.controlerotina.shared.exception.HabitoJaAtivoException;
import com.controlerotina.shared.exception.HabitoJaPausadoException;
import com.controlerotina.shared.exception.HabitoNaoEncontradoException;
import com.controlerotina.tag.dto.TagResumoResponse;
import com.controlerotina.tag.service.TagVinculoService;

@Service
public class HabitoService {

    private final HabitoRepository habitoRepository;
    private final HabitoStreakService habitoStreakService;
    private final TagVinculoService tagVinculoService;
    private final Clock clock;

    public HabitoService(
            HabitoRepository habitoRepository,
            HabitoStreakService habitoStreakService,
            TagVinculoService tagVinculoService,
            Clock clock
    ) {
        this.habitoRepository = habitoRepository;
        this.habitoStreakService = habitoStreakService;
        this.tagVinculoService = tagVinculoService;
        this.clock = clock;
    }

    @Transactional
    public HabitoResponse criar(UUID usuarioId, CriarHabitoRequest request) {
        Instant agora = clock.instant();
        Habito habito = new Habito(
                UUID.randomUUID(),
                usuarioId,
                request.titulo(),
                request.descricao(),
                request.duracaoMinutos(),
                request.diasSemana(),
                request.horarioPreferencial(),
                StatusHabito.ATIVO,
                0,
                0,
                agora,
                agora
        );
        Habito salvo = habitoRepository.save(habito);
        habitoRepository.flush();
        List<TagResumoResponse> tags = tagVinculoService.substituirTagsHabito(
                usuarioId,
                salvo.getId(),
                request.tagIds()
        );
        return HabitoResponse.from(salvo, tags, null);
    }

    @Transactional
    public Page<HabitoResponse> listar(
            UUID usuarioId,
            StatusHabito status,
            boolean incluirDeletadas,
            boolean somenteDeletadas,
            List<UUID> tagIds,
            Pageable pageable
    ) {
        List<UUID> filtroTagIds = tagIds == null
                ? List.of()
                : tagIds.stream().filter(Objects::nonNull).distinct().toList();

        Specification<Habito> spec = Specification.allOf(
                HabitoSpecs.doUsuario(usuarioId),
                HabitoSpecs.comStatus(status),
                HabitoSpecs.filtroDelecao(incluirDeletadas, somenteDeletadas),
                HabitoSpecs.comAlgumaTag(filtroTagIds)
        );

        Page<Habito> pagina = habitoRepository.findAll(spec, pageable);
        List<Habito> alterados = habitoStreakService.aplicarQuebraPreguicosaEmLote(pagina.getContent());
        if (!alterados.isEmpty()) {
            habitoRepository.saveAll(alterados);
        }

        Map<UUID, List<TagResumoResponse>> tagsPorHabito = tagVinculoService.mapearTagsPorHabito(
                pagina.getContent().stream().map(Habito::getId).toList()
        );

        List<HabitoResponse> conteudo = new ArrayList<>();
        for (Habito h : pagina.getContent()) {
            List<TagResumoResponse> tags = tagsPorHabito.getOrDefault(h.getId(), List.of());
            Integer score = filtroTagIds.isEmpty()
                    ? null
                    : TagVinculoService.matchScore(
                            tags.stream().map(TagResumoResponse::id).toList(),
                            filtroTagIds
                    );
            conteudo.add(HabitoResponse.from(h, tags, score));
        }

        if (filtroTagIds.size() >= 2) {
            conteudo.sort(Comparator
                    .comparing((HabitoResponse r) -> r.tagMatchScore() == null ? 0 : r.tagMatchScore())
                    .reversed());
        }

        return new PageImpl<>(conteudo, pageable, pagina.getTotalElements());
    }

    @Transactional
    public HabitoResponse buscarPorId(UUID usuarioId, UUID id) {
        Habito habito = buscarDoUsuario(usuarioId, id);
        if (habitoStreakService.aplicarQuebraPreguicosa(habito)) {
            habitoRepository.save(habito);
        }
        return HabitoResponse.from(
                habito,
                tagVinculoService.listarTagsHabito(habito.getId()),
                null
        );
    }

    @Transactional
    public HabitoResponse atualizar(UUID usuarioId, UUID id, AtualizarHabitoRequest request) {
        Habito habito = buscarDoUsuario(usuarioId, id);
        habito.atualizar(
                request.titulo(),
                request.descricao(),
                request.duracaoMinutos(),
                request.diasSemana(),
                request.horarioPreferencial(),
                request.horarioPreferencialPresente() && request.horarioPreferencial() == null,
                clock.instant()
        );
        List<TagResumoResponse> tags = request.tagIdsPresente()
                ? tagVinculoService.substituirTagsHabito(usuarioId, habito.getId(), request.tagIds())
                : tagVinculoService.listarTagsHabito(habito.getId());
        return HabitoResponse.from(habito, tags, null);
    }

    @Transactional
    public HabitoResponse pausar(UUID usuarioId, UUID id) {
        Habito habito = buscarNaoDeletado(usuarioId, id);
        if (habito.getStatus() == StatusHabito.PAUSADO) {
            throw new HabitoJaPausadoException();
        }
        habito.pausar(clock.instant());
        return HabitoResponse.from(
                habito,
                tagVinculoService.listarTagsHabito(habito.getId()),
                null
        );
    }

    @Transactional
    public HabitoResponse reativar(UUID usuarioId, UUID id) {
        Habito habito = buscarNaoDeletado(usuarioId, id);
        if (habito.getStatus() == StatusHabito.ATIVO) {
            throw new HabitoJaAtivoException();
        }
        habito.reativar(clock.instant());
        return HabitoResponse.from(
                habito,
                tagVinculoService.listarTagsHabito(habito.getId()),
                null
        );
    }

    @Transactional
    public void excluir(UUID usuarioId, UUID id) {
        Habito habito = buscarDoUsuario(usuarioId, id);
        if (habito.isDeletado()) {
            throw new HabitoNaoEncontradoException();
        }
        habito.softDelete(clock.instant());
    }

    @Transactional
    public HabitoResponse restaurar(UUID usuarioId, UUID id) {
        Habito habito = buscarDoUsuario(usuarioId, id);
        if (!habito.isDeletado()) {
            throw new HabitoNaoEncontradoException();
        }
        habito.restore(clock.instant());
        return HabitoResponse.from(
                habito,
                tagVinculoService.listarTagsHabito(habito.getId()),
                null
        );
    }

    private Habito buscarNaoDeletado(UUID usuarioId, UUID id) {
        Habito habito = buscarDoUsuario(usuarioId, id);
        if (habito.isDeletado()) {
            throw new HabitoNaoEncontradoException();
        }
        return habito;
    }

    private Habito buscarDoUsuario(UUID usuarioId, UUID id) {
        return habitoRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(HabitoNaoEncontradoException::new);
    }
}
