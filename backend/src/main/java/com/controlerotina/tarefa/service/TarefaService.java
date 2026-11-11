package com.controlerotina.tarefa.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.controlerotina.decidir.repository.ItemDecidirRepository;
import com.controlerotina.execucao.entity.StatusExecucao;
import com.controlerotina.execucao.repository.ExecucaoRepository;
import com.controlerotina.historico.PeriodoBRT;
import com.controlerotina.planner.service.PropostaGeracaoService;
import com.controlerotina.projeto.entity.Projeto;
import com.controlerotina.projeto.entity.StatusProjeto;
import com.controlerotina.projeto.repository.ProjetoRepository;
import com.controlerotina.shared.exception.AtrasadaComPrazoFuturoException;
import com.controlerotina.shared.exception.CampoTextoTarefaInvalidoException;
import com.controlerotina.shared.exception.PrazoNoPassadoException;
import com.controlerotina.shared.exception.ProjetoArquivadoException;
import com.controlerotina.shared.exception.ProjetoNaoEncontradoException;
import com.controlerotina.shared.exception.TarefaNaoEncontradaException;
import com.controlerotina.tag.dto.TagResumoResponse;
import com.controlerotina.tag.service.TagVinculoService;
import com.controlerotina.tarefa.dto.AtualizarTarefaRequest;
import com.controlerotina.tarefa.dto.CriarTarefaRequest;
import com.controlerotina.tarefa.dto.TarefaFiltro;
import com.controlerotina.tarefa.dto.TarefaResponse;
import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;
import com.controlerotina.tarefa.repository.TarefaRepository;
import com.controlerotina.tarefa.repository.TarefaSpecs;

@Service
public class TarefaService {

    private static final Prioridade PRIORIDADE_DEFAULT = Prioridade.MEDIA;
    private static final int DURACAO_MINUTOS_DEFAULT = 30;

    private final TarefaRepository tarefaRepository;
    private final ProjetoRepository projetoRepository;
    private final PropostaGeracaoService propostaGeracaoService;
    private final ExecucaoRepository execucaoRepository;
    private final TagVinculoService tagVinculoService;
    private final ItemDecidirRepository itemDecidirRepository;
    private final Clock clock;

    public TarefaService(
            TarefaRepository tarefaRepository,
            ProjetoRepository projetoRepository,
            PropostaGeracaoService propostaGeracaoService,
            ExecucaoRepository execucaoRepository,
            TagVinculoService tagVinculoService,
            ItemDecidirRepository itemDecidirRepository,
            Clock clock
    ) {
        this.tarefaRepository = tarefaRepository;
        this.projetoRepository = projetoRepository;
        this.propostaGeracaoService = propostaGeracaoService;
        this.execucaoRepository = execucaoRepository;
        this.tagVinculoService = tagVinculoService;
        this.itemDecidirRepository = itemDecidirRepository;
        this.clock = clock;
    }

    @Transactional
    public TarefaResponse criar(UUID usuarioId, CriarTarefaRequest request) {
        Instant agora = clock.instant();
        if (request.prazo() != null && request.prazo().isBefore(agora)) {
            throw new PrazoNoPassadoException();
        }

        Prioridade prioridade = request.prioridade() != null ? request.prioridade() : PRIORIDADE_DEFAULT;
        int duracaoMinutos = request.duracaoMinutos() != null
                ? request.duracaoMinutos()
                : DURACAO_MINUTOS_DEFAULT;

        String projetoNome = null;
        if (request.projetoId() != null) {
            projetoNome = validarProjetoAtivoParaVinculo(usuarioId, request.projetoId()).getNome();
        }

        Tarefa tarefa = new Tarefa(
                UUID.randomUUID(),
                usuarioId,
                request.titulo(),
                request.descricao(),
                prioridade,
                request.prazo(),
                StatusTarefa.PENDENTE,
                duracaoMinutos,
                agora,
                agora
        );
        if (request.projetoId() != null) {
            tarefa.definirProjetoId(request.projetoId(), agora);
        }

        Tarefa salva = tarefaRepository.save(tarefa);
        tarefaRepository.flush();
        List<TagResumoResponse> tags = tagVinculoService.substituirTagsTarefa(
                usuarioId,
                salva.getId(),
                request.tagIds()
        );
        propostaGeracaoService.gerarSeTarefaNaJanelaAprovada(usuarioId, salva.getPrazo());
        return TarefaResponse.from(salva, projetoNome, salva.getDuracaoMinutos(), tags, null);
    }

