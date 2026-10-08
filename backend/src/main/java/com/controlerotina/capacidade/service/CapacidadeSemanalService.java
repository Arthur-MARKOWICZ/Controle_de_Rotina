package com.controlerotina.capacidade.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.controlerotina.capacidade.dto.AtualizarCapacidadeSemanalRequest;
import com.controlerotina.capacidade.dto.CapacidadeSemanalResponse;
import com.controlerotina.capacidade.dto.CriarCapacidadeSemanalItemRequest;
import com.controlerotina.capacidade.entity.CapacidadeSemanal;
import com.controlerotina.capacidade.entity.DiaSemana;
import com.controlerotina.capacidade.repository.CapacidadeSemanalRepository;
import com.controlerotina.shared.exception.CapacidadeSemanalConflitoException;
import com.controlerotina.shared.exception.CapacidadeSemanalNaoEncontradaException;

@Service
public class CapacidadeSemanalService {

    private final CapacidadeSemanalRepository capacidadeSemanalRepository;
    private final Clock clock;

    public CapacidadeSemanalService(CapacidadeSemanalRepository capacidadeSemanalRepository, Clock clock) {
        this.capacidadeSemanalRepository = capacidadeSemanalRepository;
        this.clock = clock;
    }

    @Transactional
    public List<CapacidadeSemanalResponse> criar(UUID usuarioId, List<CriarCapacidadeSemanalItemRequest> itens) {
        if (itens == null || itens.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe ao menos um dia da semana.");
        }

        validarDiasUnicosNoLote(itens);

        for (CriarCapacidadeSemanalItemRequest item : itens) {
            if (capacidadeSemanalRepository.existsByUsuarioIdAndDiaSemanaAndAtivoTrue(usuarioId, item.diaSemana())) {
                throw new CapacidadeSemanalConflitoException(item.diaSemana().name());
            }
        }

        Instant agora = clock.instant();
        List<CapacidadeSemanal> criadas = itens.stream()
                .map(item -> new CapacidadeSemanal(
                        UUID.randomUUID(),
                        usuarioId,
                        item.diaSemana(),
                        item.horas(),
                        true,
                        agora,
                        agora
                ))
                .toList();

        return capacidadeSemanalRepository.saveAll(criadas).stream()
                .sorted(porOrdemDoEnum())
                .map(CapacidadeSemanalResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CapacidadeSemanalResponse> listar(UUID usuarioId, boolean incluirDeletadas, boolean somenteDeletadas) {
        List<CapacidadeSemanal> registros;
        if (somenteDeletadas) {
            registros = capacidadeSemanalRepository.findByUsuarioIdAndAtivoFalseOrderByDiaSemanaAsc(usuarioId);
        } else if (incluirDeletadas) {
            registros = capacidadeSemanalRepository.findByUsuarioIdOrderByDiaSemanaAsc(usuarioId);
        } else {
            registros = capacidadeSemanalRepository.findByUsuarioIdAndAtivoTrueOrderByDiaSemanaAsc(usuarioId);
        }

        return registros.stream()
                .sorted(porOrdemDoEnum())
                .map(CapacidadeSemanalResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CapacidadeSemanalResponse buscarPorId(UUID usuarioId, UUID id) {
        return CapacidadeSemanalResponse.from(buscarDoUsuario(usuarioId, id));
    }

    @Transactional
    public CapacidadeSemanalResponse atualizar(UUID usuarioId, UUID id, AtualizarCapacidadeSemanalRequest request) {
        CapacidadeSemanal capacidade = buscarDoUsuario(usuarioId, id);

        if (request.diaSemana() != null
                && request.diaSemana() != capacidade.getDiaSemana()
                && capacidadeSemanalRepository.existsByUsuarioIdAndDiaSemanaAndAtivoTrueAndIdNot(
                        usuarioId, request.diaSemana(), id)) {
            throw new CapacidadeSemanalConflitoException(request.diaSemana().name());
        }

        capacidade.atualizar(request.diaSemana(), request.horas(), clock.instant());
        return CapacidadeSemanalResponse.from(capacidade);
    }

    @Transactional
    public void excluir(UUID usuarioId, UUID id) {
        CapacidadeSemanal capacidade = buscarDoUsuario(usuarioId, id);
        if (!capacidade.isAtivo()) {
            throw new CapacidadeSemanalNaoEncontradaException();
        }
        capacidade.softDelete(clock.instant());
    }

    @Transactional
    public CapacidadeSemanalResponse restaurar(UUID usuarioId, UUID id) {
        CapacidadeSemanal capacidade = buscarDoUsuario(usuarioId, id);
        if (capacidade.isAtivo()) {
            throw new CapacidadeSemanalNaoEncontradaException();
        }
        if (capacidadeSemanalRepository.existsByUsuarioIdAndDiaSemanaAndAtivoTrue(
                usuarioId, capacidade.getDiaSemana())) {
            throw new CapacidadeSemanalConflitoException(capacidade.getDiaSemana().name());
        }
        capacidade.restore(clock.instant());
        return CapacidadeSemanalResponse.from(capacidade);
    }

    private CapacidadeSemanal buscarDoUsuario(UUID usuarioId, UUID id) {
        return capacidadeSemanalRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(CapacidadeSemanalNaoEncontradaException::new);
    }

    private static void validarDiasUnicosNoLote(List<CriarCapacidadeSemanalItemRequest> itens) {
        Set<DiaSemana> vistos = new HashSet<>();
        for (CriarCapacidadeSemanalItemRequest item : itens) {
            if (!vistos.add(item.diaSemana())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Dia da semana duplicado no lote: " + item.diaSemana()
                );
            }
        }
    }

    private static Comparator<CapacidadeSemanal> porOrdemDoEnum() {
        return Comparator.comparing(capacidade -> capacidade.getDiaSemana().ordinal());
    }
}
