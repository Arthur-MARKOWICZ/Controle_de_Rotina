package com.controlerotina.capacidade.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.controlerotina.capacidade.dto.AtualizarCapacidadeOverrideRequest;
import com.controlerotina.capacidade.dto.CapacidadeOverrideResponse;
import com.controlerotina.capacidade.dto.CriarCapacidadeOverrideItemRequest;
import com.controlerotina.capacidade.entity.CapacidadeOverride;
import com.controlerotina.capacidade.repository.CapacidadeOverrideRepository;
import com.controlerotina.shared.exception.CapacidadeOverrideConflitoException;
import com.controlerotina.shared.exception.CapacidadeOverrideNaoEncontradaException;

@Service
public class CapacidadeOverrideService {

    private final CapacidadeOverrideRepository capacidadeOverrideRepository;
    private final Clock clock;

    public CapacidadeOverrideService(CapacidadeOverrideRepository capacidadeOverrideRepository, Clock clock) {
        this.capacidadeOverrideRepository = capacidadeOverrideRepository;
        this.clock = clock;
    }

    @Transactional
    public List<CapacidadeOverrideResponse> criar(UUID usuarioId, List<CriarCapacidadeOverrideItemRequest> itens) {
        if (itens == null || itens.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe ao menos uma data.");
        }

        validarDatasUnicasNoLote(itens);

        for (CriarCapacidadeOverrideItemRequest item : itens) {
            if (capacidadeOverrideRepository.existsByUsuarioIdAndDataAndAtivoTrue(usuarioId, item.data())) {
                throw new CapacidadeOverrideConflitoException(item.data().toString());
            }
        }

        Instant agora = clock.instant();
        List<CapacidadeOverride> criados = itens.stream()
                .map(item -> new CapacidadeOverride(
                        UUID.randomUUID(),
                        usuarioId,
                        item.data(),
                        item.horas(),
                        true,
                        agora,
                        agora
                ))
                .toList();

        return capacidadeOverrideRepository.saveAll(criados).stream()
                .sorted((a, b) -> a.getData().compareTo(b.getData()))
                .map(CapacidadeOverrideResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CapacidadeOverrideResponse> listar(
            UUID usuarioId,
            boolean incluirDeletadas,
            boolean somenteDeletadas,
            LocalDate dataDe,
            LocalDate dataAte
    ) {
        List<CapacidadeOverride> registros;
        if (somenteDeletadas) {
            registros = capacidadeOverrideRepository.findByUsuarioIdAndAtivoFalseOrderByDataAsc(usuarioId);
        } else if (incluirDeletadas) {
            registros = capacidadeOverrideRepository.findByUsuarioIdOrderByDataAsc(usuarioId);
        } else {
            registros = capacidadeOverrideRepository.findByUsuarioIdAndAtivoTrueOrderByDataAsc(usuarioId);
        }

        return registros.stream()
                .filter(override -> dentroDoIntervalo(override.getData(), dataDe, dataAte))
                .map(CapacidadeOverrideResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CapacidadeOverrideResponse buscarPorId(UUID usuarioId, UUID id) {
        return CapacidadeOverrideResponse.from(buscarDoUsuario(usuarioId, id));
    }

    @Transactional
    public CapacidadeOverrideResponse atualizar(UUID usuarioId, UUID id, AtualizarCapacidadeOverrideRequest request) {
        CapacidadeOverride override = buscarDoUsuario(usuarioId, id);

        if (request.data() != null
                && !request.data().equals(override.getData())
                && capacidadeOverrideRepository.existsByUsuarioIdAndDataAndAtivoTrueAndIdNot(
                        usuarioId, request.data(), id)) {
            throw new CapacidadeOverrideConflitoException(request.data().toString());
        }

        override.atualizar(request.data(), request.horas(), clock.instant());
        return CapacidadeOverrideResponse.from(override);
    }

    @Transactional
    public void excluir(UUID usuarioId, UUID id) {
        CapacidadeOverride override = buscarDoUsuario(usuarioId, id);
        if (!override.isAtivo()) {
            throw new CapacidadeOverrideNaoEncontradaException();
        }
        override.softDelete(clock.instant());
    }

    @Transactional
    public CapacidadeOverrideResponse restaurar(UUID usuarioId, UUID id) {
        CapacidadeOverride override = buscarDoUsuario(usuarioId, id);
        if (override.isAtivo()) {
            throw new CapacidadeOverrideNaoEncontradaException();
        }
        if (capacidadeOverrideRepository.existsByUsuarioIdAndDataAndAtivoTrue(usuarioId, override.getData())) {
            throw new CapacidadeOverrideConflitoException(override.getData().toString());
        }
        override.restore(clock.instant());
        return CapacidadeOverrideResponse.from(override);
    }

    private CapacidadeOverride buscarDoUsuario(UUID usuarioId, UUID id) {
        return capacidadeOverrideRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(CapacidadeOverrideNaoEncontradaException::new);
    }

    private static void validarDatasUnicasNoLote(List<CriarCapacidadeOverrideItemRequest> itens) {
        Set<LocalDate> vistas = new HashSet<>();
        for (CriarCapacidadeOverrideItemRequest item : itens) {
            if (!vistas.add(item.data())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Data duplicada no lote: " + item.data()
                );
            }
        }
    }

    private static boolean dentroDoIntervalo(LocalDate data, LocalDate dataDe, LocalDate dataAte) {
        if (dataDe != null && data.isBefore(dataDe)) {
            return false;
        }
        if (dataAte != null && data.isAfter(dataAte)) {
            return false;
        }
        return true;
    }
}
