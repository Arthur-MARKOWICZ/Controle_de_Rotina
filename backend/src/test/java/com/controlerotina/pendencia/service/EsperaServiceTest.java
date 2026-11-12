package com.controlerotina.pendencia.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.controlerotina.pendencia.dto.CriarEsperaRequest;
import com.controlerotina.pendencia.dto.EsperaResumo;
import com.controlerotina.pendencia.entity.Espera;
import com.controlerotina.pendencia.entity.TipoRecursoEspera;
import com.controlerotina.pendencia.repository.EsperaRepository;
import com.controlerotina.projeto.entity.Projeto;
import com.controlerotina.projeto.entity.StatusProjeto;
import com.controlerotina.projeto.repository.ProjetoRepository;
import com.controlerotina.shared.exception.AutoEsperaException;
import com.controlerotina.shared.exception.CicloEsperaException;
import com.controlerotina.shared.exception.EsperaDuplicadaException;
import com.controlerotina.shared.exception.EsperaNaoEncontradaException;
import com.controlerotina.shared.exception.TarefaNaoEncontradaException;
import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;
import com.controlerotina.tarefa.repository.TarefaRepository;

@ExtendWith(MockitoExtension.class)
class EsperaServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-12T15:00:00Z");
    private static final UUID USUARIO = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORIGEM_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ALVO_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID TERCEIRO_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Mock
    EsperaRepository esperaRepository;

    @Mock
    TarefaRepository tarefaRepository;

    @Mock
    ProjetoRepository projetoRepository;

    private EsperaService service;

    @BeforeEach
    void setUp() {
        service = new EsperaService(
                esperaRepository,
                tarefaRepository,
                projetoRepository,
                Clock.fixed(AGORA, ZoneOffset.UTC)
        );
    }

    @Test
    void deveCriarEsperaTarefaParaTarefa() {
        stubTarefa(ORIGEM_ID, "Origem");
        stubTarefa(ALVO_ID, "Estudar");
        when(esperaRepository.existsByUsuarioIdAndOrigemTipoAndOrigemIdAndAlvoTipoAndAlvoId(
                USUARIO, TipoRecursoEspera.TAREFA, ORIGEM_ID, TipoRecursoEspera.TAREFA, ALVO_ID))
                .thenReturn(false);
        when(esperaRepository.save(any(Espera.class))).thenAnswer(inv -> inv.getArgument(0));

        EsperaResumo resumo = service.criar(
                USUARIO,
                TipoRecursoEspera.TAREFA,
                ORIGEM_ID,
                new CriarEsperaRequest(TipoRecursoEspera.TAREFA, ALVO_ID)
        );

        assertThat(resumo.alvoTipo()).isEqualTo(TipoRecursoEspera.TAREFA);
        assertThat(resumo.alvoId()).isEqualTo(ALVO_ID);
        assertThat(resumo.alvoTitulo()).isEqualTo("Estudar");
        assertThat(resumo.resolvida()).isFalse();

        ArgumentCaptor<Espera> captor = ArgumentCaptor.forClass(Espera.class);
        verify(esperaRepository).save(captor.capture());
        Espera salva = captor.getValue();
        assertThat(salva.getUsuarioId()).isEqualTo(USUARIO);
        assertThat(salva.getOrigemTipo()).isEqualTo(TipoRecursoEspera.TAREFA);
        assertThat(salva.getOrigemId()).isEqualTo(ORIGEM_ID);
        assertThat(salva.getCreatedAt()).isEqualTo(AGORA);
    }

    @Test
    void deveCriarEsperaTarefaParaProjeto() {
        stubTarefa(ORIGEM_ID, "Origem");
        stubProjeto(ALVO_ID, "TCC");
        when(esperaRepository.existsByUsuarioIdAndOrigemTipoAndOrigemIdAndAlvoTipoAndAlvoId(
                USUARIO, TipoRecursoEspera.TAREFA, ORIGEM_ID, TipoRecursoEspera.PROJETO, ALVO_ID))
                .thenReturn(false);
        when(esperaRepository.save(any(Espera.class))).thenAnswer(inv -> inv.getArgument(0));

        EsperaResumo resumo = service.criar(
                USUARIO,
                TipoRecursoEspera.TAREFA,
                ORIGEM_ID,
                new CriarEsperaRequest(TipoRecursoEspera.PROJETO, ALVO_ID)
        );

        assertThat(resumo.alvoTipo()).isEqualTo(TipoRecursoEspera.PROJETO);
        assertThat(resumo.alvoTitulo()).isEqualTo("TCC");
    }

    @Test
    void deveCriarEsperaProjetoParaTarefa() {
        stubProjeto(ORIGEM_ID, "Origem");
        stubTarefa(ALVO_ID, "Estudar");
        when(esperaRepository.existsByUsuarioIdAndOrigemTipoAndOrigemIdAndAlvoTipoAndAlvoId(
                USUARIO, TipoRecursoEspera.PROJETO, ORIGEM_ID, TipoRecursoEspera.TAREFA, ALVO_ID))
                .thenReturn(false);
        when(esperaRepository.save(any(Espera.class))).thenAnswer(inv -> inv.getArgument(0));

        EsperaResumo resumo = service.criar(
                USUARIO,
                TipoRecursoEspera.PROJETO,
                ORIGEM_ID,
                new CriarEsperaRequest(TipoRecursoEspera.TAREFA, ALVO_ID)
        );

        assertThat(resumo.alvoTipo()).isEqualTo(TipoRecursoEspera.TAREFA);
        assertThat(resumo.alvoTitulo()).isEqualTo("Estudar");
    }

    @Test
    void deveCriarEsperaProjetoParaProjeto() {
        stubProjeto(ORIGEM_ID, "Origem");
        stubProjeto(ALVO_ID, "Alvo");
        when(esperaRepository.existsByUsuarioIdAndOrigemTipoAndOrigemIdAndAlvoTipoAndAlvoId(
                USUARIO, TipoRecursoEspera.PROJETO, ORIGEM_ID, TipoRecursoEspera.PROJETO, ALVO_ID))
                .thenReturn(false);
        when(esperaRepository.save(any(Espera.class))).thenAnswer(inv -> inv.getArgument(0));

        EsperaResumo resumo = service.criar(
                USUARIO,
                TipoRecursoEspera.PROJETO,
                ORIGEM_ID,
                new CriarEsperaRequest(TipoRecursoEspera.PROJETO, ALVO_ID)
        );

        assertThat(resumo.alvoTipo()).isEqualTo(TipoRecursoEspera.PROJETO);
        assertThat(resumo.alvoTitulo()).isEqualTo("Alvo");
    }

    @Test
    void deveRecusarAlvoDeOutroUsuario() {
        stubTarefa(ORIGEM_ID, "Origem");
        when(tarefaRepository.findByIdAndUsuarioId(ALVO_ID, USUARIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criar(
                USUARIO,
                TipoRecursoEspera.TAREFA,
                ORIGEM_ID,
                new CriarEsperaRequest(TipoRecursoEspera.TAREFA, ALVO_ID)
        )).isInstanceOf(TarefaNaoEncontradaException.class);
    }

    @Test
    void deveRecusarAutoEspera() {
        stubTarefa(ORIGEM_ID, "Origem");

        assertThatThrownBy(() -> service.criar(
                USUARIO,
                TipoRecursoEspera.TAREFA,
                ORIGEM_ID,
                new CriarEsperaRequest(TipoRecursoEspera.TAREFA, ORIGEM_ID)
        )).isInstanceOf(AutoEsperaException.class);
    }

    @Test
    void deveRecusarDuplicata() {
        stubTarefa(ORIGEM_ID, "Origem");
        stubTarefa(ALVO_ID, "Estudar");
        when(esperaRepository.existsByUsuarioIdAndOrigemTipoAndOrigemIdAndAlvoTipoAndAlvoId(
                USUARIO, TipoRecursoEspera.TAREFA, ORIGEM_ID, TipoRecursoEspera.TAREFA, ALVO_ID))
                .thenReturn(true);

        assertThatThrownBy(() -> service.criar(
                USUARIO,
                TipoRecursoEspera.TAREFA,
                ORIGEM_ID,
                new CriarEsperaRequest(TipoRecursoEspera.TAREFA, ALVO_ID)
        )).isInstanceOf(EsperaDuplicadaException.class);
    }

    @Test
    void deveRecusarCicloDireto() {
        stubTarefa(ALVO_ID, "B");
        stubTarefa(ORIGEM_ID, "A");
        when(esperaRepository.existsByUsuarioIdAndOrigemTipoAndOrigemIdAndAlvoTipoAndAlvoId(
                USUARIO, TipoRecursoEspera.TAREFA, ALVO_ID, TipoRecursoEspera.TAREFA, ORIGEM_ID))
                .thenReturn(false);
        when(esperaRepository.findByUsuarioId(USUARIO)).thenReturn(List.of(esperaTarefaParaTarefa()));

        assertThatThrownBy(() -> service.criar(
                USUARIO,
                TipoRecursoEspera.TAREFA,
                ALVO_ID,
                new CriarEsperaRequest(TipoRecursoEspera.TAREFA, ORIGEM_ID)
        )).isInstanceOf(CicloEsperaException.class);
    }

    @Test
    void deveRecusarCicloTransitivo() {
        stubTarefa(TERCEIRO_ID, "C");
        stubTarefa(ORIGEM_ID, "A");
        when(esperaRepository.existsByUsuarioIdAndOrigemTipoAndOrigemIdAndAlvoTipoAndAlvoId(
                USUARIO, TipoRecursoEspera.TAREFA, TERCEIRO_ID, TipoRecursoEspera.TAREFA, ORIGEM_ID))
                .thenReturn(false);
        when(esperaRepository.findByUsuarioId(USUARIO)).thenReturn(List.of(
                esperaTarefaParaTarefa(),
                new Espera(
                        UUID.randomUUID(),
                        USUARIO,
                        TipoRecursoEspera.TAREFA,
                        ALVO_ID,
                        TipoRecursoEspera.TAREFA,
                        TERCEIRO_ID,
                        AGORA
                )
        ));

        assertThatThrownBy(() -> service.criar(
                USUARIO,
                TipoRecursoEspera.TAREFA,
                TERCEIRO_ID,
                new CriarEsperaRequest(TipoRecursoEspera.TAREFA, ORIGEM_ID)
        )).isInstanceOf(CicloEsperaException.class);
    }

    @Test
    void deveApagarEsperasDoRecursoComoOrigemEAlvo() {
        service.apagarPorRecurso(USUARIO, TipoRecursoEspera.TAREFA, ORIGEM_ID);

        verify(esperaRepository).deleteByUsuarioIdAndOrigemTipoAndOrigemId(
                USUARIO, TipoRecursoEspera.TAREFA, ORIGEM_ID);
        verify(esperaRepository).deleteByUsuarioIdAndAlvoTipoAndAlvoId(
                USUARIO, TipoRecursoEspera.TAREFA, ORIGEM_ID);
    }

    @Test
    void deveListarEsperasDaOrigemComTituloDoAlvo() {
        stubTarefa(ORIGEM_ID, "Origem");
        stubListagem(esperaTarefaParaTarefa());
        stubAlvoTarefa(ALVO_ID, "Estudar", StatusTarefa.PENDENTE);

        List<EsperaResumo> lista = service.listar(USUARIO, TipoRecursoEspera.TAREFA, ORIGEM_ID);

        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).alvoTitulo()).isEqualTo("Estudar");
        assertThat(lista.get(0).resolvida()).isFalse();
    }

    @Test
    void deveMarcarEsperaResolvidaQuandoAlvoTarefaEstaFinalizada() {
        stubTarefa(ORIGEM_ID, "Origem");
        stubListagem(esperaTarefaParaTarefa());
        stubAlvoTarefa(ALVO_ID, "Estudar", StatusTarefa.FINALIZADA);

        List<EsperaResumo> lista = service.listar(USUARIO, TipoRecursoEspera.TAREFA, ORIGEM_ID);

        assertThat(lista.get(0).resolvida()).isTrue();
    }

    @Test
    void deveManterEsperaIrresolvidaQuandoAlvoTarefaEstaCancelada() {
        stubTarefa(ORIGEM_ID, "Origem");
        stubListagem(esperaTarefaParaTarefa());
        stubAlvoTarefa(ALVO_ID, "Estudar", StatusTarefa.CANCELADA);

        List<EsperaResumo> lista = service.listar(USUARIO, TipoRecursoEspera.TAREFA, ORIGEM_ID);

        assertThat(lista.get(0).resolvida()).isFalse();
    }

    @Test
    void deveManterEsperaIrresolvidaQuandoProjetoAlvoEstaVazio() {
        stubTarefa(ORIGEM_ID, "Origem");
        stubListagem(esperaTarefaParaProjeto());
        stubAlvoProjeto(ALVO_ID, "TCC", List.of());

        List<EsperaResumo> lista = service.listar(USUARIO, TipoRecursoEspera.TAREFA, ORIGEM_ID);

        assertThat(lista.get(0).resolvida()).isFalse();
    }

    @Test
    void deveMarcarEsperaResolvidaQuandoTodasTarefasDoProjetoAlvoEstaoFinalizadas() {
        stubTarefa(ORIGEM_ID, "Origem");
        stubListagem(esperaTarefaParaProjeto());
        stubAlvoProjeto(ALVO_ID, "TCC", List.of(
                tarefaNoProjeto(UUID.randomUUID(), "Passo 1", StatusTarefa.FINALIZADA),
                tarefaNoProjeto(UUID.randomUUID(), "Passo 2", StatusTarefa.FINALIZADA)
        ));

        List<EsperaResumo> lista = service.listar(USUARIO, TipoRecursoEspera.TAREFA, ORIGEM_ID);

        assertThat(lista.get(0).resolvida()).isTrue();
    }

    @Test
    void deveManterEsperaIrresolvidaQuandoProjetoAlvoTemTarefaAtrasada() {
        stubTarefa(ORIGEM_ID, "Origem");
        stubListagem(esperaTarefaParaProjeto());
        stubAlvoProjeto(ALVO_ID, "TCC", List.of(
                tarefaNoProjeto(UUID.randomUUID(), "Feita", StatusTarefa.FINALIZADA),
                tarefaNoProjeto(UUID.randomUUID(), "Atrasada", StatusTarefa.ATRASADA)
        ));

        List<EsperaResumo> lista = service.listar(USUARIO, TipoRecursoEspera.TAREFA, ORIGEM_ID);

        assertThat(lista.get(0).resolvida()).isFalse();
    }

    @Test
    void deveExcluirEsperaDaOrigem() {
        stubTarefa(ORIGEM_ID, "Origem");
        UUID esperaId = UUID.randomUUID();
        Espera espera = new Espera(
                esperaId,
                USUARIO,
                TipoRecursoEspera.TAREFA,
                ORIGEM_ID,
                TipoRecursoEspera.TAREFA,
                ALVO_ID,
                AGORA
        );
        when(esperaRepository.findByIdAndUsuarioIdAndOrigemTipoAndOrigemId(
                esperaId, USUARIO, TipoRecursoEspera.TAREFA, ORIGEM_ID))
                .thenReturn(Optional.of(espera));

        service.excluir(USUARIO, TipoRecursoEspera.TAREFA, ORIGEM_ID, esperaId);

        verify(esperaRepository).delete(espera);
    }

    @Test
    void deveRecusarExclusaoDeEsperaInexistente() {
        stubTarefa(ORIGEM_ID, "Origem");
        UUID esperaId = UUID.randomUUID();
        when(esperaRepository.findByIdAndUsuarioIdAndOrigemTipoAndOrigemId(
                esperaId, USUARIO, TipoRecursoEspera.TAREFA, ORIGEM_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.excluir(USUARIO, TipoRecursoEspera.TAREFA, ORIGEM_ID, esperaId))
                .isInstanceOf(EsperaNaoEncontradaException.class);
    }

    private void stubTarefa(UUID id, String titulo) {
        stubTarefa(id, titulo, StatusTarefa.PENDENTE);
    }

    private void stubTarefa(UUID id, String titulo, StatusTarefa status) {
        when(tarefaRepository.findByIdAndUsuarioId(id, USUARIO)).thenReturn(Optional.of(tarefa(id, titulo, status)));
    }

    private void stubProjeto(UUID id, String nome) {
        when(projetoRepository.findByIdAndUsuarioId(id, USUARIO)).thenReturn(Optional.of(projeto(id, nome)));
    }

    private void stubAlvoTarefa(UUID id, String titulo, StatusTarefa status) {
        when(tarefaRepository.findAllById(any())).thenReturn(List.of(tarefa(id, titulo, status)));
    }

    private void stubAlvoProjeto(UUID id, String nome, List<Tarefa> tarefasDoProjeto) {
        when(projetoRepository.findAllById(any())).thenReturn(List.of(projeto(id, nome)));
        when(tarefaRepository.findByProjetoIdInAndDeletedAtIsNull(any())).thenReturn(tarefasDoProjeto);
    }

    private void stubListagem(Espera espera) {
        when(esperaRepository.findByUsuarioIdAndOrigemTipoAndOrigemIdOrderByCreatedAtAsc(
                USUARIO, espera.getOrigemTipo(), espera.getOrigemId()))
                .thenReturn(List.of(espera));
    }

    private static Espera esperaTarefaParaTarefa() {
        return new Espera(
                UUID.randomUUID(),
                USUARIO,
                TipoRecursoEspera.TAREFA,
                ORIGEM_ID,
                TipoRecursoEspera.TAREFA,
                ALVO_ID,
                AGORA
        );
    }

    private static Espera esperaTarefaParaProjeto() {
        return new Espera(
                UUID.randomUUID(),
                USUARIO,
                TipoRecursoEspera.TAREFA,
                ORIGEM_ID,
                TipoRecursoEspera.PROJETO,
                ALVO_ID,
                AGORA
        );
    }

    private static Tarefa tarefa(UUID id, String titulo) {
        return tarefa(id, titulo, StatusTarefa.PENDENTE);
    }

    private static Tarefa tarefa(UUID id, String titulo, StatusTarefa status) {
        return new Tarefa(
                id,
                USUARIO,
                titulo,
                "desc",
                Prioridade.MEDIA,
                AGORA,
                status,
                30,
                AGORA,
                AGORA
        );
    }

    private static Tarefa tarefaNoProjeto(UUID id, String titulo, StatusTarefa status) {
        Tarefa tarefa = tarefa(id, titulo, status);
        tarefa.definirProjetoId(ALVO_ID, AGORA);
        return tarefa;
    }

    private static Projeto projeto(UUID id, String nome) {
        return new Projeto(id, USUARIO, nome, null, StatusProjeto.ATIVO, AGORA, AGORA);
    }
}
