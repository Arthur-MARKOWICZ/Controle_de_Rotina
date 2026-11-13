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

import com.controlerotina.capacidade.dto.CriarCapacidadeOverrideItemRequest;
import com.controlerotina.capacidade.entity.CapacidadeOverride;
import com.controlerotina.capacidade.repository.CapacidadeOverrideRepository;
import com.controlerotina.planner.service.PropostaGeracaoService;
import com.controlerotina.shared.exception.CapacidadeLoteVazioException;
import com.controlerotina.shared.exception.CapacidadeOverrideConflitoException;
import com.controlerotina.shared.exception.CapacidadeOverrideNaoEncontradaException;
import com.controlerotina.shared.exception.DataDuplicadaNoLoteException;

@ExtendWith(MockitoExtension.class)
class CapacidadeOverrideServiceTest {

    private static final Instant AGORA = Instant.parse("2026-10-27T12:00:00Z");
    private static final UUID USUARIO = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final LocalDate DIA = LocalDate.of(2026, 10, 27);

    @Mock
    CapacidadeOverrideRepository repository;
    @Mock
    PropostaGeracaoService propostaGeracaoService;

    private CapacidadeOverrideService service;

    @BeforeEach
    void setUp() {
        service = new CapacidadeOverrideService(
                repository, propostaGeracaoService, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @Test
    void deveCriarOverrideNoLote() {
        when(repository.existsByUsuarioIdAndDataAndAtivoTrue(USUARIO, DIA)).thenReturn(false);
        when(repository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        var respostas = service.criar(USUARIO, List.of(new CriarCapacidadeOverrideItemRequest(DIA, 2)));

        assertThat(respostas).hasSize(1);
        assertThat(respostas.get(0).horas()).isEqualTo(2);
        verify(propostaGeracaoService).gerarSeDiaAprovado(USUARIO, DIA);
    }

    @Test
    void deveRejeitarLoteVazioSemPersistir() {
        assertThatThrownBy(() -> service.criar(USUARIO, List.of()))
                .isInstanceOf(CapacidadeLoteVazioException.class);
        verify(repository, never()).saveAll(any());
    }

    @Test
    void deveRejeitarLoteNuloSemPersistir() {
        assertThatThrownBy(() -> service.criar(USUARIO, null))
                .isInstanceOf(CapacidadeLoteVazioException.class);
        verify(repository, never()).saveAll(any());
    }

    @Test
    void deveRejeitarDatasDuplicadasNoLote() {
        var itens = List.of(
                new CriarCapacidadeOverrideItemRequest(DIA, 1),
                new CriarCapacidadeOverrideItemRequest(DIA, 2)
        );

        assertThatThrownBy(() -> service.criar(USUARIO, itens))
                .isInstanceOf(DataDuplicadaNoLoteException.class);
        verify(repository, never()).saveAll(any());
    }

    @Test
    void deveRejeitarConflitoComOverrideAtivo() {
        when(repository.existsByUsuarioIdAndDataAndAtivoTrue(USUARIO, DIA)).thenReturn(true);

        assertThatThrownBy(() -> service.criar(
                USUARIO, List.of(new CriarCapacidadeOverrideItemRequest(DIA, 1))))
                .isInstanceOf(CapacidadeOverrideConflitoException.class);
        verify(repository, never()).saveAll(any());
    }

    @Test
    void deveRejeitarRestaurarOverrideAindaAtivo() {
        UUID id = UUID.randomUUID();
        CapacidadeOverride ativo = new CapacidadeOverride(id, USUARIO, DIA, 2, true, AGORA, AGORA);
        when(repository.findByIdAndUsuarioId(id, USUARIO)).thenReturn(Optional.of(ativo));

        assertThatThrownBy(() -> service.restaurar(USUARIO, id))
                .isInstanceOf(CapacidadeOverrideNaoEncontradaException.class);
        assertThat(ativo.isAtivo()).isTrue();
        verify(propostaGeracaoService, never()).gerarSeDiaAprovado(any(), any());
    }

    @Test
    void deveRejeitarOverrideDeOutroUsuario() {
        when(repository.findByIdAndUsuarioId(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorId(USUARIO, UUID.randomUUID()))
                .isInstanceOf(CapacidadeOverrideNaoEncontradaException.class);
    }
}
