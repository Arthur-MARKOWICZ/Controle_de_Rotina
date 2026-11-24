package com.controlerotina.objetivo.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import com.controlerotina.objetivo.dto.ObjetivoProgressoResponse;
import com.controlerotina.objetivo.dto.ObjetivoResponse;
import com.controlerotina.objetivo.entity.Objetivo;
import com.controlerotina.objetivo.entity.StatusObjetivo;
import com.controlerotina.objetivo.repository.ObjetivoRepository;
import com.controlerotina.objetivo.repository.ObjetivoSpecs;
import com.controlerotina.shared.exception.CombinacaoStatusInvalidaException;
import com.controlerotina.shared.exception.ObjetivoArquivadoException;
import com.controlerotina.shared.exception.ObjetivoJaArquivadoException;
import com.controlerotina.shared.exception.ObjetivoJaAtivoException;
import com.controlerotina.shared.exception.ObjetivoJaConcluidoException;
import com.controlerotina.shared.exception.ObjetivoNaoEncontradoException;
import com.controlerotina.shared.exception.PrazoNoPassadoException;

@Service
public class ObjetivoService {

    private final ObjetivoRepository objetivoRepository;
    private final ObjetivoVinculoService objetivoVinculoService;
    private final ObjetivoProgressoService objetivoProgressoService;
    private final Clock clock;

    public ObjetivoService(
            ObjetivoRepository objetivoRepository,
            ObjetivoVinculoService objetivoVinculoService,
            ObjetivoProgressoService objetivoProgressoService,
            Clock clock
    ) {
        this.objetivoRepository = objetivoRepository;
        this.objetivoVinculoService = objetivoVinculoService;
        this.objetivoProgressoService = objetivoProgressoService;
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
        return ObjetivoResponse.from(objetivoRepository.save(objetivo), null);
    }

    @Transactional(readOnly = true)
    public Page<ObjetivoResponse> listar(UUID usuarioId, List<StatusObjetivo> status, Pageable pageable) {
        Set<StatusObjetivo> filtroStatus = normalizarStatus(status);
        Specification<Objetivo> spec = Specification.allOf(
                ObjetivoSpecs.doUsuario(usuarioId),
                ObjetivoSpecs.comStatus(filtroStatus)
        );
        return comProgresso(objetivoRepository.findAll(spec, comOrdemPadrao(pageable, filtroStatus)));
    }

    @Transactional(readOnly = true)
    public ObjetivoResponse buscarPorId(UUID usuarioId, UUID id) {
        return comProgresso(buscarDoUsuario(usuarioId, id));
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
        return comProgresso(objetivo);
    }

    @Transactional
    public ObjetivoResponse concluir(UUID usuarioId, UUID id) {
        Objetivo objetivo = buscarDoUsuario(usuarioId, id);
        switch (objetivo.getStatus()) {
            case CONCLUIDO -> throw new ObjetivoJaConcluidoException();
            case ARQUIVADO -> throw new ObjetivoArquivadoException();
            case ATIVO -> objetivo.concluir(clock.instant());
        }
        return comProgresso(objetivo);
    }

    @Transactional
    public ObjetivoResponse arquivar(UUID usuarioId, UUID id) {
        Objetivo objetivo = buscarDoUsuario(usuarioId, id);
        if (objetivo.getStatus() == StatusObjetivo.ARQUIVADO) {
            throw new ObjetivoJaArquivadoException();
        }
        objetivo.arquivar(clock.instant());
        return comProgresso(objetivo);
    }

    @Transactional
    public ObjetivoResponse reabrir(UUID usuarioId, UUID id) {
        Objetivo objetivo = buscarDoUsuario(usuarioId, id);
        if (objetivo.getStatus() == StatusObjetivo.ATIVO) {
            throw new ObjetivoJaAtivoException();
        }
        objetivo.reabrir(clock.instant());
        return comProgresso(objetivo);
    }

    private Page<ObjetivoResponse> comProgresso(Page<Objetivo> pagina) {
        List<Objetivo> objetivos = pagina.getContent();
        if (objetivos.isEmpty()) {
            return pagina.map(objetivo -> ObjetivoResponse.from(objetivo, null));
        }
        Map<UUID, ObjetivoConjunto> conjuntos = objetivoVinculoService.resolverConjuntos(
                objetivos.stream().map(Objetivo::getId).toList());
        Map<UUID, ObjetivoProgressoResponse> progressos = objetivoProgressoService.calcularLote(objetivos, conjuntos);
        return pagina.map(objetivo -> ObjetivoResponse.from(objetivo, progressos.get(objetivo.getId())));
    }

    private ObjetivoResponse comProgresso(Objetivo objetivo) {
        ObjetivoConjunto conjunto = objetivoVinculoService.resolverConjunto(objetivo.getId());
        return ObjetivoResponse.from(objetivo, objetivoProgressoService.calcular(objetivo, conjunto));
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

    /** Sem status → só ATIVO. ATIVO misturado com encerrados não tem ordem definida, então é recusado. */
    private static Set<StatusObjetivo> normalizarStatus(List<StatusObjetivo> status) {
        if (status == null || status.isEmpty()) {
            return EnumSet.of(StatusObjetivo.ATIVO);
        }
        Set<StatusObjetivo> conjunto = EnumSet.copyOf(status);
        if (conjunto.contains(StatusObjetivo.ATIVO) && conjunto.size() > 1) {
            throw new CombinacaoStatusInvalidaException();
        }
        return conjunto;
    }

    private static Pageable comOrdemPadrao(Pageable pageable, Set<StatusObjetivo> status) {
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), ordemPadrao(status));
    }

    private static Sort ordemPadrao(Set<StatusObjetivo> status) {
        if (status.contains(StatusObjetivo.ATIVO)) {
            return Sort.by(Sort.Order.asc("prazo").nullsLast(), Sort.Order.asc("createdAt"));
        }
        // Encerrados: pela data em que foram encerrados (conclusão, senão arquivamento).
        return Sort.by(Sort.Order.desc("encerradoEm"), Sort.Order.desc("createdAt"));
    }

    private Objetivo buscarDoUsuario(UUID usuarioId, UUID id) {
        return objetivoRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(ObjetivoNaoEncontradoException::new);
    }
}
