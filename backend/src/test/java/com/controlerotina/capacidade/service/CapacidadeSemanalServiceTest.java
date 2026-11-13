package com.controlerotina.capacidade.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.controlerotina.capacidade.dto.AtualizarCapacidadeSemanalRequest;
import com.controlerotina.capacidade.dto.CriarCapacidadeSemanalItemRequest;
import com.controlerotina.capacidade.entity.CapacidadeSemanal;
import com.controlerotina.capacidade.entity.DiaSemana;
import com.controlerotina.capacidade.repository.CapacidadeSemanalRepository;
import com.controlerotina.planner.service.PropostaGeracaoService;
import com.controlerotina.projeto.entity.ReservaProjetoSemanal;
import com.controlerotina.projeto.repository.ReservaProjetoSemanalRepository;
import com.controlerotina.shared.exception.CapacidadeAbaixoDasReservasException;
import com.controlerotina.shared.exception.CapacidadeLoteVazioException;
import com.controlerotina.shared.exception.CapacidadeSemanalConflitoException;
import com.controlerotina.shared.exception.CapacidadeSemanalNaoEncontradaException;
import com.controlerotina.shared.exception.DiaSemanaDuplicadoNoLoteException;

@ExtendWith(MockitoExtension.class)
class CapacidadeSemanalServiceTest {

    private static final Instant AGORA = Instant.parse("2026-10-27T12:00:00Z");
    private static final UUID USUARIO = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    CapacidadeSemanalRepository repository;
    @Mock
    ReservaProjetoSemanalRepository reservaRepository;
    @Mock
    PropostaGeracaoService propostaGeracaoService;

    private CapacidadeSemanalService service;

    @BeforeEach
    void setUp() {
        service = new CapacidadeSemanalService(
                repository, reservaRepository, propostaGeracaoService, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @Test
    void deveCriarCapacidadeSemanal() {
        when(repository.existsByUsuarioIdAndDiaSemanaAndAtivoTrue(USUARIO, DiaSemana.SEGUNDA)).thenReturn(false);
        when(repository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        var respostas = service.criar(USUARIO, List.of(
                new CriarCapacidadeSemanalItemRequest(DiaSemana.SEGUNDA, 4)));

        assertThat(respostas).hasSize(1);
        assertThat(respostas.get(0).horas()).isEqualTo(4);
    }

    @Test
    void deveRejeitarLoteVazio() {
        assertThatThrownBy(() -> service.criar(USUARIO, List.of()))
                .isInstanceOf(CapacidadeLoteVazioException.class);
        verify(repository, never()).saveAll(any());
    }

    @Test
    void deveRejeitarDiaDuplicadoNoLote() {
        var itens = List.of(
                new CriarCapacidadeSemanalItemRequest(DiaSemana.SEGUNDA, 1),
                new CriarCapacidadeSemanalItemRequest(DiaSemana.SEGUNDA, 2)
        );

        assertThatThrownBy(() -> service.criar(USUARIO, itens))
                .isInstanceOf(DiaSemanaDuplicadoNoLoteException.class);
        verify(repository, never()).saveAll(any());
    }

    @Test
    void deveRejeitarConflitoComDiaAtivo() {
        when(repository.existsByUsuarioIdAndDiaSemanaAndAtivoTrue(USUARIO, DiaSemana.TERCA)).thenReturn(true);

        assertThatThrownBy(() -> service.criar(
                USUARIO, List.of(new CriarCapacidadeSemanalItemRequest(DiaSemana.TERCA, 2))))
                .isInstanceOf(CapacidadeSemanalConflitoException.class);
    }

    @Test
    void deveRejeitarReduzirHorasAbaixoDasReservas() {
        UUID id = UUID.randomUUID();
        CapacidadeSemanal atual = new CapacidadeSemanal(
                id, USUARIO, DiaSemana.SEGUNDA, 4, true, AGORA, AGORA);
        when(repository.findByIdAndUsuarioId(id, USUARIO)).thenReturn(Optional.of(atual));
        when(reservaRepository.findByUsuarioIdAndDiaSemana(USUARIO, DiaSemana.SEGUNDA))
                .thenReturn(List.of(new ReservaProjetoSemanal(
                        UUID.randomUUID(), USUARIO, UUID.randomUUID(), DiaSemana.SEGUNDA, 180, AGORA, AGORA)));

        assertThatThrownBy(() -> service.atualizar(
                USUARIO, id, new AtualizarCapacidadeSemanalRequest(null, 2)))
                .isInstanceOf(CapacidadeAbaixoDasReservasException.class);
        assertThat(atual.getHoras()).isEqualTo(4);
    }

    @Test
    void deveRejeitarRestaurarCapacidadeAindaAtiva() {
        UUID id = UUID.randomUUID();
        CapacidadeSemanal ativa = new CapacidadeSemanal(
                id, USUARIO, DiaSemana.SEGUNDA, 2, true, AGORA, AGORA);
        when(repository.findByIdAndUsuarioId(id, USUARIO)).thenReturn(Optional.of(ativa));

        assertThatThrownBy(() -> service.restaurar(USUARIO, id))
                .isInstanceOf(CapacidadeSemanalNaoEncontradaException.class);
        assertThat(ativa.isAtivo()).isTrue();
    }

    @Test
    void deveRejeitarCapacidadeDeOutroUsuario() {
        when(repository.findByIdAndUsuarioId(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorId(USUARIO, UUID.randomUUID()))
                .isInstanceOf(CapacidadeSemanalNaoEncontradaException.class);
    }
}
