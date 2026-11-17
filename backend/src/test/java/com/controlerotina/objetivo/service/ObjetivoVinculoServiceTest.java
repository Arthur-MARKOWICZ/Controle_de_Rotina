package com.controlerotina.objetivo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.ErrorResponseException;

import com.controlerotina.habito.entity.Habito;
import com.controlerotina.habito.entity.StatusHabito;
import com.controlerotina.habito.repository.HabitoRepository;
import com.controlerotina.objetivo.dto.ObjetivoItemTarefaResponse;
import com.controlerotina.objetivo.dto.ObjetivoItensResponse;
import com.controlerotina.objetivo.dto.ObjetivoResumoResponse;
import com.controlerotina.objetivo.dto.OrigemVinculoObjetivo;
import com.controlerotina.objetivo.entity.Objetivo;
import com.controlerotina.objetivo.entity.ObjetivoHabito;
import com.controlerotina.objetivo.entity.ObjetivoProjeto;
import com.controlerotina.objetivo.entity.ObjetivoTarefa;
import com.controlerotina.objetivo.entity.StatusObjetivo;
import com.controlerotina.objetivo.repository.ObjetivoHabitoRepository;
import com.controlerotina.objetivo.repository.ObjetivoProjetoRepository;
import com.controlerotina.objetivo.repository.ObjetivoRepository;
import com.controlerotina.objetivo.repository.ObjetivoTarefaRepository;
import com.controlerotina.projeto.entity.Projeto;
import com.controlerotina.projeto.entity.StatusProjeto;
import com.controlerotina.projeto.repository.ProjetoRepository;
import com.controlerotina.shared.exception.ObjetivoNaoAtivoException;
import com.controlerotina.shared.exception.ObjetivoNaoEncontradoException;
import com.controlerotina.shared.exception.SubtarefaObjetivoException;
import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;
import com.controlerotina.tarefa.repository.TarefaRepository;

@ExtendWith(MockitoExtension.class)
class ObjetivoVinculoServiceTest {

    private static final Instant ANTES = Instant.parse("2026-11-01T12:00:00Z");
    private static final Instant DEPOIS = Instant.parse("2026-11-02T12:00:00Z");
    private static final UUID USUARIO = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID TAREFA_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID HABITO_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PROJETO_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OBJETIVO_A = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID OBJETIVO_B = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID OBJETIVO_OUTRO = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

    @Mock
    ObjetivoRepository objetivoRepository;
    @Mock
    ObjetivoTarefaRepository objetivoTarefaRepository;
    @Mock
    ObjetivoHabitoRepository objetivoHabitoRepository;
    @Mock
    ObjetivoProjetoRepository objetivoProjetoRepository;
    @Mock
    TarefaRepository tarefaRepository;
    @Mock
    HabitoRepository habitoRepository;
    @Mock
    ProjetoRepository projetoRepository;

    private ObjetivoVinculoService service;

    @BeforeEach
    void setUp() {
        service = new ObjetivoVinculoService(
                objetivoRepository,
                objetivoTarefaRepository,
                objetivoHabitoRepository,
                objetivoProjetoRepository,
                tarefaRepository,
                habitoRepository,
                projetoRepository
        );
    }

    @Test
    void deveVincularDoisObjetivosAtivosNaTarefaNaOrdemInformada() {
        Objetivo primeiro = objetivo(OBJETIVO_A, "A", StatusObjetivo.ATIVO);
        Objetivo segundo = objetivo(OBJETIVO_B, "B", StatusObjetivo.ATIVO);
        stubObjetivosDoUsuario(segundo, primeiro);

        List<ObjetivoResumoResponse> resultado = service.substituirObjetivosTarefa(
                USUARIO, TAREFA_ID, List.of(OBJETIVO_A, OBJETIVO_B));

        assertThat(resultado).extracting(ObjetivoResumoResponse::id)
                .containsExactly(OBJETIVO_A, OBJETIVO_B);
        assertThat(resultado).extracting(ObjetivoResumoResponse::titulo)
                .containsExactly("A", "B");
        List<ObjetivoTarefa> salvos = vinculosTarefaSalvos();
        assertThat(salvos).extracting(ObjetivoTarefa::getObjetivoId)
                .containsExactly(OBJETIVO_A, OBJETIVO_B);
        assertThat(salvos).extracting(ObjetivoTarefa::getTarefaId)
                .containsOnly(TAREFA_ID);
        verify(objetivoTarefaRepository).deleteAllByTarefaId(TAREFA_ID);
    }

