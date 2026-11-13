package com.controlerotina.tarefa.controller;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.controlerotina.execucao.dto.ExecucaoResponse;
import com.controlerotina.execucao.dto.ResultadoConclusao;
import com.controlerotina.execucao.dto.ResultadoInicio;
import com.controlerotina.pendencia.dto.CriarEsperaRequest;
import com.controlerotina.pendencia.dto.EsperaResumo;
import com.controlerotina.pendencia.entity.TipoRecursoEspera;
import com.controlerotina.pendencia.service.EsperaService;
import com.controlerotina.shared.dto.ReordenarRequest;
import com.controlerotina.shared.exception.OrdenacaoInvalidaException;
import com.controlerotina.tarefa.dto.AtualizarTarefaRequest;
import com.controlerotina.tarefa.dto.CriarTarefaRequest;
import com.controlerotina.tarefa.dto.SessaoTarefaRequest;
import com.controlerotina.tarefa.dto.TarefaFiltro;
import com.controlerotina.tarefa.dto.TarefaResponse;
import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.service.TarefaExecucaoAvulsaService;
import com.controlerotina.tarefa.service.TarefaService;
import com.controlerotina.xp.dto.XpConclusaoResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/tarefas")
public class TarefaController {

    private static final Set<String> SORTS_PERMITIDOS = Set.of("prazo", "duracao", "prioridade", "ordem");
    private static final int SIZE_MAX = 100;
    private static final int SIZE_DEFAULT = 20;

    private final TarefaService tarefaService;
    private final TarefaExecucaoAvulsaService tarefaExecucaoAvulsaService;
    private final EsperaService esperaService;

    public TarefaController(
            TarefaService tarefaService,
            TarefaExecucaoAvulsaService tarefaExecucaoAvulsaService,
            EsperaService esperaService
    ) {
        this.tarefaService = tarefaService;
        this.tarefaExecucaoAvulsaService = tarefaExecucaoAvulsaService;
        this.esperaService = esperaService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TarefaResponse criar(
            @AuthenticationPrincipal UUID usuarioId,
            @Valid @RequestBody CriarTarefaRequest request
    ) {
        return tarefaService.criar(usuarioId, request);
    }

    @GetMapping
    public Page<TarefaResponse> listar(
            @AuthenticationPrincipal UUID usuarioId,
            @RequestParam(required = false) List<StatusTarefa> status,
            @RequestParam(required = false) List<Prioridade> prioridade,
            @RequestParam(required = false) Instant prazoDe,
            @RequestParam(required = false) Instant prazoAte,
            @RequestParam(required = false) Integer duracaoDe,
            @RequestParam(required = false) Integer duracaoAte,
            @RequestParam(required = false) Instant criadoDe,
            @RequestParam(required = false) Instant criadoAte,
            @RequestParam(required = false) Instant atualizadoDe,
            @RequestParam(required = false) Instant atualizadoAte,
            @RequestParam(defaultValue = "false") boolean incluirDeletadas,
            @RequestParam(defaultValue = "false") boolean somenteDeletadas,
            @RequestParam(required = false) UUID projetoId,
            @RequestParam(required = false) List<UUID> tagId,
            @RequestParam(required = false) UUID parentId,
            @RequestParam(defaultValue = "false") boolean somenteRaizes,
            @RequestParam(defaultValue = "false") boolean semPrazo,
            @RequestParam(defaultValue = "false") boolean semProjeto,
            @RequestParam(defaultValue = "false") boolean semTag,
            @RequestParam(required = false) Boolean bloqueada,
            Pageable pageable
    ) {
        TarefaFiltro filtro = new TarefaFiltro(
                status,
                prioridade,
                prazoDe,
                prazoAte,
                duracaoDe,
                duracaoAte,
                criadoDe,
                criadoAte,
                atualizadoDe,
                atualizadoAte,
                incluirDeletadas,
                somenteDeletadas,
                projetoId,
                tagId,
                parentId,
                somenteRaizes,
                semPrazo,
                semProjeto,
                semTag,
                bloqueada
        );
        return tarefaService.listar(usuarioId, filtro, normalizarPageable(pageable));
    }

    @PostMapping("/{id}/iniciar")
    public ResponseEntity<ExecucaoResponse> iniciar(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID id,
            @RequestBody(required = false) SessaoTarefaRequest request
    ) {
        SessaoTarefaRequest body = request != null ? request : new SessaoTarefaRequest(null, null, null);
        ResultadoInicio resultado = tarefaExecucaoAvulsaService.iniciar(
                usuarioId,
                id,
                body.duracaoMinutos(),
                body.data(),
                body.blocoId()
        );
        HttpStatus status = resultado.criada() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ExecucaoResponse.from(resultado.execucao()));
    }

