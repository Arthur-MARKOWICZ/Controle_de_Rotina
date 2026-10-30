package com.controlerotina.capacidade.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.controlerotina.capacidade.dto.AtualizarCapacidadeSemanalRequest;
import com.controlerotina.capacidade.dto.CapacidadeSemanalResponse;
import com.controlerotina.capacidade.dto.CriarCapacidadeSemanalItemRequest;
import com.controlerotina.capacidade.entity.CapacidadeSemanal;
import com.controlerotina.capacidade.entity.DiaSemana;
import com.controlerotina.capacidade.repository.CapacidadeSemanalRepository;
import com.controlerotina.planner.service.PropostaGeracaoService;
import com.controlerotina.projeto.entity.ReservaProjetoSemanal;
import com.controlerotina.projeto.repository.ReservaProjetoSemanalRepository;
import com.controlerotina.shared.exception.CapacidadeAbaixoDasReservasException;
import com.controlerotina.shared.exception.CapacidadeLoteVazioException;
import com.controlerotina.shared.exception.CapacidadeSemanalConflitoException;
import com.controlerotina.shared.exception.CapacidadeSemanalNaoEncontradaException;
import com.controlerotina.shared.exception.DiaSemanaDuplicadoNoLoteException;

@Service
public class CapacidadeSemanalService {

    private final CapacidadeSemanalRepository capacidadeSemanalRepository;
    private final ReservaProjetoSemanalRepository reservaProjetoSemanalRepository;
    private final PropostaGeracaoService propostaGeracaoService;
    private final Clock clock;

    public CapacidadeSemanalService(
            CapacidadeSemanalRepository capacidadeSemanalRepository,
            ReservaProjetoSemanalRepository reservaProjetoSemanalRepository,
            PropostaGeracaoService propostaGeracaoService,
            Clock clock
    ) {
        this.capacidadeSemanalRepository = capacidadeSemanalRepository;
        this.reservaProjetoSemanalRepository = reservaProjetoSemanalRepository;
        this.propostaGeracaoService = propostaGeracaoService;
        this.clock = clock;
    }

    @Transactional
    public List<CapacidadeSemanalResponse> criar(UUID usuarioId, List<CriarCapacidadeSemanalItemRequest> itens) {
        if (itens == null || itens.isEmpty()) {
            throw new CapacidadeLoteVazioException("Informe ao menos um dia da semana.");
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

        List<CapacidadeSemanalResponse> respostas = capacidadeSemanalRepository.saveAll(criadas).stream()
                .sorted(porOrdemDoEnum())
                .map(CapacidadeSemanalResponse::from)
                .toList();
        capacidadeSemanalRepository.flush();
        for (CriarCapacidadeSemanalItemRequest item : itens) {
            propostaGeracaoService.gerarSeDiaSemanaAprovado(usuarioId, item.diaSemana());
        }
        return respostas;
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

        if (request.horas() != null && request.horas() < capacidade.getHoras()) {
            DiaSemana diaParaValidar = request.diaSemana() != null
                    ? request.diaSemana()
                    : capacidade.getDiaSemana();
            validarHorasContraReservas(usuarioId, diaParaValidar, request.horas());
        }

        DiaSemana diaAnterior = capacidade.getDiaSemana();
        capacidade.atualizar(request.diaSemana(), request.horas(), clock.instant());
        capacidadeSemanalRepository.flush();
        propostaGeracaoService.gerarSeDiaSemanaAprovado(usuarioId, diaAnterior);
        if (request.diaSemana() != null && request.diaSemana() != diaAnterior) {
            propostaGeracaoService.gerarSeDiaSemanaAprovado(usuarioId, request.diaSemana());
        }
        return CapacidadeSemanalResponse.from(capacidade);
    }

    private void validarHorasContraReservas(UUID usuarioId, DiaSemana diaSemana, int horas) {
        int somaReservas = reservaProjetoSemanalRepository.findByUsuarioIdAndDiaSemana(usuarioId, diaSemana)
                .stream()
                .mapToInt(ReservaProjetoSemanal::getMinutosReservados)
                .sum();
        if (horas * 60 < somaReservas) {
            throw new CapacidadeAbaixoDasReservasException();
        }
    }

    @Transactional
    public void excluir(UUID usuarioId, UUID id) {
        CapacidadeSemanal capacidade = buscarDoUsuario(usuarioId, id);
        if (!capacidade.isAtivo()) {
            throw new CapacidadeSemanalNaoEncontradaException();
        }
        DiaSemana dia = capacidade.getDiaSemana();
        capacidade.softDelete(clock.instant());
        capacidadeSemanalRepository.flush();
        propostaGeracaoService.gerarSeDiaSemanaAprovado(usuarioId, dia);
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
        capacidadeSemanalRepository.flush();
        propostaGeracaoService.gerarSeDiaSemanaAprovado(usuarioId, capacidade.getDiaSemana());
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
                throw new DiaSemanaDuplicadoNoLoteException(item.diaSemana().name());
            }
        }
    }

    private static Comparator<CapacidadeSemanal> porOrdemDoEnum() {
        return Comparator.comparing(capacidade -> capacidade.getDiaSemana().ordinal());
    }
}