    @Test
    void devePersistirUmaLinhaQuandoIdsRepetidos() {
        Objetivo unico = objetivo(OBJETIVO_A, "A", StatusObjetivo.ATIVO);
        stubObjetivosDoUsuario(unico);

        List<ObjetivoResumoResponse> resultado = service.substituirObjetivosTarefa(
                USUARIO, TAREFA_ID, Arrays.asList(OBJETIVO_A, OBJETIVO_A, null));

        assertThat(resultado).extracting(ObjetivoResumoResponse::id).containsExactly(OBJETIVO_A);
        assertThat(vinculosTarefaSalvos()).hasSize(1);
    }

    @Test
    void deveSubstituirPeloMesmoConjuntoSemErro() {
        Objetivo primeiro = objetivo(OBJETIVO_A, "A", StatusObjetivo.ATIVO);
        Objetivo segundo = objetivo(OBJETIVO_B, "B", StatusObjetivo.ATIVO);
        stubObjetivosDoUsuario(primeiro, segundo);
        when(objetivoTarefaRepository.findByTarefaId(TAREFA_ID)).thenReturn(List.of(
                new ObjetivoTarefa(OBJETIVO_A, TAREFA_ID),
                new ObjetivoTarefa(OBJETIVO_B, TAREFA_ID)
        ));

        List<ObjetivoResumoResponse> resultado = service.substituirObjetivosTarefa(
                USUARIO, TAREFA_ID, List.of(OBJETIVO_A, OBJETIVO_B));

        assertThat(resultado).extracting(ObjetivoResumoResponse::id)
                .containsExactly(OBJETIVO_A, OBJETIVO_B);
        verify(objetivoTarefaRepository).deleteAllByTarefaId(TAREFA_ID);
        assertThat(vinculosTarefaSalvos()).hasSize(2);
    }

    @Test
    void deveAceitarReenvioDeObjetivoJaLigadoDepoisDeConcluido() {
        Objetivo concluido = objetivo(OBJETIVO_A, "A", StatusObjetivo.CONCLUIDO);
        stubObjetivosDoUsuario(concluido);
        when(objetivoTarefaRepository.findByTarefaId(TAREFA_ID))
                .thenReturn(List.of(new ObjetivoTarefa(OBJETIVO_A, TAREFA_ID)));

        List<ObjetivoResumoResponse> resultado = service.substituirObjetivosTarefa(
                USUARIO, TAREFA_ID, List.of(OBJETIVO_A));

        assertThat(resultado).extracting(ObjetivoResumoResponse::status)
                .containsExactly(StatusObjetivo.CONCLUIDO);
        verify(objetivoTarefaRepository).deleteAllByTarefaId(TAREFA_ID);
        assertThat(vinculosTarefaSalvos()).hasSize(1);
    }

    @Test
    void deveLimparVinculosDaTarefaQuandoListaVazia() {
        List<ObjetivoResumoResponse> resultado = service.substituirObjetivosTarefa(
                USUARIO, TAREFA_ID, List.of());

        assertThat(resultado).isEmpty();
        verify(objetivoTarefaRepository).deleteAllByTarefaId(TAREFA_ID);
        verify(objetivoTarefaRepository, never()).saveAll(any());
        verify(objetivoRepository, never()).findByUsuarioIdAndIdIn(any(), any());
    }

