package com.controlerotina.projeto.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.controlerotina.capacidade.entity.CapacidadeSemanal;
import com.controlerotina.capacidade.entity.DiaSemana;
import com.controlerotina.capacidade.repository.CapacidadeSemanalRepository;
import com.controlerotina.planner.service.PropostaGeracaoService;
import com.controlerotina.projeto.dto.AtualizarReservaProjetoRequest;
import com.controlerotina.projeto.dto.CriarReservaProjetoRequest;
import com.controlerotina.projeto.dto.ReservaProjetoResponse;
import com.controlerotina.projeto.entity.Projeto;
import com.controlerotina.projeto.entity.ReservaProjetoSemanal;
import com.controlerotina.projeto.entity.StatusProjeto;
import com.controlerotina.projeto.repository.ProjetoRepository;
import com.controlerotina.projeto.repository.ReservaProjetoSemanalRepository;
import com.controlerotina.shared.exception.ProjetoArquivadoException;
import com.controlerotina.shared.exception.ProjetoNaoEncontradoException;
import com.controlerotina.shared.exception.ReservaCapacidadeInsuficienteException;
import com.controlerotina.shared.exception.ReservaProjetoDuplicadaException;
import com.controlerotina.shared.exception.ReservaProjetoNaoEncontradaException;

@Service
public class ReservaProjetoService {

    private final ReservaProjetoSemanalRepository reservaRepository;
    private final ProjetoRepository projetoRepository;
    private final CapacidadeSemanalRepository capacidadeSemanalRepository;
    private final PropostaGeracaoService propostaGeracaoService;
    private final Clock clock;

    public ReservaProjetoService(
            ReservaProjetoSemanalRepository reservaRepository,
            ProjetoRepository projetoRepository,
            CapacidadeSemanalRepository capacidadeSemanalRepository,
            PropostaGeracaoService propostaGeracaoService,
            Clock clock
    ) {
        this.reservaRepository = reservaRepository;
        this.projetoRepository = projetoRepository;
        this.capacidadeSemanalRepository = capacidadeSemanalRepository;
        this.propostaGeracaoService = propostaGeracaoService;
        this.clock = clock;
    }

    @Transactional
    public ReservaProjetoResponse criar(UUID usuarioId, UUID projetoId, CriarReservaProjetoRequest request) {
        Projeto projeto = buscarProjetoDoUsuario(usuarioId, projetoId);
        if (projeto.getStatus() == StatusProjeto.ARQUIVADO) {
            throw new ProjetoArquivadoException();
        }

        if (reservaRepository.existsByUsuarioIdAndProjetoIdAndDiaSemana(
                usuarioId, projetoId, request.diaSemana())) {
            throw new ReservaProjetoDuplicadaException();
        }

        validarSomaReservas(usuarioId, request.diaSemana(), request.minutosReservados(), null);

        Instant agora = clock.instant();
        ReservaProjetoSemanal reserva = new ReservaProjetoSemanal(
                UUID.randomUUID(),
                usuarioId,
                projetoId,
                request.diaSemana(),
                request.minutosReservados(),
                agora,
                agora
        );
        ReservaProjetoResponse resposta = ReservaProjetoResponse.from(reservaRepository.save(reserva));
        reservaRepository.flush();
        propostaGeracaoService.gerarSeDiaSemanaAprovado(usuarioId, request.diaSemana());
        return resposta;
    }