    @Transactional(readOnly = true)
    public Page<TarefaResponse> listar(UUID usuarioId, TarefaFiltro filtro, Pageable pageable) {
        List<UUID> tagIds = filtro.tagIds() == null
                ? List.of()
                : filtro.tagIds().stream().filter(Objects::nonNull).distinct().toList();

        Specification<Tarefa> spec = Specification.allOf(
                TarefaSpecs.doUsuario(usuarioId),
                TarefaSpecs.comStatus(filtro.status()),
                TarefaSpecs.comPrioridade(filtro.prioridade()),
                TarefaSpecs.prazoEntre(filtro.prazoDe(), filtro.prazoAte()),
                TarefaSpecs.duracaoEntre(filtro.duracaoDe(), filtro.duracaoAte()),
                TarefaSpecs.criadoEntre(filtro.criadoDe(), filtro.criadoAte()),
                TarefaSpecs.atualizadoEntre(filtro.atualizadoDe(), filtro.atualizadoAte()),
                TarefaSpecs.filtroDelecao(filtro.incluirDeletadas(), filtro.somenteDeletadas()),
                TarefaSpecs.comProjetoId(filtro.projetoId()),
                TarefaSpecs.comAlgumaTag(tagIds)
        );

        Page<Tarefa> page = tarefaRepository.findAll(spec, pageable);
        Map<UUID, String> nomes = carregarNomesProjetos(page.getContent());
        Map<UUID, Integer> restantes = carregarRestantes(usuarioId, page.getContent());
        Map<UUID, List<TagResumoResponse>> tagsPorTarefa = tagVinculoService.mapearTagsPorTarefa(
                page.getContent().stream().map(Tarefa::getId).toList()
        );

        List<TarefaResponse> conteudo = new ArrayList<>();
        for (Tarefa t : page.getContent()) {
            List<TagResumoResponse> tags = tagsPorTarefa.getOrDefault(t.getId(), List.of());
            Integer score = tagIds.isEmpty()
                    ? null
                    : TagVinculoService.matchScore(
                            tags.stream().map(TagResumoResponse::id).toList(),
                            tagIds
                    );
            conteudo.add(TarefaResponse.from(
                    t,
                    t.getProjetoId() == null ? null : nomes.get(t.getProjetoId()),
                    restantes.getOrDefault(t.getId(), t.getDuracaoMinutos()),
                    tags,
                    score
            ));
        }

        if (tagIds.size() >= 2) {
            conteudo.sort(Comparator
                    .comparing((TarefaResponse r) -> r.tagMatchScore() == null ? 0 : r.tagMatchScore())
                    .reversed());
        }

        return new PageImpl<>(conteudo, pageable, page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public TarefaResponse buscarPorId(UUID usuarioId, UUID id) {
        Tarefa tarefa = buscarDoUsuario(usuarioId, id);
        return TarefaResponse.from(
                tarefa,
                nomeDoProjeto(tarefa.getProjetoId()),
                restanteDe(usuarioId, tarefa),
                tagVinculoService.listarTagsTarefa(tarefa.getId()),
                null
        );
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

        if (request.projetoIdPresente()) {
            aplicarVinculoProjeto(usuarioId, tarefa, request.projetoId(), agora);
        }

        List<TagResumoResponse> tags = request.tagIdsPresente()
                ? tagVinculoService.substituirTagsTarefa(usuarioId, tarefa.getId(), request.tagIds())
                : tagVinculoService.listarTagsTarefa(tarefa.getId());

        return TarefaResponse.from(
                tarefa,
                nomeDoProjeto(tarefa.getProjetoId()),
                restanteDe(usuarioId, tarefa),
                tags,
                null
        );
    }

    private void aplicarVinculoProjeto(UUID usuarioId, Tarefa tarefa, UUID novoProjetoId, Instant agora) {
        if (novoProjetoId == null) {
            tarefa.definirProjetoId(null, agora);
            return;
        }
        if (novoProjetoId.equals(tarefa.getProjetoId())) {
            return;
        }
        validarProjetoAtivoParaVinculo(usuarioId, novoProjetoId);
        tarefa.definirProjetoId(novoProjetoId, agora);
    }

    private Projeto validarProjetoAtivoParaVinculo(UUID usuarioId, UUID projetoId) {
        Projeto projeto = projetoRepository.findByIdAndUsuarioId(projetoId, usuarioId)
                .orElseThrow(ProjetoNaoEncontradoException::new);
        if (projeto.getStatus() == StatusProjeto.ARQUIVADO) {
            throw new ProjetoArquivadoException();
        }
        return projeto;
    }

    private Map<UUID, String> carregarNomesProjetos(java.util.List<Tarefa> tarefas) {
        Set<UUID> ids = tarefas.stream()
                .map(Tarefa::getProjetoId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return projetoRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Projeto::getId, Projeto::getNome));
    }

    private String nomeDoProjeto(UUID projetoId) {
        if (projetoId == null) {
            return null;
        }
        return projetoRepository.findById(projetoId).map(Projeto::getNome).orElse(null);
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
        Instant agora = clock.instant();
        tarefa.softDelete(agora);
        itemDecidirRepository.reabrirPorTarefa(usuarioId, tarefa.getId(), agora);
    }

    @Transactional
    public TarefaResponse restaurar(UUID usuarioId, UUID id) {
        Tarefa tarefa = buscarDoUsuario(usuarioId, id);
        if (tarefa.getDeletedAt() == null) {
            throw new TarefaNaoEncontradaException();
        }
        tarefa.restore(clock.instant());
        return TarefaResponse.from(
                tarefa,
                nomeDoProjeto(tarefa.getProjetoId()),
                restanteDe(usuarioId, tarefa),
                tagVinculoService.listarTagsTarefa(tarefa.getId()),
                null
        );
    }

    private Map<UUID, Integer> carregarRestantes(UUID usuarioId, List<Tarefa> tarefas) {
        if (tarefas.isEmpty()) {
            return Map.of();
        }
        Set<UUID> ids = tarefas.stream().map(Tarefa::getId).collect(Collectors.toSet());
        Map<UUID, Long> concluidos = new HashMap<>();
        for (Object[] linha : execucaoRepository.somarDuracaoMinutosPorTarefasEStatus(
                ids, usuarioId, StatusExecucao.CONCLUIDO)) {
            concluidos.put((UUID) linha[0], ((Number) linha[1]).longValue());
        }
        Map<UUID, Integer> restantes = new HashMap<>();
        for (Tarefa t : tarefas) {
            long feito = concluidos.getOrDefault(t.getId(), 0L);
            restantes.put(t.getId(), Math.max(0, t.getDuracaoMinutos() - (int) feito));
        }
        return restantes;
    }

    private int restanteDe(UUID usuarioId, Tarefa tarefa) {
        long feito = execucaoRepository.somarDuracaoMinutosPorTarefaEStatus(
                tarefa.getId(), usuarioId, StatusExecucao.CONCLUIDO);
        return Math.max(0, tarefa.getDuracaoMinutos() - (int) feito);
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
    }
}
