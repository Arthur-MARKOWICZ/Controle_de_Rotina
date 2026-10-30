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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.controlerotina.shared.exception.OrdenacaoInvalidaException;
import com.controlerotina.tarefa.dto.AtualizarTarefaRequest;
import com.controlerotina.tarefa.dto.CriarTarefaRequest;
import com.controlerotina.tarefa.dto.TarefaFiltro;
import com.controlerotina.tarefa.dto.TarefaResponse;
import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.service.TarefaService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/tarefas")
public class TarefaController {

    private static final Set<String> SORTS_PERMITIDOS = Set.of("prazo", "duracao", "prioridade");
    private static final int SIZE_MAX = 100;
    private static final int SIZE_DEFAULT = 20;

    private final TarefaService tarefaService;

    public TarefaController(TarefaService tarefaService) {
        this.tarefaService = tarefaService;
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
                projetoId
        );
        return tarefaService.listar(usuarioId, filtro, normalizarPageable(pageable));
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