    @Transactional(readOnly = true)
    public List<ReservaProjetoResponse> listarDoProjeto(UUID usuarioId, UUID projetoId) {
        buscarProjetoDoUsuario(usuarioId, projetoId);
        return reservaRepository.findByUsuarioIdAndProjetoIdOrderByDiaSemanaAsc(usuarioId, projetoId)
                .stream()
                .map(ReservaProjetoResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReservaProjetoResponse> listarDoUsuario(UUID usuarioId, DiaSemana diaSemana, UUID projetoId) {
        List<ReservaProjetoSemanal> reservas;
        if (projetoId != null) {
            buscarProjetoDoUsuario(usuarioId, projetoId);
            reservas = reservaRepository.findByUsuarioIdAndProjetoIdOrderByDiaSemanaAsc(usuarioId, projetoId);
            if (diaSemana != null) {
                reservas = reservas.stream()
                        .filter(r -> r.getDiaSemana() == diaSemana)
                        .toList();
            }
        } else if (diaSemana != null) {
            reservas = reservaRepository.findByUsuarioIdAndDiaSemana(usuarioId, diaSemana);
        } else {
            reservas = reservaRepository.findByUsuarioId(usuarioId);
        }
        return reservas.stream()
                .sorted(Comparator
                        .comparing(ReservaProjetoSemanal::getDiaSemana)
                        .thenComparing(ReservaProjetoSemanal::getProjetoId))
                .map(ReservaProjetoResponse::from)
                .toList();
    }

    @Transactional
    public ReservaProjetoResponse atualizar(
            UUID usuarioId,
            UUID projetoId,
            UUID reservaId,
            AtualizarReservaProjetoRequest request
    ) {
        Projeto projeto = buscarProjetoDoUsuario(usuarioId, projetoId);
        ReservaProjetoSemanal reserva = reservaRepository
                .findByIdAndUsuarioIdAndProjetoId(reservaId, usuarioId, projetoId)
                .orElseThrow(ReservaProjetoNaoEncontradaException::new);

        DiaSemana diaAnterior = reserva.getDiaSemana();
        DiaSemana diaFinal = request.diaSemana() != null ? request.diaSemana() : reserva.getDiaSemana();
        int minutosFinais = request.minutosReservados() != null
                ? request.minutosReservados()
                : reserva.getMinutosReservados();

        if (projeto.getStatus() == StatusProjeto.ARQUIVADO && minutosFinais > reserva.getMinutosReservados()) {
            throw new ProjetoArquivadoException();
        }

        if (diaFinal != reserva.getDiaSemana()
                && reservaRepository.existsByUsuarioIdAndProjetoIdAndDiaSemanaAndIdNot(
                        usuarioId, projetoId, diaFinal, reservaId)) {
            throw new ReservaProjetoDuplicadaException();
        }

        validarSomaReservas(usuarioId, diaFinal, minutosFinais, reservaId);

        reserva.atualizar(request.diaSemana(), request.minutosReservados(), clock.instant());
        reservaRepository.flush();
        propostaGeracaoService.gerarSeDiaSemanaAprovado(usuarioId, diaAnterior);
        if (diaFinal != diaAnterior) {
            propostaGeracaoService.gerarSeDiaSemanaAprovado(usuarioId, diaFinal);
        }
        return ReservaProjetoResponse.from(reserva);
    }

    @Transactional
    public void excluir(UUID usuarioId, UUID projetoId, UUID reservaId) {
        buscarProjetoDoUsuario(usuarioId, projetoId);
        ReservaProjetoSemanal reserva = reservaRepository
                .findByIdAndUsuarioIdAndProjetoId(reservaId, usuarioId, projetoId)
                .orElseThrow(ReservaProjetoNaoEncontradaException::new);
        DiaSemana diaSemana = reserva.getDiaSemana();
        reservaRepository.delete(reserva);
        reservaRepository.flush();
        propostaGeracaoService.gerarSeDiaSemanaAprovado(usuarioId, diaSemana);
    }

    void validarSomaReservas(UUID usuarioId, DiaSemana diaSemana, int minutosNovos, UUID reservaIdExcluir) {
        int capacidadeMinutos = capacidadeSemanalRepository
                .findByUsuarioIdAndDiaSemanaAndAtivoTrue(usuarioId, diaSemana)
                .map(CapacidadeSemanal::getHoras)
                .map(horas -> horas * 60)
                .orElse(0);

        int somaOutras = reservaRepository.findByUsuarioIdAndDiaSemana(usuarioId, diaSemana).stream()
                .filter(r -> reservaIdExcluir == null || !r.getId().equals(reservaIdExcluir))
                .mapToInt(ReservaProjetoSemanal::getMinutosReservados)
                .sum();

        if (somaOutras + minutosNovos > capacidadeMinutos) {
            throw new ReservaCapacidadeInsuficienteException();
        }
    }

    private Projeto buscarProjetoDoUsuario(UUID usuarioId, UUID projetoId) {
        return projetoRepository.findByIdAndUsuarioId(projetoId, usuarioId)
                .orElseThrow(ProjetoNaoEncontradoException::new);
    }
}