    @Test
    void deveRetornar404QuandoObjetivoDeOutroUsuarioSemAlterarEstado() {
        when(objetivoRepository.findByUsuarioIdAndIdIn(eq(USUARIO), any())).thenReturn(List.of());

        assertErro(
                () -> service.substituirObjetivosTarefa(USUARIO, TAREFA_ID, List.of(OBJETIVO_OUTRO)),
                ObjetivoNaoEncontradoException.class,
                404,
                "Objetivo não encontrado"
        );
        verify(objetivoTarefaRepository, never()).deleteAllByTarefaId(any());
        verify(objetivoTarefaRepository, never()).saveAll(any());
    }

    @Test
    void deveRetornar409QuandoNovoObjetivoConcluidoSemAlterarEstado() {
        Objetivo concluido = objetivo(OBJETIVO_A, "A", StatusObjetivo.CONCLUIDO);
        stubObjetivosDoUsuario(concluido);

        assertErro(
                () -> service.substituirObjetivosTarefa(USUARIO, TAREFA_ID, List.of(OBJETIVO_A)),
                ObjetivoNaoAtivoException.class,
                409,
                "Objetivo não está ativo"
        );
        verify(objetivoTarefaRepository, never()).deleteAllByTarefaId(any());
        verify(objetivoTarefaRepository, never()).saveAll(any());
    }

    @Test
    void deveRetornar409QuandoNovoObjetivoArquivadoSemAlterarEstado() {
        Objetivo arquivado = objetivo(OBJETIVO_A, "A", StatusObjetivo.ARQUIVADO);
        stubObjetivosDoUsuario(arquivado);

        assertErro(
                () -> service.substituirObjetivosTarefa(USUARIO, TAREFA_ID, List.of(OBJETIVO_A)),
                ObjetivoNaoAtivoException.class,
                409,
                "Objetivo não está ativo"
        );
        verify(objetivoTarefaRepository, never()).deleteAllByTarefaId(any());
        verify(objetivoTarefaRepository, never()).saveAll(any());
    }

    @Test
    void deveVincularDoisObjetivosAtivosNoHabito() {
        Objetivo primeiro = objetivo(OBJETIVO_A, "A", StatusObjetivo.ATIVO);
        Objetivo segundo = objetivo(OBJETIVO_B, "B", StatusObjetivo.ATIVO);
        stubObjetivosDoUsuario(primeiro, segundo);

        List<ObjetivoResumoResponse> resultado = service.substituirObjetivosHabito(
                USUARIO, HABITO_ID, List.of(OBJETIVO_A, OBJETIVO_B));

        assertThat(resultado).extracting(ObjetivoResumoResponse::id)
                .containsExactly(OBJETIVO_A, OBJETIVO_B);
        verify(objetivoHabitoRepository).deleteAllByHabitoId(HABITO_ID);
        assertThat(vinculosHabitoSalvos()).extracting(ObjetivoHabito::getObjetivoId)
                .containsExactly(OBJETIVO_A, OBJETIVO_B);
    }

    @Test
    void deveVincularDoisObjetivosAtivosNoProjeto() {
        Objetivo primeiro = objetivo(OBJETIVO_A, "A", StatusObjetivo.ATIVO);
        Objetivo segundo = objetivo(OBJETIVO_B, "B", StatusObjetivo.ATIVO);
        stubObjetivosDoUsuario(primeiro, segundo);

        List<ObjetivoResumoResponse> resultado = service.substituirObjetivosProjeto(
                USUARIO, PROJETO_ID, List.of(OBJETIVO_A, OBJETIVO_B));

        assertThat(resultado).extracting(ObjetivoResumoResponse::id)
                .containsExactly(OBJETIVO_A, OBJETIVO_B);
        verify(objetivoProjetoRepository).deleteAllByProjetoId(PROJETO_ID);
        assertThat(vinculosProjetoSalvos()).extracting(ObjetivoProjeto::getObjetivoId)
                .containsExactly(OBJETIVO_A, OBJETIVO_B);
    }

