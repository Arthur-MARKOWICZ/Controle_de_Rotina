package com.controlerotina.capacidade.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.controlerotina.capacidade.dto.AtualizarCapacidadeOverrideRequest;
import com.controlerotina.capacidade.dto.CapacidadeOverrideResponse;
import com.controlerotina.capacidade.dto.CriarCapacidadeOverrideItemRequest;
import com.controlerotina.capacidade.entity.CapacidadeOverride;
import com.controlerotina.capacidade.repository.CapacidadeOverrideRepository;
import com.controlerotina.planner.service.PropostaGeracaoService;
import com.controlerotina.shared.exception.CapacidadeLoteVazioException;
import com.controlerotina.shared.exception.CapacidadeOverrideConflitoException;
import com.controlerotina.shared.exception.CapacidadeOverrideNaoEncontradaException;
import com.controlerotina.shared.exception.DataDuplicadaNoLoteException;

@Service
public class CapacidadeOverrideService {

    private final CapacidadeOverrideRepository capacidadeOverrideRepository;
    private final PropostaGeracaoService propostaGeracaoService;
    private final Clock clock;

    public CapacidadeOverrideService(
            CapacidadeOverrideRepository capacidadeOverrideRepository,
            PropostaGeracaoService propostaGeracaoService,
            Clock clock
    ) {
        this.capacidadeOverrideRepository = capacidadeOverrideRepository;
        this.propostaGeracaoService = propostaGeracaoService;
        this.clock = clock;
    }

    @Transactional
    public List<CapacidadeOverrideResponse> criar(UUID usuarioId, List<CriarCapacidadeOverrideItemRequest> itens) {
        if (itens == null || itens.isEmpty()) {
            throw new CapacidadeLoteVazioException("Informe ao menos uma data.");
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

        List<CapacidadeOverrideResponse> respostas = capacidadeOverrideRepository.saveAll(criados).stream()
                .sorted((a, b) -> a.getData().compareTo(b.getData()))
                .map(CapacidadeOverrideResponse::from)
                .toList();

        capacidadeOverrideRepository.flush();
        for (CriarCapacidadeOverrideItemRequest item : itens) {
            propostaGeracaoService.gerarSeDiaAprovado(usuarioId, item.data());
        }
        return respostas;
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

        LocalDate dataAnterior = override.getData();
        override.atualizar(request.data(), request.horas(), clock.instant());
        capacidadeOverrideRepository.flush();

        propostaGeracaoService.gerarSeDiaAprovado(usuarioId, dataAnterior);
        if (request.data() != null && !request.data().equals(dataAnterior)) {
            propostaGeracaoService.gerarSeDiaAprovado(usuarioId, request.data());
        }

        return CapacidadeOverrideResponse.from(override);
    }

    @Transactional
    public void excluir(UUID usuarioId, UUID id) {
        CapacidadeOverride override = buscarDoUsuario(usuarioId, id);
        if (!override.isAtivo()) {
            throw new CapacidadeOverrideNaoEncontradaException();
        }
        LocalDate data = override.getData();
        override.softDelete(clock.instant());
        capacidadeOverrideRepository.flush();
        propostaGeracaoService.gerarSeDiaAprovado(usuarioId, data);
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
        capacidadeOverrideRepository.flush();
        propostaGeracaoService.gerarSeDiaAprovado(usuarioId, override.getData());
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
                throw new DataDuplicadaNoLoteException(item.data().toString());
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