    @PostMapping("/{id}/concluir-direto")
    public ExecucaoResponse concluirDireto(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID id,
            @RequestBody(required = false) SessaoTarefaRequest request
    ) {
        SessaoTarefaRequest body = request != null ? request : new SessaoTarefaRequest(null, null, null);
        ResultadoConclusao resultado = tarefaExecucaoAvulsaService.concluirDireto(
                usuarioId,
                id,
                body.duracaoMinutos(),
                body.data(),
                body.blocoId()
        );
        return ExecucaoResponse.from(resultado.execucao(), XpConclusaoResponse.from(resultado.xp()));
    }

    @PostMapping("/{id}/esperas")
    @ResponseStatus(HttpStatus.CREATED)
    public EsperaResumo criarEspera(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID id,
            @Valid @RequestBody CriarEsperaRequest request
    ) {
        return esperaService.criar(usuarioId, TipoRecursoEspera.TAREFA, id, request);
    }

    @GetMapping("/{id}/esperas")
    public List<EsperaResumo> listarEsperas(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID id
    ) {
        return esperaService.listar(usuarioId, TipoRecursoEspera.TAREFA, id);
    }

    @DeleteMapping("/{id}/esperas/{esperaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluirEspera(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID id,
            @PathVariable UUID esperaId
    ) {
        esperaService.excluir(usuarioId, TipoRecursoEspera.TAREFA, id, esperaId);
    }

    @GetMapping("/{id}")
    public TarefaResponse buscar(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID id
    ) {
        return tarefaService.buscarPorId(usuarioId, id);
    }

    @PatchMapping("/{id}")
    public TarefaResponse atualizar(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID id,
            @Valid @RequestBody AtualizarTarefaRequest request
    ) {
        return tarefaService.atualizar(usuarioId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID id
    ) {
        tarefaService.excluir(usuarioId, id);
    }

    @PostMapping("/{id}/restore")
    public TarefaResponse restaurar(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID id
    ) {
        return tarefaService.restaurar(usuarioId, id);
    }

    @PutMapping("/ordem")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reordenar(
            @AuthenticationPrincipal UUID usuarioId,
            @Valid @RequestBody ReordenarRequest request
    ) {
        tarefaService.reordenar(usuarioId, request.ids());
    }

    private static Pageable normalizarPageable(Pageable pageable) {
        int size = pageable.getPageSize() <= 0 ? SIZE_DEFAULT : Math.min(pageable.getPageSize(), SIZE_MAX);
        int page = Math.max(pageable.getPageNumber(), 0);

        List<Sort.Order> orders = pageable.getSort().stream()
                .map(TarefaController::mapearOrder)
                .toList();

        Sort sort = orders.isEmpty() ? Sort.by(Sort.Order.asc("prazo")) : Sort.by(orders);
        return PageRequest.of(page, size, sort);
    }

    private static Sort.Order mapearOrder(Sort.Order order) {
        String propriedade = order.getProperty();
        if (!SORTS_PERMITIDOS.contains(propriedade)) {
            throw new OrdenacaoInvalidaException(propriedade);
        }
        String campo = "duracao".equals(propriedade) ? "duracaoMinutos" : propriedade;
        return new Sort.Order(order.getDirection(), campo);
    }
}
