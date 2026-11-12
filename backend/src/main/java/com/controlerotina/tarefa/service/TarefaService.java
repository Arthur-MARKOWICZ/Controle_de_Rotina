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
import com.controlerotina.pendencia.dto.EsperasDeOrigem;
import com.controlerotina.pendencia.entity.TipoRecursoEspera;
import com.controlerotina.pendencia.service.EsperaService;
import com.controlerotina.execucao.repository.ExecucaoRepository;
import com.controlerotina.historico.PeriodoBRT;
import com.controlerotina.planner.service.PropostaGeracaoService;
import com.controlerotina.projeto.entity.Projeto;
import com.controlerotina.projeto.entity.StatusProjeto;
import com.controlerotina.projeto.repository.ProjetoRepository;
import com.controlerotina.shared.exception.AtrasadaComPrazoFuturoException;
import com.controlerotina.shared.exception.CampoTextoTarefaInvalidoException;
import com.controlerotina.shared.exception.DuracaoPaiComFilhosException;
import com.controlerotina.shared.exception.PaiComBlocoConcluidoException;
import com.controlerotina.shared.exception.PrazoNoPassadoException;
import com.controlerotina.shared.exception.ProjetoArquivadoException;
import com.controlerotina.shared.exception.ProjetoNaoEncontradoException;
import com.controlerotina.shared.exception.SubtarefaNivelInvalidoException;
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
    private final EsperaService esperaService;
    private final Clock clock;

    public TarefaService(
            TarefaRepository tarefaRepository,
            ProjetoRepository projetoRepository,
            PropostaGeracaoService propostaGeracaoService,
            ExecucaoRepository execucaoRepository,
            TagVinculoService tagVinculoService,
            ItemDecidirRepository itemDecidirRepository,
            EsperaService esperaService,
            Clock clock
    ) {
        this.tarefaRepository = tarefaRepository;
        this.projetoRepository = projetoRepository;
        this.propostaGeracaoService = propostaGeracaoService;
        this.execucaoRepository = execucaoRepository;
        this.tagVinculoService = tagVinculoService;
        this.itemDecidirRepository = itemDecidirRepository;
        this.esperaService = esperaService;
        this.clock = clock;
    }

    @Transactional
    public TarefaResponse criar(UUID usuarioId, CriarTarefaRequest request) {
        Instant agora = clock.instant();
        if (request.prazo() != null && request.prazo().isBefore(agora)) {
            throw new PrazoNoPassadoException();
        }

        Tarefa pai = request.parentId() == null
                ? null
                : carregarPaiParaSubtarefa(usuarioId, request.parentId());

        Prioridade prioridade = request.prioridade() != null ? request.prioridade() : PRIORIDADE_DEFAULT;
        int duracaoMinutos = request.duracaoMinutos() != null
                ? request.duracaoMinutos()
                : DURACAO_MINUTOS_DEFAULT;

        UUID projetoId = request.projetoId();
        String projetoNome = null;
        if (projetoId != null) {
            projetoNome = validarProjetoAtivoParaVinculo(usuarioId, projetoId).getNome();
        } else if (pai != null && pai.getProjetoId() != null) {
            projetoId = pai.getProjetoId();
            projetoNome = nomeDoProjeto(projetoId);
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
        if (projetoId != null) {
            tarefa.definirProjetoId(projetoId, agora);
        }
        if (pai != null) {
            tarefa.definirParentId(pai.getId());
        }

        Tarefa salva = tarefaRepository.save(tarefa);
        tarefaRepository.flush();
        if (pai != null && pai.getStatus() == StatusTarefa.FINALIZADA) {
            pai.atualizar(null, null, null, null, StatusTarefa.PENDENTE, null, agora);
        }
        if (pai != null) {
            recomputarDuracaoPai(pai);
        }
        List<TagResumoResponse> tags = tagVinculoService.substituirTagsTarefa(
                usuarioId,
                salva.getId(),
                request.tagIds()
        );
        propostaGeracaoService.gerarSeTarefaNaJanelaAprovada(usuarioId, salva.getPrazo());
        EsperasDeOrigem esperas = esperaService.enriquecerTarefas(usuarioId, List.of(salva));
        return TarefaResponse.from(
                salva,
                projetoNome,
                salva.getDuracaoMinutos(),
                tags,
                null,
                pai == null ? null : pai.getTitulo(),
                false,
                esperas.de(salva.getId()),
                esperas.bloqueada(salva.getId())
        );
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
                TarefaSpecs.comAlgumaTag(tagIds),
                TarefaSpecs.comParentId(filtro.parentId()),
                TarefaSpecs.somenteRaizes(filtro.somenteRaizes())
        );

        Page<Tarefa> page = tarefaRepository.findAll(spec, pageable);
        Map<UUID, String> nomes = carregarNomesProjetos(page.getContent());
        Map<UUID, Integer> restantes = carregarRestantes(usuarioId, page.getContent());
        Map<UUID, List<TagResumoResponse>> tagsPorTarefa = tagVinculoService.mapearTagsPorTarefa(
                page.getContent().stream().map(Tarefa::getId).toList()
        );
        Map<UUID, String> titulosPais = carregarTitulosPais(page.getContent());
        Set<UUID> idsComFilhos = carregarIdsComFilhos(page.getContent());
        EsperasDeOrigem esperas = esperaService.enriquecerTarefas(usuarioId, page.getContent());

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
                    score,
                    t.getParentId() == null ? null : titulosPais.get(t.getParentId()),
                    idsComFilhos.contains(t.getId()),
                    esperas.de(t.getId()),
                    esperas.bloqueada(t.getId())
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
        return toResponse(
                usuarioId,
                tarefa,
                tagVinculoService.listarTagsTarefa(tarefa.getId())
        );
    }

    @Transactional
    public TarefaResponse atualizar(UUID usuarioId, UUID id, AtualizarTarefaRequest request) {
        Tarefa tarefa = buscarDoUsuario(usuarioId, id);
        Instant agora = clock.instant();

        validarCamposTexto(request);
        if (request.duracaoMinutos() != null
                && tarefaRepository.existsByParentIdAndDeletedAtIsNull(tarefa.getId())) {
            throw new DuracaoPaiComFilhosException();
        }

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

        if (statusParaGravar == StatusTarefa.FINALIZADA) {
            esperaService.garantirTarefaNaoBloqueada(usuarioId, tarefa);
        }

        tarefa.atualizar(
                request.titulo(),
                request.descricao(),
                request.prioridade(),
                request.prazo(),
                statusParaGravar,
                request.duracaoMinutos(),
                agora
        );
        aplicarCascataDeStatus(tarefa, request.status(), agora);

        if (request.projetoIdPresente()) {
            aplicarVinculoProjeto(usuarioId, tarefa, request.projetoId(), agora);
        }

        List<TagResumoResponse> tags = request.tagIdsPresente()
                ? tagVinculoService.substituirTagsTarefa(usuarioId, tarefa.getId(), request.tagIds())
                : tagVinculoService.listarTagsTarefa(tarefa.getId());

        if (request.duracaoMinutos() != null && tarefa.getParentId() != null) {
            tarefaRepository.flush();
            recomputarDuracaoPaiPorId(tarefa.getParentId());
        }

        return toResponse(usuarioId, tarefa, tags);
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

    private TarefaResponse toResponse(UUID usuarioId, Tarefa tarefa, List<TagResumoResponse> tags) {
        EsperasDeOrigem esperas = esperaService.enriquecerTarefas(usuarioId, List.of(tarefa));
        return TarefaResponse.from(
                tarefa,
                nomeDoProjeto(tarefa.getProjetoId()),
                restanteDe(usuarioId, tarefa),
                tags,
                null,
                tituloDoPai(tarefa.getParentId()),
                tarefaRepository.existsByParentIdAndDeletedAtIsNull(tarefa.getId()),
                esperas.de(tarefa.getId()),
                esperas.bloqueada(tarefa.getId())
        );
    }

    private void recomputarDuracaoPaiPorId(UUID parentId) {
        tarefaRepository.findById(parentId).ifPresent(this::recomputarDuracaoPai);
    }

    private void recomputarDuracaoPai(Tarefa pai) {
        long soma = tarefaRepository.somarDuracaoFilhosNaoDeletados(pai.getId());
        if (soma == 0) {
            return;
        }
        pai.atualizar(null, null, null, null, null, (int) soma, clock.instant());
    }

    private String tituloDoPai(UUID parentId) {
        if (parentId == null) {
            return null;
        }
        return tarefaRepository.findById(parentId).map(Tarefa::getTitulo).orElse(null);
    }

    private Map<UUID, String> carregarTitulosPais(List<Tarefa> tarefas) {
        Set<UUID> ids = tarefas.stream()
                .map(Tarefa::getParentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return tarefaRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Tarefa::getId, Tarefa::getTitulo));
    }

    private Set<UUID> carregarIdsComFilhos(List<Tarefa> tarefas) {
        Set<UUID> ids = tarefas.stream().map(Tarefa::getId).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Set.of();
        }
        return tarefaRepository.findParentIdsComFilhosNaoDeletados(ids);
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

    private void aplicarCascataDeStatus(Tarefa tarefa, StatusTarefa statusSolicitado, Instant agora) {
        if (statusSolicitado == null) {
            return;
        }
        if (tarefa.getParentId() == null) {
            if (statusSolicitado == StatusTarefa.FINALIZADA || statusSolicitado == StatusTarefa.CANCELADA) {
                for (Tarefa filho : tarefaRepository.findByParentIdAndDeletedAtIsNull(tarefa.getId())) {
                    filho.atualizar(null, null, null, null, statusSolicitado, null, agora);
                }
            }
            return;
        }
        if (statusAberto(statusSolicitado)) {
            reabrirPaiSeFinalizada(tarefa.getParentId(), agora);
            return;
        }
        if (statusSolicitado == StatusTarefa.FINALIZADA) {
            tarefaRepository.flush();
            finalizarPaiSeTodosFilhosFinalizados(tarefa.getParentId(), agora);
        }
    }

    private void reabrirPaiSeFinalizada(UUID parentId, Instant agora) {
        Tarefa pai = tarefaRepository.findById(parentId).orElse(null);
        if (pai == null || pai.getDeletedAt() != null || pai.getStatus() != StatusTarefa.FINALIZADA) {
            return;
        }
        pai.atualizar(null, null, null, null, StatusTarefa.PENDENTE, null, agora);
    }

    private void finalizarPaiSeTodosFilhosFinalizados(UUID parentId, Instant agora) {
        if (tarefaRepository.existsByParentIdAndDeletedAtIsNullAndStatusNot(parentId, StatusTarefa.FINALIZADA)) {
            return;
        }
        Tarefa pai = tarefaRepository.findById(parentId).orElse(null);
        if (pai == null || pai.getDeletedAt() != null || pai.getStatus() == StatusTarefa.FINALIZADA) {
            return;
        }
        pai.atualizar(null, null, null, null, StatusTarefa.FINALIZADA, null, agora);
    }

    private void excluirFilhosAtivos(UUID usuarioId, UUID parentId, Instant agora) {
        List<Tarefa> filhos = tarefaRepository.findByParentIdAndDeletedAtIsNull(parentId);
        for (Tarefa filho : filhos) {
            filho.softDelete(agora);
            esperaService.apagarPorRecurso(usuarioId, TipoRecursoEspera.TAREFA, filho.getId());
        }
        tarefaRepository.flush();
        for (Tarefa filho : filhos) {
            itemDecidirRepository.reabrirPorTarefa(usuarioId, filho.getId(), agora);
        }
    }

    private void restaurarFilhosDeletados(UUID parentId, Instant agora) {
        for (Tarefa filho : tarefaRepository.findByParentIdAndDeletedAtIsNotNull(parentId)) {
            filho.restore(agora);
        }
    }

    private void restaurarPaiSeDeletado(UUID parentId, Instant agora) {
        Tarefa pai = tarefaRepository.findById(parentId).orElse(null);
        if (pai == null || pai.getDeletedAt() == null) {
            return;
        }
        pai.restore(agora);
    }

    private static boolean statusAberto(StatusTarefa status) {
        return status == StatusTarefa.PENDENTE
                || status == StatusTarefa.EM_PROGRESSO
                || status == StatusTarefa.ATRASADA;
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
        UUID parentId = tarefa.getParentId();
        tarefa.softDelete(agora);
        esperaService.apagarPorRecurso(usuarioId, TipoRecursoEspera.TAREFA, tarefa.getId());
        itemDecidirRepository.reabrirPorTarefa(usuarioId, tarefa.getId(), agora);
        if (parentId == null) {
            excluirFilhosAtivos(usuarioId, tarefa.getId(), agora);
        } else {
            tarefaRepository.flush();
            recomputarDuracaoPaiPorId(parentId);
        }
    }

    @Transactional
    public TarefaResponse restaurar(UUID usuarioId, UUID id) {
        Tarefa tarefa = buscarDoUsuario(usuarioId, id);
        if (tarefa.getDeletedAt() == null) {
            throw new TarefaNaoEncontradaException();
        }
        Instant agora = clock.instant();
        tarefa.restore(agora);
        if (tarefa.getParentId() == null) {
            restaurarFilhosDeletados(tarefa.getId(), agora);
            recomputarDuracaoPai(tarefa);
        } else {
            restaurarPaiSeDeletado(tarefa.getParentId(), agora);
            tarefaRepository.flush();
            recomputarDuracaoPaiPorId(tarefa.getParentId());
        }
        return toResponse(
                usuarioId,
                tarefa,
                tagVinculoService.listarTagsTarefa(tarefa.getId())
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

    private Tarefa carregarPaiParaSubtarefa(UUID usuarioId, UUID parentId) {
        Tarefa pai = tarefaRepository.findByIdAndUsuarioId(parentId, usuarioId)
                .orElseThrow(TarefaNaoEncontradaException::new);
        if (pai.getDeletedAt() != null) {
            throw new TarefaNaoEncontradaException();
        }
        if (pai.getParentId() != null) {
            throw new SubtarefaNivelInvalidoException();
        }
        if (execucaoRepository.existsPorTarefaEStatus(pai.getId(), StatusExecucao.CONCLUIDO)) {
            throw new PaiComBlocoConcluidoException();
        }
        return pai;
    }

    private static void validarCamposTexto(AtualizarTarefaRequest request) {
        if (request.titulo() != null && request.titulo().isBlank()) {
            throw new CampoTextoTarefaInvalidoException("Título não pode ser vazio.");
        }
    }
}
