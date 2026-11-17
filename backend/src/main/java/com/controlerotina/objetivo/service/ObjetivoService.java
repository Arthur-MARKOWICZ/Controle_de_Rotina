package com.controlerotina.objetivo.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.controlerotina.historico.PeriodoBRT;
import com.controlerotina.objetivo.dto.AtualizarObjetivoRequest;
import com.controlerotina.objetivo.dto.CriarObjetivoRequest;
import com.controlerotina.objetivo.dto.ObjetivoResponse;
import com.controlerotina.objetivo.entity.Objetivo;
import com.controlerotina.objetivo.entity.StatusObjetivo;
import com.controlerotina.objetivo.repository.ObjetivoRepository;
import com.controlerotina.objetivo.repository.ObjetivoSpecs;
import com.controlerotina.shared.exception.ObjetivoArquivadoException;
import com.controlerotina.shared.exception.ObjetivoJaArquivadoException;
import com.controlerotina.shared.exception.ObjetivoJaAtivoException;
import com.controlerotina.shared.exception.ObjetivoJaConcluidoException;
import com.controlerotina.shared.exception.ObjetivoNaoEncontradoException;
import com.controlerotina.shared.exception.PrazoNoPassadoException;

@Service
public class ObjetivoService {

    private final ObjetivoRepository objetivoRepository;
    private final Clock clock;

    public ObjetivoService(ObjetivoRepository objetivoRepository, Clock clock) {
        this.objetivoRepository = objetivoRepository;
        this.clock = clock;
    }

    @Transactional
    public ObjetivoResponse criar(UUID usuarioId, CriarObjetivoRequest request) {
        if (request.prazo() != null && request.prazo().isBefore(hoje())) {
            throw new PrazoNoPassadoException();
        }
        Objetivo objetivo = new Objetivo(
                UUID.randomUUID(),
                usuarioId,
                request.titulo(),
                request.descricao(),
                request.prazo(),
                clock.instant()
        );
        return ObjetivoResponse.from(objetivoRepository.save(objetivo));
    }

    @Transactional(readOnly = true)
    public Page<ObjetivoResponse> listar(UUID usuarioId, StatusObjetivo status, Pageable pageable) {
        StatusObjetivo filtroStatus = status != null ? status : StatusObjetivo.ATIVO;
        Specification<Objetivo> spec = Specification.allOf(
                ObjetivoSpecs.doUsuario(usuarioId),
                ObjetivoSpecs.comStatus(filtroStatus)
        );
        return objetivoRepository.findAll(spec, comOrdemPadrao(pageable, filtroStatus))
                .map(ObjetivoResponse::from);
    }

    @Transactional(readOnly = true)
    public ObjetivoResponse buscarPorId(UUID usuarioId, UUID id) {
        return ObjetivoResponse.from(buscarDoUsuario(usuarioId, id));
    }

    @Transactional
    public ObjetivoResponse atualizar(UUID usuarioId, UUID id, AtualizarObjetivoRequest request) {
        Objetivo objetivo = buscarDoUsuario(usuarioId, id);
        if (prazoNovoNoPassado(objetivo, request)) {
            throw new PrazoNoPassadoException();
        }
        objetivo.atualizar(
                request.titulo(),
                request.descricao(),
                request.prazoPresente(),
                request.prazo(),
                clock.instant()
        );
        return ObjetivoResponse.from(objetivo);
    }

    @Transactional
    public ObjetivoResponse concluir(UUID usuarioId, UUID id) {
        Objetivo objetivo = buscarDoUsuario(usuarioId, id);
        switch (objetivo.getStatus()) {
            case CONCLUIDO -> throw new ObjetivoJaConcluidoException();
            case ARQUIVADO -> throw new ObjetivoArquivadoException();
            case ATIVO -> objetivo.concluir(clock.instant());
        }
        return ObjetivoResponse.from(objetivo);
    }

    @Transactional
    public ObjetivoResponse arquivar(UUID usuarioId, UUID id) {
        Objetivo objetivo = buscarDoUsuario(usuarioId, id);
        if (objetivo.getStatus() == StatusObjetivo.ARQUIVADO) {
            throw new ObjetivoJaArquivadoException();
        }
        objetivo.arquivar(clock.instant());
        return ObjetivoResponse.from(objetivo);
    }

    @Transactional
    public ObjetivoResponse reabrir(UUID usuarioId, UUID id) {
        Objetivo objetivo = buscarDoUsuario(usuarioId, id);
        if (objetivo.getStatus() == StatusObjetivo.ATIVO) {
            throw new ObjetivoJaAtivoException();
        }
        objetivo.reabrir(clock.instant());
        return ObjetivoResponse.from(objetivo);
    }

    private boolean prazoNovoNoPassado(Objetivo objetivo, AtualizarObjetivoRequest request) {
        LocalDate prazo = request.prazo();
        return request.prazoPresente()
                && prazo != null
                && !prazo.equals(objetivo.getPrazo())
                && prazo.isBefore(hoje());
    }

    private LocalDate hoje() {
        return LocalDate.now(clock.withZone(PeriodoBRT.ZONE));
    }

    private static Pageable comOrdemPadrao(Pageable pageable, StatusObjetivo status) {
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), ordemPadrao(status));
    }

    private static Sort ordemPadrao(StatusObjetivo status) {
        return switch (status) {
            case ATIVO -> Sort.by(Sort.Order.asc("prazo").nullsLast(), Sort.Order.asc("createdAt"));
            case CONCLUIDO -> Sort.by(Sort.Order.desc("concluidoEm"), Sort.Order.desc("createdAt"));
            case ARQUIVADO -> Sort.by(Sort.Order.desc("arquivadoEm"), Sort.Order.desc("createdAt"));
        };
    }

    private Objetivo buscarDoUsuario(UUID usuarioId, UUID id) {
        return objetivoRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(ObjetivoNaoEncontradoException::new);
    }
}