    @Test
    void deveLimparVinculosDoHabitoEDoProjetoQuandoListaVazia() {
        assertThat(service.substituirObjetivosHabito(USUARIO, HABITO_ID, List.of())).isEmpty();
        assertThat(service.substituirObjetivosProjeto(USUARIO, PROJETO_ID, null)).isEmpty();
        verify(objetivoHabitoRepository).deleteAllByHabitoId(HABITO_ID);
        verify(objetivoProjetoRepository).deleteAllByProjetoId(PROJETO_ID);
        verify(objetivoHabitoRepository, never()).saveAll(any());
        verify(objetivoProjetoRepository, never()).saveAll(any());
    }

    @Test
    void deveDevolverMapaVazioQuandoIdsDeTarefaVazios() {
        assertThat(service.mapearObjetivosPorTarefa(List.of())).isEmpty();
        assertThat(service.mapearObjetivosPorHabito(List.of())).isEmpty();
        assertThat(service.mapearObjetivosPorProjeto(List.of())).isEmpty();
        verify(objetivoTarefaRepository, never()).findByTarefaIdIn(any());
    }

    @Test
    void deveMapearObjetivosPorTarefaHabitoEProjetoEmLote() {
        when(objetivoTarefaRepository.findByTarefaIdIn(List.of(TAREFA_ID)))
                .thenReturn(List.of(new ObjetivoTarefa(OBJETIVO_A, TAREFA_ID)));
        when(objetivoHabitoRepository.findByHabitoIdIn(List.of(HABITO_ID)))
                .thenReturn(List.of(new ObjetivoHabito(OBJETIVO_A, HABITO_ID)));
        when(objetivoProjetoRepository.findByProjetoIdIn(List.of(PROJETO_ID)))
                .thenReturn(List.of(new ObjetivoProjeto(OBJETIVO_A, PROJETO_ID)));
        when(objetivoRepository.findAllById(any())).thenReturn(List.of(
                objetivo(OBJETIVO_A, "A", StatusObjetivo.ATIVO)));

        assertThat(service.mapearObjetivosPorTarefa(List.of(TAREFA_ID)).get(TAREFA_ID))
                .extracting(ObjetivoResumoResponse::id).containsExactly(OBJETIVO_A);
        assertThat(service.mapearObjetivosPorHabito(List.of(HABITO_ID)).get(HABITO_ID))
                .extracting(ObjetivoResumoResponse::id).containsExactly(OBJETIVO_A);
        assertThat(service.mapearObjetivosPorProjeto(List.of(PROJETO_ID)).get(PROJETO_ID))
                .extracting(ObjetivoResumoResponse::id).containsExactly(OBJETIVO_A);
    }

    @Test
    void deveListarObjetivosDaEntidadeEListaVazia() {
        when(objetivoTarefaRepository.findByTarefaId(TAREFA_ID))
                .thenReturn(List.of(new ObjetivoTarefa(OBJETIVO_A, TAREFA_ID)));
        when(objetivoHabitoRepository.findByHabitoId(HABITO_ID))
                .thenReturn(List.of(new ObjetivoHabito(OBJETIVO_A, HABITO_ID)));
        when(objetivoProjetoRepository.findByProjetoId(PROJETO_ID))
                .thenReturn(List.of(new ObjetivoProjeto(OBJETIVO_A, PROJETO_ID)));
        when(objetivoRepository.findAllById(any())).thenReturn(List.of(
                objetivo(OBJETIVO_A, "A", StatusObjetivo.ATIVO)));

        assertThat(service.listarObjetivosTarefa(TAREFA_ID))
                .extracting(ObjetivoResumoResponse::id).containsExactly(OBJETIVO_A);
        assertThat(service.listarObjetivosHabito(HABITO_ID))
                .extracting(ObjetivoResumoResponse::id).containsExactly(OBJETIVO_A);
        assertThat(service.listarObjetivosProjeto(PROJETO_ID))
                .extracting(ObjetivoResumoResponse::id).containsExactly(OBJETIVO_A);
        assertThat(service.listarObjetivosTarefa(UUID.randomUUID())).isEmpty();
    }

