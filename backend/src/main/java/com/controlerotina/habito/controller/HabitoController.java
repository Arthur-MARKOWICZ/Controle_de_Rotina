package com.controlerotina.habito.controller;

import java.time.LocalDate;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.controlerotina.execucao.dto.ExecucaoResponse;
import com.controlerotina.execucao.dto.ResultadoConclusao;
import com.controlerotina.execucao.dto.ResultadoInicio;
import com.controlerotina.habito.dto.AtualizarHabitoRequest;
import com.controlerotina.habito.dto.CriarHabitoRequest;
import com.controlerotina.habito.dto.HabitoDoDiaResponse;
import com.controlerotina.habito.dto.HabitoResponse;
import com.controlerotina.habito.entity.StatusHabito;
import com.controlerotina.habito.service.HabitoDoDiaService;
import com.controlerotina.habito.service.HabitoExecucaoAvulsaService;
import com.controlerotina.habito.service.HabitoService;
import com.controlerotina.xp.dto.XpConclusaoResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/habitos")
public class HabitoController {

    private static final int SIZE_MAX = 100;
    private static final int SIZE_DEFAULT = 20;

    private final HabitoService habitoService;
    private final HabitoDoDiaService habitoDoDiaService;
    private final HabitoExecucaoAvulsaService habitoExecucaoAvulsaService;

    public HabitoController(
            HabitoService habitoService,
            HabitoDoDiaService habitoDoDiaService,
            HabitoExecucaoAvulsaService habitoExecucaoAvulsaService
    ) {
        this.habitoService = habitoService;
        this.habitoDoDiaService = habitoDoDiaService;
        this.habitoExecucaoAvulsaService = habitoExecucaoAvulsaService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HabitoResponse criar(
            @AuthenticationPrincipal UUID usuarioId,
            @Valid @RequestBody CriarHabitoRequest request
    ) {
        return habitoService.criar(usuarioId, request);
    }

    @GetMapping
    public Page<HabitoResponse> listar(
            @AuthenticationPrincipal UUID usuarioId,
            @RequestParam(required = false) StatusHabito status,
            @RequestParam(defaultValue = "false") boolean incluirDeletadas,
            @RequestParam(defaultValue = "false") boolean somenteDeletadas,
            @RequestParam(required = false) List<UUID> tagId,
            Pageable pageable
    ) {
        return habitoService.listar(
                usuarioId,
                status,
                incluirDeletadas,
                somenteDeletadas,
                tagId,
                normalizarPageable(pageable)
        );
    }

    @GetMapping("/do-dia")
    public List<HabitoDoDiaResponse> listarDoDia(
            @AuthenticationPrincipal UUID usuarioId,
            @RequestParam(required = false) LocalDate data
    ) {
        return habitoDoDiaService.listarDoDia(usuarioId, data);
    }

    @PostMapping("/{id}/iniciar")
    public ResponseEntity<ExecucaoResponse> iniciar(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID id,
            @RequestParam(required = false) LocalDate data
    ) {
        ResultadoInicio resultado = habitoExecucaoAvulsaService.iniciar(usuarioId, id, data);
        HttpStatus status = resultado.criada() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ExecucaoResponse.from(resultado.execucao()));
    }

    @PostMapping("/{id}/concluir-direto")
    public ExecucaoResponse concluirDireto(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID id,
            @RequestParam(required = false) LocalDate data
    ) {
        ResultadoConclusao resultado = habitoExecucaoAvulsaService.concluirDireto(usuarioId, id, data);
        return ExecucaoResponse.from(resultado.execucao(), XpConclusaoResponse.from(resultado.xp()));
    }

    @GetMapping("/{id}")
    public HabitoResponse buscar(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID id
    ) {
        return habitoService.buscarPorId(usuarioId, id);
    }

    @PatchMapping("/{id}")
    public HabitoResponse atualizar(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID id,
            @Valid @RequestBody AtualizarHabitoRequest request
    ) {
        return habitoService.atualizar(usuarioId, id, request);
    }

    @PostMapping("/{id}/pausar")
    public HabitoResponse pausar(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID id
    ) {
        return habitoService.pausar(usuarioId, id);
    }

    @PostMapping("/{id}/reativar")
    public HabitoResponse reativar(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID id
    ) {
        return habitoService.reativar(usuarioId, id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID id
    ) {
        habitoService.excluir(usuarioId, id);
    }

    @PostMapping("/{id}/restore")
    public HabitoResponse restaurar(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID id
    ) {
        return habitoService.restaurar(usuarioId, id);
    }

    private static Pageable normalizarPageable(Pageable pageable) {
        int size = pageable.getPageSize() <= 0 ? SIZE_DEFAULT : Math.min(pageable.getPageSize(), SIZE_MAX);
        int page = Math.max(pageable.getPageNumber(), 0);
        Sort sort = pageable.getSort().isSorted()
                ? pageable.getSort()
                : Sort.by(Sort.Order.asc("titulo"));
        return PageRequest.of(page, size, sort);
    }
}
