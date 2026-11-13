package com.controlerotina.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.controlerotina.habito.repository.HabitoRepository;
import com.controlerotina.planner.entity.BlocoPlano;
import com.controlerotina.planner.entity.Plano;
import com.controlerotina.planner.entity.StatusPlano;
import com.controlerotina.planner.repository.BlocoPlanoRepository;
import com.controlerotina.planner.repository.PlanoRepository;
import com.controlerotina.projeto.repository.ProjetoRepository;
import com.controlerotina.shared.exception.PlanoNaoEncontradoException;
import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;
import com.controlerotina.tarefa.repository.TarefaRepository;

@ExtendWith(MockitoExtension.class)
class PlanoConsultaServiceTest {

    private static final Instant AGORA = Instant.parse("2026-10-20T12:00:00Z");
    private static final UUID USUARIO = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLANO_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final LocalDate DIA = LocalDate.of(2026, 10, 20);

    @Mock
    PlanoRepository planoRepository;
    @Mock
    BlocoPlanoRepository blocoPlanoRepository;
    @Mock
    TarefaRepository tarefaRepository;
    @Mock
    HabitoRepository habitoRepository;
    @Mock
    ProjetoRepository projetoRepository;

    private PlanoConsultaService service;

    @BeforeEach
    void setUp() {
        service = new PlanoConsultaService(
                planoRepository, blocoPlanoRepository, tarefaRepository, habitoRepository, projetoRepository);
    }

    @Test
    void deveBuscarPlanoCorrentePorData() {
        Plano plano = rascunho();
        when(planoRepository.findFirstByUsuarioIdAndDataAndStatusInOrderByVersaoDesc(any(), any(), any()))
                .thenReturn(Optional.of(plano));
        when(blocoPlanoRepository.findByPlanoIdOrderByOrdemAsc(PLANO_ID)).thenReturn(List.of());

        var encontrado = service.buscarPorData(USUARIO, DIA, null);

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().id()).isEqualTo(PLANO_ID);
        assertThat(encontrado.get().blocos()).isEmpty();
    }

    @Test
    void deveRetornarVazioQuandoDiaNaoTemPlano() {
        when(planoRepository.findFirstByUsuarioIdAndDataAndStatusInOrderByVersaoDesc(any(), any(), any()))
                .thenReturn(Optional.empty());

        assertThat(service.buscarPorData(USUARIO, DIA, null)).isEmpty();
        verify(blocoPlanoRepository, never()).findByPlanoIdOrderByOrdemAsc(any());
    }

    @Test
    void deveRetornarVazioParaPlanoDeOutroUsuario() {
        when(planoRepository.findByIdAndUsuarioId(PLANO_ID, USUARIO)).thenReturn(Optional.empty());

        assertThat(service.buscarPorId(USUARIO, PLANO_ID)).isEmpty();
    }

    @Test
    void deveIgnorarPlanoDescartado() {
        Plano descartado = new Plano(
                PLANO_ID, USUARIO, DIA, 1, StatusPlano.DESCARTADO,
                false, null, null, AGORA, AGORA, AGORA);
        when(planoRepository.findByIdAndUsuarioId(PLANO_ID, USUARIO)).thenReturn(Optional.of(descartado));

        assertThat(service.buscarPorId(USUARIO, PLANO_ID)).isEmpty();
    }

    @Test
    void deveBuscarVersaoEspecifica() {
        Plano plano = rascunho();
        when(planoRepository.findByUsuarioIdAndDataAndVersao(USUARIO, DIA, 1)).thenReturn(Optional.of(plano));
        when(blocoPlanoRepository.findByPlanoIdOrderByOrdemAsc(PLANO_ID)).thenReturn(List.of());

        assertThat(service.buscarPorData(USUARIO, DIA, 1)).isPresent();
    }

    @Test
    void deveFalharQuandoBlocoApontaTarefaInexistente() {
        Plano plano = rascunho();
        UUID tarefaId = UUID.randomUUID();
        when(planoRepository.findByIdAndUsuarioId(PLANO_ID, USUARIO)).thenReturn(Optional.of(plano));
        when(blocoPlanoRepository.findByPlanoIdOrderByOrdemAsc(PLANO_ID)).thenReturn(List.of(
                new BlocoPlano(UUID.randomUUID(), PLANO_ID, tarefaId, 30, 1, AGORA, AGORA)));
        when(tarefaRepository.findAllById(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.buscarPorId(USUARIO, PLANO_ID))
                .isInstanceOf(PlanoNaoEncontradoException.class);
    }

    @Test
    void deveMontarBlocosComTarefa() {
        Plano plano = rascunho();
        UUID tarefaId = UUID.randomUUID();
        Tarefa tarefa = new Tarefa(
                tarefaId, USUARIO, "Estudar", "", Prioridade.MEDIA,
                null, StatusTarefa.PENDENTE, 30, AGORA, AGORA);
        when(planoRepository.findByIdAndUsuarioId(PLANO_ID, USUARIO)).thenReturn(Optional.of(plano));
        when(blocoPlanoRepository.findByPlanoIdOrderByOrdemAsc(PLANO_ID)).thenReturn(List.of(
                new BlocoPlano(UUID.randomUUID(), PLANO_ID, tarefaId, 30, 1, AGORA, AGORA)));
        when(tarefaRepository.findAllById(any())).thenReturn(List.of(tarefa));

        var encontrado = service.buscarPorId(USUARIO, PLANO_ID);

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().blocos()).hasSize(1);
        assertThat(encontrado.get().blocos().get(0).tarefa().titulo()).isEqualTo("Estudar");
    }

    private static Plano rascunho() {
        return new Plano(
                PLANO_ID, USUARIO, DIA, 1, StatusPlano.RASCUNHO,
                false, null, null, AGORA, AGORA, AGORA);
    }
}