    @Test
    void deveListarItensDiretosDosTresTipos() {
        Objetivo objetivo = objetivo(OBJETIVO_A, "A", StatusObjetivo.ATIVO);
        when(objetivoRepository.findByIdAndUsuarioId(OBJETIVO_A, USUARIO)).thenReturn(Optional.of(objetivo));
        Tarefa tarefa = tarefa(TAREFA_ID, "Ler", ANTES);
        Habito habito = habito(HABITO_ID, "Correr", ANTES);
        Projeto projeto = projeto(PROJETO_ID, "Casa", StatusProjeto.ATIVO, ANTES);
        when(objetivoTarefaRepository.findByObjetivoId(OBJETIVO_A))
                .thenReturn(List.of(new ObjetivoTarefa(OBJETIVO_A, TAREFA_ID)));
        when(objetivoHabitoRepository.findByObjetivoId(OBJETIVO_A))
                .thenReturn(List.of(new ObjetivoHabito(OBJETIVO_A, HABITO_ID)));
        when(objetivoProjetoRepository.findByObjetivoId(OBJETIVO_A))
                .thenReturn(List.of(new ObjetivoProjeto(OBJETIVO_A, PROJETO_ID)));
        when(tarefaRepository.findByIdInAndDeletedAtIsNullAndParentIdIsNull(any()))
                .thenReturn(List.of(tarefa));
        when(habitoRepository.findByIdInAndDeletedAtIsNull(any())).thenReturn(List.of(habito));
        when(projetoRepository.findAllById(any())).thenReturn(List.of(projeto));

        ObjetivoItensResponse itens = service.listarItens(USUARIO, OBJETIVO_A);

        assertThat(itens.tarefas()).extracting(ObjetivoItemTarefaResponse::id).containsExactly(TAREFA_ID);
        assertThat(itens.tarefas()).extracting(ObjetivoItemTarefaResponse::origem)
                .containsExactly(OrigemVinculoObjetivo.DIRETO);
        assertThat(itens.habitos()).extracting(item -> item.id()).containsExactly(HABITO_ID);
        assertThat(itens.habitos()).extracting(item -> item.origem())
                .containsExactly(OrigemVinculoObjetivo.DIRETO);
        assertThat(itens.projetos()).extracting(item -> item.id()).containsExactly(PROJETO_ID);
        assertThat(itens.projetos()).extracting(item -> item.origem())
                .containsExactly(OrigemVinculoObjetivo.DIRETO);
    }

    @Test
    void deveListarTarefaRaizDoProjetoComoViaProjeto() {
        Objetivo objetivo = objetivo(OBJETIVO_A, "A", StatusObjetivo.ATIVO);
        when(objetivoRepository.findByIdAndUsuarioId(OBJETIVO_A, USUARIO)).thenReturn(Optional.of(objetivo));
        Projeto projeto = projeto(PROJETO_ID, "Casa", StatusProjeto.ATIVO, ANTES);
        Tarefa herdada = tarefa(TAREFA_ID, "Ler", DEPOIS);
        herdada.definirProjetoId(PROJETO_ID, DEPOIS);
        when(objetivoProjetoRepository.findByObjetivoId(OBJETIVO_A))
                .thenReturn(List.of(new ObjetivoProjeto(OBJETIVO_A, PROJETO_ID)));
        when(projetoRepository.findAllById(any())).thenReturn(List.of(projeto));
        when(tarefaRepository.findByProjetoIdInAndDeletedAtIsNullAndParentIdIsNull(any()))
                .thenReturn(List.of(herdada));

        ObjetivoItensResponse itens = service.listarItens(USUARIO, OBJETIVO_A);

        assertThat(itens.tarefas()).hasSize(1);
        assertThat(itens.tarefas().get(0).origem()).isEqualTo(OrigemVinculoObjetivo.VIA_PROJETO);
        assertThat(itens.tarefas().get(0).projetoId()).isEqualTo(PROJETO_ID);
        assertThat(itens.tarefas().get(0).projetoNome()).isEqualTo("Casa");
        assertThat(itens.projetos()).extracting(item -> item.id()).containsExactly(PROJETO_ID);
    }

