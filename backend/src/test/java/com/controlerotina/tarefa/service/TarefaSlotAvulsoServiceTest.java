package com.controlerotina.tarefa.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.controlerotina.execucao.entity.Execucao;
import com.controlerotina.execucao.entity.StatusExecucao;
import com.controlerotina.execucao.repository.ExecucaoRepository;
import com.controlerotina.planner.entity.BlocoPlano;
import com.controlerotina.planner.entity.Plano;
import com.controlerotina.planner.entity.StatusPlano;
import com.controlerotina.planner.repository.BlocoPlanoRepository;
import com.controlerotina.planner.repository.PlanoRepository;
import com.controlerotina.shared.exception.TarefaNaoElegivelException;
import com.controlerotina.shared.exception.TarefaNaoEncontradaException;
import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;
import com.controlerotina.tarefa.repository.TarefaRepository;

@ExtendWith(MockitoExtension.class)
class TarefaSlotAvulsoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-10-20T15:00:00Z");
    private static final LocalDate TERCA = LocalDate.of(2026, 10, 20);
    private static final UUID USUARIO = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID TAREFA_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Mock
    TarefaRepository tarefaRepository;
    @Mock
    PlanoRepository planoRepository;
    @Mock
    BlocoPlanoRepository blocoPlanoRepository;
    @Mock
    ExecucaoRepository execucaoRepository;

    TarefaSlotAvulsoService service;

    @BeforeEach
    void setUp() {
        service = new TarefaSlotAvulsoService(
                tarefaRepository,
                planoRepository,
                blocoPlanoRepository,
                execucaoRepository,
                Clock.fixed(AGORA, ZoneOffset.UTC)
        );
    }

    @Test
    void deveCriarPlanoTarefaAvulsaEBlocoNaPrimeiraMaterializacao() {
        Tarefa tarefa = tarefaPendente(120);
        when(tarefaRepository.findByIdAndUsuarioId(TAREFA_ID, USUARIO)).thenReturn(Optional.of(tarefa));
        when(planoRepository.findByUsuarioIdAndDataAndStatus(USUARIO, TERCA, StatusPlano.TAREFA_AVULSA))
                .thenReturn(Optional.empty());
        when(planoRepository.findMaxVersaoByUsuarioIdAndData(USUARIO, TERCA)).thenReturn(0);
        when(planoRepository.save(any(Plano.class))).thenAnswer(inv -> inv.getArgument(0));
        when(blocoPlanoRepository.findMaxOrdemByPlanoId(any())).thenReturn(0);
        when(blocoPlanoRepository.findByPlanoIdAndTarefaIdOrderByOrdemAsc(any(), eq(TAREFA_ID)))
                .thenReturn(List.of());
        when(blocoPlanoRepository.save(any(BlocoPlano.class))).thenAnswer(inv -> inv.getArgument(0));
        when(execucaoRepository.findByBlocoPlanoId(any())).thenReturn(Optional.empty());

        TarefaSlotAvulsoService.SlotAvulso slot = service.materializar(USUARIO, TAREFA_ID, TERCA, 30);

        assertThat(slot.plano().getStatus()).isEqualTo(StatusPlano.TAREFA_AVULSA);
        assertThat(slot.plano().getVersao()).isEqualTo(1);
        assertThat(slot.bloco().getTarefaId()).isEqualTo(TAREFA_ID);
        assertThat(slot.bloco().getDuracaoMinutos()).isEqualTo(30);
        assertThat(slot.execucao()).isNull();
        verify(planoRepository).save(any(Plano.class));
        verify(blocoPlanoRepository).save(any(BlocoPlano.class));
    }

    @Test
    void deveReusarBlocoAbertoAtualizandoDuracao() {
        Tarefa tarefa = tarefaPendente(120);
        Plano plano = planoAvulso(UUID.randomUUID());
        BlocoPlano bloco = new BlocoPlano(
                UUID.randomUUID(), plano.getId(), TAREFA_ID, 15, 1, AGORA, AGORA);

        when(tarefaRepository.findByIdAndUsuarioId(TAREFA_ID, USUARIO)).thenReturn(Optional.of(tarefa));
        when(planoRepository.findByUsuarioIdAndDataAndStatus(USUARIO, TERCA, StatusPlano.TAREFA_AVULSA))
                .thenReturn(Optional.of(plano));
        when(blocoPlanoRepository.findByPlanoIdAndTarefaIdOrderByOrdemAsc(plano.getId(), TAREFA_ID))
                .thenReturn(List.of(bloco));
        when(execucaoRepository.findByBlocoPlanoId(bloco.getId())).thenReturn(Optional.empty());
        when(blocoPlanoRepository.save(any(BlocoPlano.class))).thenAnswer(inv -> inv.getArgument(0));

        TarefaSlotAvulsoService.SlotAvulso slot = service.materializar(USUARIO, TAREFA_ID, TERCA, 45);

        assertThat(slot.bloco().getId()).isEqualTo(bloco.getId());
        assertThat(slot.bloco().getDuracaoMinutos()).isEqualTo(45);
        verify(planoRepository, never()).save(any());
        verify(blocoPlanoRepository).save(bloco);
    }

    @Test
    void deveCriarNovoBlocoQuandoAnteriorConcluido() {
        Tarefa tarefa = tarefaPendente(120);
        Plano plano = planoAvulso(UUID.randomUUID());
        UUID blocoConcluidoId = UUID.randomUUID();
        BlocoPlano concluido = new BlocoPlano(
                blocoConcluidoId, plano.getId(), TAREFA_ID, 30, 1, AGORA, AGORA);
        Execucao execucaoConcluida = Execucao.concluirDiretoNova(
                UUID.randomUUID(), USUARIO, blocoConcluidoId, AGORA);

        when(tarefaRepository.findByIdAndUsuarioId(TAREFA_ID, USUARIO)).thenReturn(Optional.of(tarefa));
        when(planoRepository.findByUsuarioIdAndDataAndStatus(USUARIO, TERCA, StatusPlano.TAREFA_AVULSA))
                .thenReturn(Optional.of(plano));
        when(blocoPlanoRepository.findByPlanoIdAndTarefaIdOrderByOrdemAsc(plano.getId(), TAREFA_ID))
                .thenReturn(List.of(concluido));
        when(blocoPlanoRepository.findMaxOrdemByPlanoId(plano.getId())).thenReturn(1);
        when(blocoPlanoRepository.save(any(BlocoPlano.class))).thenAnswer(inv -> inv.getArgument(0));
        when(execucaoRepository.findByBlocoPlanoId(any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            if (blocoConcluidoId.equals(id)) {
                return Optional.of(execucaoConcluida);
            }
            return Optional.empty();
        });

        TarefaSlotAvulsoService.SlotAvulso slot = service.materializar(USUARIO, TAREFA_ID, TERCA, 25);

        assertThat(slot.bloco().getId()).isNotEqualTo(blocoConcluidoId);
        assertThat(slot.bloco().getDuracaoMinutos()).isEqualTo(25);
        assertThat(slot.bloco().getOrdem()).isEqualTo(2);
    }

    @Test
    void restanteMinutosSubtraiBlocosConcluidos() {
        Tarefa tarefa = tarefaPendente(120);
        when(execucaoRepository.somarDuracaoMinutosPorTarefaEStatus(
                TAREFA_ID, USUARIO, StatusExecucao.CONCLUIDO)).thenReturn(40L);

        assertThat(service.restanteMinutos(USUARIO, tarefa)).isEqualTo(80);
    }

    @Test
    void deveRejeitarPaiComFilhos() {
        Tarefa tarefa = tarefaPendente(120);
        when(tarefaRepository.findByIdAndUsuarioId(TAREFA_ID, USUARIO)).thenReturn(Optional.of(tarefa));
        when(tarefaRepository.existsByParentIdAndDeletedAtIsNull(TAREFA_ID)).thenReturn(true);
        lenient().when(planoRepository.findByUsuarioIdAndDataAndStatus(USUARIO, TERCA, StatusPlano.TAREFA_AVULSA))
                .thenReturn(Optional.empty());
        lenient().when(planoRepository.findMaxVersaoByUsuarioIdAndData(USUARIO, TERCA)).thenReturn(0);
        lenient().when(planoRepository.save(any(Plano.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(blocoPlanoRepository.findMaxOrdemByPlanoId(any())).thenReturn(0);
        lenient().when(blocoPlanoRepository.findByPlanoIdAndTarefaIdOrderByOrdemAsc(any(), eq(TAREFA_ID)))
                .thenReturn(List.of());
        lenient().when(blocoPlanoRepository.save(any(BlocoPlano.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(execucaoRepository.findByBlocoPlanoId(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.materializar(USUARIO, TAREFA_ID, TERCA, 30))
                .isInstanceOf(TarefaNaoElegivelException.class);
        verify(planoRepository, never()).save(any());
        verify(blocoPlanoRepository, never()).save(any());
    }

    @Test
    void deveRejeitarTarefaFinalizada() {
        Tarefa tarefa = new Tarefa(
                TAREFA_ID, USUARIO, "Estudar", "desc", Prioridade.MEDIA,
                AGORA, StatusTarefa.FINALIZADA, 60, AGORA, AGORA);
        when(tarefaRepository.findByIdAndUsuarioId(TAREFA_ID, USUARIO)).thenReturn(Optional.of(tarefa));

        assertThatThrownBy(() -> service.materializar(USUARIO, TAREFA_ID, TERCA, 30))
                .isInstanceOf(TarefaNaoElegivelException.class);
    }

    @Test
    void deveRejeitarTarefaDeletada() {
        Tarefa tarefa = tarefaPendente(60);
        tarefa.softDelete(AGORA);
        when(tarefaRepository.findByIdAndUsuarioId(TAREFA_ID, USUARIO)).thenReturn(Optional.of(tarefa));

        assertThatThrownBy(() -> service.materializar(USUARIO, TAREFA_ID, TERCA, 30))
                .isInstanceOf(TarefaNaoEncontradaException.class);
    }

    private Tarefa tarefaPendente(int duracao) {
        return new Tarefa(
                TAREFA_ID, USUARIO, "Estudar", "desc", Prioridade.MEDIA,
                AGORA, StatusTarefa.PENDENTE, duracao, AGORA, AGORA);
    }

    private Plano planoAvulso(UUID id) {
        return new Plano(
                id, USUARIO, TERCA, 1, StatusPlano.TAREFA_AVULSA,
                false, null, null, AGORA, AGORA, AGORA);
    }
}
