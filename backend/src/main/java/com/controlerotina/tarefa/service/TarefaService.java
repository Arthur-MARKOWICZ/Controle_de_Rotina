package com.controlerotina.tarefa.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.controlerotina.historico.PeriodoBRT;
import com.controlerotina.planner.service.PropostaGeracaoService;
import com.controlerotina.shared.exception.AtrasadaComPrazoFuturoException;
import com.controlerotina.shared.exception.CampoTextoTarefaInvalidoException;
import com.controlerotina.shared.exception.PrazoNoPassadoException;
import com.controlerotina.shared.exception.TarefaNaoEncontradaException;
import com.controlerotina.tarefa.dto.AtualizarTarefaRequest;
import com.controlerotina.tarefa.dto.CriarTarefaRequest;
import com.controlerotina.tarefa.dto.TarefaFiltro;
import com.controlerotina.tarefa.dto.TarefaResponse;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;
import com.controlerotina.tarefa.repository.TarefaRepository;
import com.controlerotina.tarefa.repository.TarefaSpecs;

@Service
public class TarefaService {

    private final TarefaRepository tarefaRepository;
    private final PropostaGeracaoService propostaGeracaoService;
    private final Clock clock;

    public TarefaService(
            TarefaRepository tarefaRepository,
            PropostaGeracaoService propostaGeracaoService,
            Clock clock
    ) {
        this.tarefaRepository = tarefaRepository;
        this.propostaGeracaoService = propostaGeracaoService;
        this.clock = clock;
    }

    @Transactional
    public TarefaResponse criar(UUID usuarioId, CriarTarefaRequest request) {
        Instant agora = clock.instant();
        if (request.prazo() != null && request.prazo().isBefore(agora)) {
            throw new PrazoNoPassadoException();
        }

        Tarefa tarefa = new Tarefa(
                UUID.randomUUID(),
                usuarioId,
                request.titulo(),
                request.descricao(),
                request.prioridade(),
                request.prazo(),
                StatusTarefa.PENDENTE,
                request.duracaoMinutos(),
                agora,
                agora
        );

        Tarefa salva = tarefaRepository.save(tarefa);
        tarefaRepository.flush();
        propostaGeracaoService.gerarSeTarefaNaJanelaAprovada(usuarioId, salva.getPrazo());
        return TarefaResponse.from(salva);
    }

    @Transactional(readOnly = true)
    public Page<TarefaResponse> listar(UUID usuarioId, TarefaFiltro filtro, Pageable pageable) {
        Specification<Tarefa> spec = Specification.allOf(
                TarefaSpecs.doUsuario(usuarioId),
                TarefaSpecs.comStatus(filtro.status()),
                TarefaSpecs.comPrioridade(filtro.prioridade()),
                TarefaSpecs.prazoEntre(filtro.prazoDe(), filtro.prazoAte()),
                TarefaSpecs.duracaoEntre(filtro.duracaoDe(), filtro.duracaoAte()),
                TarefaSpecs.criadoEntre(filtro.criadoDe(), filtro.criadoAte()),
                TarefaSpecs.atualizadoEntre(filtro.atualizadoDe(), filtro.atualizadoAte()),
                TarefaSpecs.filtroDelecao(filtro.incluirDeletadas(), filtro.somenteDeletadas())
        );

        return tarefaRepository.findAll(spec, pageable).map(TarefaResponse::from);
    }

    @Transactional(readOnly = true)
    public TarefaResponse buscarPorId(UUID usuarioId, UUID id) {
        return TarefaResponse.from(buscarDoUsuario(usuarioId, id));
    }

    @Transactional
    public TarefaResponse atualizar(UUID usuarioId, UUID id, AtualizarTarefaRequest request) {
        Tarefa tarefa = buscarDoUsuario(usuarioId, id);
        Instant agora = clock.instant();

        validarCamposTexto(request);

        Instant prazoFinal = request.prazo() != null ? request.prazo() : tarefa.getPrazo();
        StatusTarefa statusFinal = request.status() != null ? request.status() : tarefa.getStatus();

        if (request.prazo() != null && request.prazo().isBefore(agora)) {
            throw new PrazoNoPassadoException();
        }

        boolean prazoMovidoParaFuturo = request.prazo() != null
                && prazoFinal != null
                && !prazoFinal.isBefore(agora);
        if (prazoMovidoParaFuturo && request.status() == null && statusFinal == StatusTarefa.ATRASADA) {
            statusFinal = StatusTarefa.PENDENTE;
        }

        if (statusFinal == StatusTarefa.ATRASADA
                && (prazoFinal == null || !prazoFinal.isBefore(agora))) {
            throw new AtrasadaComPrazoFuturoException();
        }

        StatusTarefa statusParaGravar = statusMudou(tarefa.getStatus(), statusFinal, request.status())
                ? statusFinal
                : null;

        tarefa.atualizar(
                request.titulo(),
                request.descricao(),
                request.prioridade(),
                request.prazo(),
                statusParaGravar,
                request.duracaoMinutos(),
                agora
        );

        return TarefaResponse.from(tarefa);
    }

    private static boolean statusMudou(
            StatusTarefa atual,
            StatusTarefa statusFinal,
            StatusTarefa statusSolicitado
    ) {
        return statusSolicitado != null || statusFinal != atual;
    }

    @Transactional
    public void excluir(UUID usuarioId, UUID id) {
        Tarefa tarefa = buscarDoUsuario(usuarioId, id);
        if (tarefa.getDeletedAt() != null) {
            throw new TarefaNaoEncontradaException();
        }
        tarefa.softDelete(clock.instant());
    }

    @Transactional
    public TarefaResponse restaurar(UUID usuarioId, UUID id) {
        Tarefa tarefa = buscarDoUsuario(usuarioId, id);
        if (tarefa.getDeletedAt() == null) {
            throw new TarefaNaoEncontradaException();
        }
        tarefa.restore(clock.instant());
        return TarefaResponse.from(tarefa);
    }

    @Transactional
    public void processarAtrasos() {
        Instant agora = clock.instant();
        LocalDate hoje = LocalDate.now(clock.withZone(PeriodoBRT.ZONE));
        Instant inicioDoDia = PeriodoBRT.intervaloDia(hoje).inicioInclusivo();
        tarefaRepository.atualizarStatusQuandoPrazoPassou(
                StatusTarefa.PENDENTE,
                StatusTarefa.ATRASADA,
                inicioDoDia,
                agora
        );
        tarefaRepository.atualizarStatusQuandoPrazoPassou(
                StatusTarefa.EM_PROGRESSO,
                StatusTarefa.ATRASADA,
                inicioDoDia,
                agora
        );
    }

    private Tarefa buscarDoUsuario(UUID usuarioId, UUID id) {
        return tarefaRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(TarefaNaoEncontradaException::new);
    }

    private static void validarCamposTexto(AtualizarTarefaRequest request) {
        if (request.titulo() != null && request.titulo().isBlank()) {
            throw new CampoTextoTarefaInvalidoException("Título não pode ser vazio.");
        }
        if (request.descricao() != null && request.descricao().isBlank()) {
            throw new CampoTextoTarefaInvalidoException("Descrição não pode ser vazia.");
        }
    }
}