    @Test
    void devePreferirDiretoQuandoTarefaChegaPelosDoisCaminhos() {
        Objetivo objetivo = objetivo(OBJETIVO_A, "A", StatusObjetivo.ATIVO);
        when(objetivoRepository.findByIdAndUsuarioId(OBJETIVO_A, USUARIO)).thenReturn(Optional.of(objetivo));
        Projeto projeto = projeto(PROJETO_ID, "Casa", StatusProjeto.ATIVO, ANTES);
        Tarefa tarefa = tarefa(TAREFA_ID, "Ler", ANTES);
        tarefa.definirProjetoId(PROJETO_ID, ANTES);
        when(objetivoTarefaRepository.findByObjetivoId(OBJETIVO_A))
                .thenReturn(List.of(new ObjetivoTarefa(OBJETIVO_A, TAREFA_ID)));
        when(objetivoProjetoRepository.findByObjetivoId(OBJETIVO_A))
                .thenReturn(List.of(new ObjetivoProjeto(OBJETIVO_A, PROJETO_ID)));
        when(tarefaRepository.findByIdInAndDeletedAtIsNullAndParentIdIsNull(any()))
                .thenReturn(List.of(tarefa));
        when(projetoRepository.findAllById(any())).thenReturn(List.of(projeto));
        when(tarefaRepository.findByProjetoIdInAndDeletedAtIsNullAndParentIdIsNull(any()))
                .thenReturn(List.of(tarefa));

        ObjetivoItensResponse itens = service.listarItens(USUARIO, OBJETIVO_A);

        assertThat(itens.tarefas()).hasSize(1);
        assertThat(itens.tarefas().get(0).origem()).isEqualTo(OrigemVinculoObjetivo.DIRETO);
        assertThat(itens.tarefas().get(0).id()).isEqualTo(TAREFA_ID);
    }

    @Test
    void naoDeveIncluirSubtarefaDoProjeto() {
        Objetivo objetivo = objetivo(OBJETIVO_A, "A", StatusObjetivo.ATIVO);
        when(objetivoRepository.findByIdAndUsuarioId(OBJETIVO_A, USUARIO)).thenReturn(Optional.of(objetivo));
        Projeto projeto = projeto(PROJETO_ID, "Casa", StatusProjeto.ATIVO, ANTES);
        Tarefa subtarefa = tarefa(TAREFA_ID, "Filho", ANTES);
        subtarefa.definirProjetoId(PROJETO_ID, ANTES);
        subtarefa.definirParentId(UUID.randomUUID());
        when(objetivoProjetoRepository.findByObjetivoId(OBJETIVO_A))
                .thenReturn(List.of(new ObjetivoProjeto(OBJETIVO_A, PROJETO_ID)));
        when(projetoRepository.findAllById(any())).thenReturn(List.of(projeto));
        when(tarefaRepository.findByProjetoIdInAndDeletedAtIsNullAndParentIdIsNull(any()))
                .thenReturn(List.of(subtarefa));

        ObjetivoItensResponse itens = service.listarItens(USUARIO, OBJETIVO_A);

        assertThat(itens.tarefas()).isEmpty();
        assertThat(itens.projetos()).extracting(item -> item.id()).containsExactly(PROJETO_ID);
    }

