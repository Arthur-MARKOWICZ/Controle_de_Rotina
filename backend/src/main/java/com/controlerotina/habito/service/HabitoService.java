package com.controlerotina.habito.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.controlerotina.habito.dto.AtualizarHabitoRequest;
import com.controlerotina.habito.dto.CriarHabitoRequest;
import com.controlerotina.habito.dto.HabitoResponse;
import com.controlerotina.habito.entity.Habito;
import com.controlerotina.habito.entity.StatusHabito;
import com.controlerotina.habito.repository.HabitoRepository;
import com.controlerotina.shared.exception.HabitoJaAtivoException;
import com.controlerotina.shared.exception.HabitoJaPausadoException;
import com.controlerotina.shared.exception.HabitoNaoEncontradoException;

@Service
public class HabitoService {

    private final HabitoRepository habitoRepository;
    private final HabitoStreakService habitoStreakService;
    private final Clock clock;

    public HabitoService(
            HabitoRepository habitoRepository,
            HabitoStreakService habitoStreakService,
            Clock clock
    ) {
        this.habitoRepository = habitoRepository;
        this.habitoStreakService = habitoStreakService;
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
        return HabitoResponse.from(habitoRepository.save(habito));
    }

    @Transactional
    public Page<HabitoResponse> listar(
            UUID usuarioId,
            StatusHabito status,
            boolean incluirDeletadas,
            boolean somenteDeletadas,
            Pageable pageable
    ) {
        Page<Habito> pagina;
        if (somenteDeletadas) {
            pagina = status != null
                    ? habitoRepository.findByUsuarioIdAndStatusAndDeletedAtIsNotNull(usuarioId, status, pageable)
                    : habitoRepository.findByUsuarioIdAndDeletedAtIsNotNull(usuarioId, pageable);
        } else if (incluirDeletadas) {
            pagina = status != null
                    ? habitoRepository.findByUsuarioIdAndStatus(usuarioId, status, pageable)
                    : habitoRepository.findByUsuarioId(usuarioId, pageable);
        } else if (status != null) {
            pagina = habitoRepository.findByUsuarioIdAndStatusAndDeletedAtIsNull(usuarioId, status, pageable);
        } else {
            pagina = habitoRepository.findByUsuarioIdAndDeletedAtIsNull(usuarioId, pageable);
        }
        List<Habito> alterados = habitoStreakService.aplicarQuebraPreguicosaEmLote(pagina.getContent());
        if (!alterados.isEmpty()) {
            habitoRepository.saveAll(alterados);
        }
        return pagina.map(HabitoResponse::from);
    }

    @Transactional
    public HabitoResponse buscarPorId(UUID usuarioId, UUID id) {
        Habito habito = buscarDoUsuario(usuarioId, id);
        if (habitoStreakService.aplicarQuebraPreguicosa(habito)) {
            habitoRepository.save(habito);
        }
        return HabitoResponse.from(habito);
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
        return HabitoResponse.from(habito);
    }

    @Transactional
    public HabitoResponse pausar(UUID usuarioId, UUID id) {
        Habito habito = buscarNaoDeletado(usuarioId, id);
        if (habito.getStatus() == StatusHabito.PAUSADO) {
            throw new HabitoJaPausadoException();
        }
        habito.pausar(clock.instant());
        return HabitoResponse.from(habito);
    }

    @Transactional
    public HabitoResponse reativar(UUID usuarioId, UUID id) {
        Habito habito = buscarNaoDeletado(usuarioId, id);
        if (habito.getStatus() == StatusHabito.ATIVO) {
            throw new HabitoJaAtivoException();
        }
        habito.reativar(clock.instant());
        return HabitoResponse.from(habito);
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
        return HabitoResponse.from(habito);
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
