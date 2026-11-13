package com.controlerotina.tarefa.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.controlerotina.decidir.repository.ItemDecidirRepository;
import com.controlerotina.execucao.repository.ExecucaoRepository;
import com.controlerotina.pendencia.dto.EsperasDeOrigem;
import com.controlerotina.pendencia.service.EsperaService;
import com.controlerotina.planner.service.PropostaGeracaoService;
import com.controlerotina.projeto.repository.ProjetoRepository;
import com.controlerotina.tag.service.TagVinculoService;
import com.controlerotina.tarefa.dto.TarefaFiltro;
import com.controlerotina.tarefa.dto.TarefaResponse;
import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;
import com.controlerotina.tarefa.repository.TarefaRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TarefaServiceListarBloqueadaTest {

    private static final Instant AGORA = Instant.parse("2026-11-13T15:00:00Z");
    private static final UUID USUARIO = UUID.randomUUID();
    private static final Pageable PAGINA = PageRequest.of(0, 20);

    @Mock
    TarefaRepository tarefaRepository;
    @Mock
    ProjetoRepository projetoRepository;
    @Mock
    PropostaGeracaoService propostaGeracaoService;
    @Mock
    ExecucaoRepository execucaoRepository;
    @Mock
    TagVinculoService tagVinculoService;
    @Mock
    ItemDecidirRepository itemDecidirRepository;
    @Mock
    EsperaService esperaService;

    private TarefaService tarefaService;

    @BeforeEach
    void setUp() {
        tarefaService = new TarefaService(
                tarefaRepository,
                projetoRepository,
                propostaGeracaoService,
                execucaoRepository,
                tagVinculoService,
                itemDecidirRepository,
                esperaService,
                Clock.fixed(AGORA, ZoneOffset.UTC)
        );
        when(tagVinculoService.mapearTagsPorTarefa(any())).thenReturn(Map.of());
        when(esperaService.enriquecerTarefas(eq(USUARIO), anyCollection()))
                .thenReturn(EsperasDeOrigem.vazio());
    }

    @Test
    void deveCalcularBloqueioSobreTodasAsCandidatasQuandoBloqueadaTrue() {
        Tarefa bloqueada = tarefa("Bloqueada", StatusTarefa.PENDENTE);
        Tarefa livre = tarefa("Livre", StatusTarefa.PENDENTE);
        when(tarefaRepository.findAll(any(Specification.class))).thenReturn(List.of(bloqueada, livre));
        when(esperaService.idsTarefasBloqueadas(USUARIO, List.of(bloqueada, livre)))
                .thenReturn(Set.of(bloqueada.getId()));
        when(tarefaRepository.findAll(any(Specification.class), eq(PAGINA)))
                .thenReturn(new PageImpl<>(List.of(bloqueada), PAGINA, 1));

        Page<TarefaResponse> page = tarefaService.listar(USUARIO, filtro(null, true), PAGINA);

        assertThat(page.getContent()).extracting(TarefaResponse::id).containsExactly(bloqueada.getId());
        assertThat(page.getTotalElements()).isEqualTo(1);
        verify(esperaService).idsTarefasBloqueadas(USUARIO, List.of(bloqueada, livre));
    }

    @Test
    void deveCalcularBloqueioTambemQuandoBloqueadaFalse() {
        Tarefa livre = tarefa("Livre", StatusTarefa.PENDENTE);
        when(tarefaRepository.findAll(any(Specification.class))).thenReturn(List.of(livre));
        when(esperaService.idsTarefasBloqueadas(USUARIO, List.of(livre))).thenReturn(Set.of());
        when(tarefaRepository.findAll(any(Specification.class), eq(PAGINA)))
                .thenReturn(new PageImpl<>(List.of(livre), PAGINA, 1));

        Page<TarefaResponse> page = tarefaService.listar(USUARIO, filtro(null, false), PAGINA);

        assertThat(page.getContent()).extracting(TarefaResponse::id).containsExactly(livre.getId());
        verify(esperaService).idsTarefasBloqueadas(USUARIO, List.of(livre));
    }

    @Test
    void naoDeveCalcularBloqueioQuandoFiltroAusente() {
        when(tarefaRepository.findAll(any(Specification.class), eq(PAGINA)))
                .thenReturn(new PageImpl<>(List.of(), PAGINA, 0));

        tarefaService.listar(USUARIO, filtro(null, null), PAGINA);

        verify(tarefaRepository, never()).findAll(any(Specification.class));
        verify(esperaService, never()).idsTarefasBloqueadas(any(), anyCollection());
    }

    @Test
    void naoDeveChamarEnrichDeBloqueioQuandoFiltroBaseVazio() {
        when(tarefaRepository.findAll(any(Specification.class))).thenReturn(List.of());
        when(tarefaRepository.findAll(any(Specification.class), eq(PAGINA)))
                .thenReturn(new PageImpl<>(List.of(), PAGINA, 0));

        Page<TarefaResponse> page = tarefaService.listar(USUARIO, filtro(null, true), PAGINA);

        assertThat(page.getContent()).isEmpty();
        verify(esperaService, never()).idsTarefasBloqueadas(any(), anyCollection());
    }

    @Test
    void deveCombinarFiltroBloqueadaComStatusAtrasada() {
        Tarefa atrasada = tarefa("Atrasada", StatusTarefa.ATRASADA);
        when(tarefaRepository.findAll(any(Specification.class))).thenReturn(List.of(atrasada));
        when(esperaService.idsTarefasBloqueadas(USUARIO, List.of(atrasada)))
                .thenReturn(Set.of(atrasada.getId()));
        when(tarefaRepository.findAll(any(Specification.class), eq(PAGINA)))
                .thenReturn(new PageImpl<>(List.of(atrasada), PAGINA, 1));

        Page<TarefaResponse> page = tarefaService.listar(
                USUARIO, filtro(List.of(StatusTarefa.ATRASADA), true), PAGINA);

        assertThat(page.getContent()).extracting(TarefaResponse::status)
                .containsExactly(StatusTarefa.ATRASADA);
        verify(tarefaRepository, times(1)).findAll(any(Specification.class));
    }

    @Test
    void devePropagarFalhaDoCalculoDeBloqueioSemPaginar() {
        when(tarefaRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(tarefa("X", StatusTarefa.PENDENTE)));
        when(esperaService.idsTarefasBloqueadas(eq(USUARIO), anyCollection()))
                .thenThrow(new IllegalStateException("grafo inválido"));

        assertThatThrownBy(() -> tarefaService.listar(USUARIO, filtro(null, true), PAGINA))
                .isInstanceOf(IllegalStateException.class);
        verify(tarefaRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    private static Tarefa tarefa(String titulo, StatusTarefa status) {
        return new Tarefa(
                UUID.randomUUID(),
                USUARIO,
                titulo,
                null,
                Prioridade.MEDIA,
                null,
                status,
                60,
                AGORA,
                AGORA
        );
    }

    private static TarefaFiltro filtro(List<StatusTarefa> status, Boolean bloqueada) {
        return new TarefaFiltro(
                status, null, null, null, null, null, null, null, null, null,
                false, false, null, null, null, false, false, false, false,
                bloqueada
        );
    }
}