    @Test
    void naoDeveHerdarHabitoViaProjeto() {
        Objetivo objetivo = objetivo(OBJETIVO_A, "A", StatusObjetivo.ATIVO);
        when(objetivoRepository.findByIdAndUsuarioId(OBJETIVO_A, USUARIO)).thenReturn(Optional.of(objetivo));
        Projeto projeto = projeto(PROJETO_ID, "Casa", StatusProjeto.ATIVO, ANTES);
        when(objetivoProjetoRepository.findByObjetivoId(OBJETIVO_A))
                .thenReturn(List.of(new ObjetivoProjeto(OBJETIVO_A, PROJETO_ID)));
        when(projetoRepository.findAllById(any())).thenReturn(List.of(projeto));

        ObjetivoItensResponse itens = service.listarItens(USUARIO, OBJETIVO_A);

        assertThat(itens.habitos()).isEmpty();
        verify(habitoRepository, never()).findByIdInAndDeletedAtIsNull(any());
    }

    @Test
    void naoDeveListarTarefaSoftDeletedNemApagarJoin() {
        Objetivo objetivo = objetivo(OBJETIVO_A, "A", StatusObjetivo.ATIVO);
        when(objetivoRepository.findByIdAndUsuarioId(OBJETIVO_A, USUARIO)).thenReturn(Optional.of(objetivo));
        Tarefa deletada = tarefa(TAREFA_ID, "Ler", ANTES);
        deletada.softDelete(DEPOIS);
        when(objetivoTarefaRepository.findByObjetivoId(OBJETIVO_A))
                .thenReturn(List.of(new ObjetivoTarefa(OBJETIVO_A, TAREFA_ID)));
        when(tarefaRepository.findByIdInAndDeletedAtIsNullAndParentIdIsNull(any()))
                .thenReturn(List.of(deletada));

        ObjetivoItensResponse itens = service.listarItens(USUARIO, OBJETIVO_A);

        assertThat(itens.tarefas()).isEmpty();
        verify(objetivoTarefaRepository, never()).deleteAllByTarefaId(any());
        verify(objetivoTarefaRepository, never()).delete(any());
        verify(objetivoTarefaRepository, never()).deleteAll(any());
    }

    @Test
    void deveDeixarProjetoNomeNuloQuandoProjetoDaTarefaNaoExiste() {
        Objetivo objetivo = objetivo(OBJETIVO_A, "A", StatusObjetivo.ATIVO);
        when(objetivoRepository.findByIdAndUsuarioId(OBJETIVO_A, USUARIO)).thenReturn(Optional.of(objetivo));
        Tarefa tarefa = tarefa(TAREFA_ID, "Ler", ANTES);
        tarefa.definirProjetoId(PROJETO_ID, ANTES);
        when(objetivoTarefaRepository.findByObjetivoId(OBJETIVO_A))
                .thenReturn(List.of(new ObjetivoTarefa(OBJETIVO_A, TAREFA_ID)));
        when(tarefaRepository.findByIdInAndDeletedAtIsNullAndParentIdIsNull(any()))
                .thenReturn(List.of(tarefa));

        ObjetivoItensResponse itens = service.listarItens(USUARIO, OBJETIVO_A);

        assertThat(itens.tarefas()).hasSize(1);
        assertThat(itens.tarefas().get(0).projetoId()).isEqualTo(PROJETO_ID);
        assertThat(itens.tarefas().get(0).projetoNome()).isNull();
    }

    @Test
    void deveRetornar404AoListarItensDeObjetivoInexistente() {
        when(objetivoRepository.findByIdAndUsuarioId(OBJETIVO_A, USUARIO)).thenReturn(Optional.empty());

        assertErro(
                () -> service.listarItens(USUARIO, OBJETIVO_A),
                ObjetivoNaoEncontradoException.class,
                404,
                "Objetivo não encontrado"
        );
        verify(objetivoTarefaRepository, never()).findByObjetivoId(any());
    }

