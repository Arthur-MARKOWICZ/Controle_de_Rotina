package com.controlerotina.tarefa.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.springframework.data.jpa.domain.Specification;

import com.controlerotina.decidir.repository.ItemDecidirRepository;
import com.controlerotina.execucao.entity.StatusExecucao;
import com.controlerotina.execucao.repository.ExecucaoRepository;
import com.controlerotina.objetivo.dto.ObjetivoResumoResponse;
import com.controlerotina.objetivo.entity.StatusObjetivo;
import com.controlerotina.objetivo.service.ObjetivoVinculoService;
import com.controlerotina.pendencia.dto.EsperasDeOrigem;
import com.controlerotina.pendencia.service.EsperaService;
import com.controlerotina.planner.service.PropostaGeracaoService;
import com.controlerotina.projeto.repository.ProjetoRepository;
import com.controlerotina.shared.exception.ObjetivoNaoEncontradoException;
import com.controlerotina.shared.exception.SubtarefaObjetivoException;
import com.controlerotina.tag.service.TagVinculoService;
import com.controlerotina.tarefa.dto.AtualizarTarefaRequest;
import com.controlerotina.tarefa.dto.CriarTarefaRequest;
import com.controlerotina.tarefa.dto.TarefaFiltro;
import com.controlerotina.tarefa.dto.TarefaResponse;
import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;
import com.controlerotina.tarefa.repository.TarefaRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TarefaServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-17T15:00:00Z");
    private static final UUID USUARIO = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID TAREFA_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID PAI_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OBJETIVO_A = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID OBJETIVO_B = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

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
    ObjetivoVinculoService objetivoVinculoService;
    @Mock
    ItemDecidirRepository itemDecidirRepository;
    @Mock
    EsperaService esperaService;

    private TarefaService service;

    @BeforeEach
    void setUp() {
        service = new TarefaService(
                tarefaRepository,
                projetoRepository,
                propostaGeracaoService,
                execucaoRepository,
                tagVinculoService,
                objetivoVinculoService,
                itemDecidirRepository,
                esperaService,
                Clock.fixed(AGORA, ZoneOffset.UTC)
        );
        when(tarefaRepository.save(any(Tarefa.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tagVinculoService.substituirTagsTarefa(eq(USUARIO), any(), any())).thenReturn(List.of());
        when(tagVinculoService.listarTagsTarefa(any())).thenReturn(List.of());
        when(esperaService.enriquecerTarefas(eq(USUARIO), anyCollection()))
                .thenReturn(EsperasDeOrigem.vazio());
        when(objetivoVinculoService.mapearObjetivosPorTarefa(any())).thenReturn(Map.of());
    }

    @Test
    void deveCriarTarefaRaizComDoisObjetivos() {
        when(tarefaRepository.findMaxOrdemIrmaos(USUARIO, null)).thenReturn(0);
        when(objetivoVinculoService.substituirObjetivosTarefa(
                eq(USUARIO), any(), eq(List.of(OBJETIVO_A, OBJETIVO_B))))
                .thenReturn(List.of(resumo(OBJETIVO_A, "A"), resumo(OBJETIVO_B, "B")));

        TarefaResponse response = service.criar(USUARIO, criarRaiz(List.of(OBJETIVO_A, OBJETIVO_B)));

        assertThat(response.objetivos()).extracting(ObjetivoResumoResponse::id)
                .containsExactly(OBJETIVO_A, OBJETIVO_B);
        verify(objetivoVinculoService).substituirObjetivosTarefa(
                eq(USUARIO), any(), eq(List.of(OBJETIVO_A, OBJETIVO_B)));
    }

    @Test
    void patchComObjetivoIdsSubstituiOConjunto() {
        Tarefa tarefa = raiz();
        when(tarefaRepository.findByIdAndUsuarioId(TAREFA_ID, USUARIO)).thenReturn(Optional.of(tarefa));
        when(objetivoVinculoService.substituirObjetivosTarefa(USUARIO, TAREFA_ID, List.of(OBJETIVO_B)))
                .thenReturn(List.of(resumo(OBJETIVO_B, "B")));

        TarefaResponse response = service.atualizar(
                USUARIO, TAREFA_ID, patchObjetivos(List.of(OBJETIVO_B), true));

        assertThat(response.objetivos()).extracting(ObjetivoResumoResponse::id)
                .containsExactly(OBJETIVO_B);
        verify(objetivoVinculoService).substituirObjetivosTarefa(USUARIO, TAREFA_ID, List.of(OBJETIVO_B));
        verify(objetivoVinculoService, never()).listarObjetivosTarefa(any());
    }

    @Test
    void patchSemObjetivoIdsMantemVinculos() {
        Tarefa tarefa = raiz();
        when(tarefaRepository.findByIdAndUsuarioId(TAREFA_ID, USUARIO)).thenReturn(Optional.of(tarefa));
        when(objetivoVinculoService.listarObjetivosTarefa(TAREFA_ID))
                .thenReturn(List.of(resumo(OBJETIVO_A, "A")));

        TarefaResponse response = service.atualizar(
                USUARIO, TAREFA_ID, patchObjetivos(null, false));

        assertThat(response.objetivos()).extracting(ObjetivoResumoResponse::id)
                .containsExactly(OBJETIVO_A);
        verify(objetivoVinculoService).listarObjetivosTarefa(TAREFA_ID);
        verify(objetivoVinculoService, never()).substituirObjetivosTarefa(any(), any(), any());
    }

    @Test
    void criarSubtarefaComObjetivoIdsLanca400AntesDeSubstituir() {
        Tarefa pai = raiz(PAI_ID, "Pai");
        when(tarefaRepository.findByIdAndUsuarioId(PAI_ID, USUARIO)).thenReturn(Optional.of(pai));
        when(execucaoRepository.existsPorTarefaEStatus(PAI_ID, StatusExecucao.CONCLUIDO)).thenReturn(false);
        when(tarefaRepository.findMaxOrdemIrmaos(USUARIO, PAI_ID)).thenReturn(0);

        assertThatThrownBy(() -> service.criar(USUARIO, criarFilho(PAI_ID, List.of(OBJETIVO_A))))
                .isInstanceOf(SubtarefaObjetivoException.class)
                .satisfies(ex -> {
                    SubtarefaObjetivoException erro = (SubtarefaObjetivoException) ex;
                    assertThat(erro.getStatusCode().value()).isEqualTo(400);
                    assertThat(erro.getBody().getTitle())
                            .isEqualTo("Subtarefa não pode ser vinculada a objetivo");
                });
        verify(objetivoVinculoService, never()).substituirObjetivosTarefa(any(), any(), any());
    }

    @Test
    void patchDeSubtarefaComObjetivoIdsLanca400AntesDeSubstituir() {
        Tarefa filho = raiz();
        filho.definirParentId(PAI_ID);
        when(tarefaRepository.findByIdAndUsuarioId(TAREFA_ID, USUARIO)).thenReturn(Optional.of(filho));

        assertThatThrownBy(() -> service.atualizar(
                USUARIO, TAREFA_ID, patchObjetivos(List.of(OBJETIVO_A), true)))
                .isInstanceOf(SubtarefaObjetivoException.class)
                .satisfies(ex -> {
                    SubtarefaObjetivoException erro = (SubtarefaObjetivoException) ex;
                    assertThat(erro.getStatusCode().value()).isEqualTo(400);
                    assertThat(erro.getBody().getTitle())
                            .isEqualTo("Subtarefa não pode ser vinculada a objetivo");
                });
        verify(objetivoVinculoService, never()).substituirObjetivosTarefa(any(), any(), any());
        verify(objetivoVinculoService, never()).listarObjetivosTarefa(any());
    }

    @Test
    void listagemUsaMapearObjetivosPorTarefaEmLote() {
        Tarefa tarefa = raiz();
        when(tarefaRepository.findAll(any(Specification.class), eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(tarefa), PageRequest.of(0, 20), 1));
        when(objetivoVinculoService.mapearObjetivosPorTarefa(List.of(TAREFA_ID)))
                .thenReturn(Map.of(TAREFA_ID, List.of(resumo(OBJETIVO_A, "A"))));

        Page<TarefaResponse> page = service.listar(
                USUARIO,
                new TarefaFiltro(
                        null, null, null, null, null, null, null, null, null, null,
                        false, false, null, null, null, false, false, false, false, null),
                PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).objetivos()).extracting(ObjetivoResumoResponse::id)
                .containsExactly(OBJETIVO_A);
        verify(objetivoVinculoService).mapearObjetivosPorTarefa(List.of(TAREFA_ID));
    }

    @Test
    void criarComObjetivoDeOutroUsuarioNaoChamaSubstituirDepoisDoErro() {
        when(tarefaRepository.findMaxOrdemIrmaos(USUARIO, null)).thenReturn(0);
        when(objetivoVinculoService.substituirObjetivosTarefa(eq(USUARIO), any(), eq(List.of(OBJETIVO_A))))
                .thenThrow(new ObjetivoNaoEncontradoException());

        assertThatThrownBy(() -> service.criar(USUARIO, criarRaiz(List.of(OBJETIVO_A))))
                .isInstanceOf(ObjetivoNaoEncontradoException.class)
                .satisfies(ex -> {
                    ObjetivoNaoEncontradoException erro = (ObjetivoNaoEncontradoException) ex;
                    assertThat(erro.getStatusCode().value()).isEqualTo(404);
                    assertThat(erro.getBody().getTitle()).isEqualTo("Objetivo não encontrado");
                });
    }

    private static CriarTarefaRequest criarRaiz(List<UUID> objetivoIds) {
        return new CriarTarefaRequest(
                "Raiz", "", null, null, null, null, List.of(), objetivoIds, null);
    }

    private static CriarTarefaRequest criarFilho(UUID parentId, List<UUID> objetivoIds) {
        return new CriarTarefaRequest(
                "Filha", "", null, null, null, null, List.of(), objetivoIds, parentId);
    }

    private static AtualizarTarefaRequest patchObjetivos(List<UUID> objetivoIds, boolean presente) {
        return new AtualizarTarefaRequest(
                "Novo", null, null, null, null, null, null, false, null, false, objetivoIds, presente);
    }

    private static Tarefa raiz() {
        return raiz(TAREFA_ID, "Raiz");
    }

    private static Tarefa raiz(UUID id, String titulo) {
        return new Tarefa(
                id, USUARIO, titulo, "", Prioridade.MEDIA, null,
                StatusTarefa.PENDENTE, 30, AGORA, AGORA);
    }

    private static ObjetivoResumoResponse resumo(UUID id, String titulo) {
        return new ObjetivoResumoResponse(id, titulo, StatusObjetivo.ATIVO);
    }
}