    @Test
    void deveDevolverListasVaziasQuandoItensNulos() {
        ObjetivoItensResponse itens = new ObjetivoItensResponse(null, null, null);

        assertThat(itens.tarefas()).isEmpty();
        assertThat(itens.habitos()).isEmpty();
        assertThat(itens.projetos()).isEmpty();
    }

    @Test
    void deveExpor400QuandoSubtarefaNaoPodeSerVinculada() {
        SubtarefaObjetivoException erro = new SubtarefaObjetivoException();

        assertThat(erro.getStatusCode().value()).isEqualTo(400);
        assertThat(erro.getBody().getTitle()).isEqualTo("Subtarefa não pode ser vinculada a objetivo");
    }

    private void stubObjetivosDoUsuario(Objetivo... objetivos) {
        when(objetivoRepository.findByUsuarioIdAndIdIn(eq(USUARIO), any())).thenAnswer(inv -> {
            Collection<UUID> ids = inv.getArgument(1);
            return Arrays.stream(objetivos)
                    .filter(objetivo -> ids.contains(objetivo.getId()))
                    .toList();
        });
    }

    @SuppressWarnings("unchecked")
    private List<ObjetivoTarefa> vinculosTarefaSalvos() {
        ArgumentCaptor<Iterable<ObjetivoTarefa>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(objetivoTarefaRepository).saveAll(captor.capture());
        List<ObjetivoTarefa> salvos = new ArrayList<>();
        captor.getValue().forEach(salvos::add);
        return salvos;
    }

    @SuppressWarnings("unchecked")
    private List<ObjetivoHabito> vinculosHabitoSalvos() {
        ArgumentCaptor<Iterable<ObjetivoHabito>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(objetivoHabitoRepository).saveAll(captor.capture());
        List<ObjetivoHabito> salvos = new ArrayList<>();
        captor.getValue().forEach(salvos::add);
        return salvos;
    }

    @SuppressWarnings("unchecked")
    private List<ObjetivoProjeto> vinculosProjetoSalvos() {
        ArgumentCaptor<Iterable<ObjetivoProjeto>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(objetivoProjetoRepository).saveAll(captor.capture());
        List<ObjetivoProjeto> salvos = new ArrayList<>();
        captor.getValue().forEach(salvos::add);
        return salvos;
    }

    private static Objetivo objetivo(UUID id, String titulo, StatusObjetivo status) {
        Objetivo objetivo = new Objetivo(id, USUARIO, titulo, null, null, ANTES);
        if (status == StatusObjetivo.CONCLUIDO) {
            objetivo.concluir(ANTES);
        } else if (status == StatusObjetivo.ARQUIVADO) {
            objetivo.arquivar(ANTES);
        }
        return objetivo;
    }

    private static Tarefa tarefa(UUID id, String titulo, Instant createdAt) {
        return new Tarefa(
                id,
                USUARIO,
                titulo,
                "desc",
                Prioridade.MEDIA,
                null,
                StatusTarefa.PENDENTE,
                30,
                createdAt,
                createdAt
        );
    }

    private static Habito habito(UUID id, String titulo, Instant createdAt) {
        return new Habito(
                id,
                USUARIO,
                titulo,
                null,
                30,
                Set.of(),
                null,
                StatusHabito.ATIVO,
                0,
                0,
                createdAt,
                createdAt
        );
    }

    private static Projeto projeto(UUID id, String nome, StatusProjeto status, Instant createdAt) {
        return new Projeto(id, USUARIO, nome, null, status, createdAt, createdAt);
    }

    private static void assertErro(
            ThrowingCallable chamada,
            Class<? extends ErrorResponseException> tipo,
            int status,
            String title
    ) {
        assertThatThrownBy(chamada)
                .isInstanceOf(tipo)
                .satisfies(ex -> {
                    ErrorResponseException erro = (ErrorResponseException) ex;
                    assertThat(erro.getStatusCode().value()).isEqualTo(status);
                    assertThat(erro.getBody().getTitle()).isEqualTo(title);
                });
    }
}
