package com.controlerotina.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

import com.controlerotina.planner.dto.PlanoResponse;
import com.controlerotina.planner.entity.Plano;
import com.controlerotina.planner.entity.StatusPlano;
import com.controlerotina.planner.repository.PlanoRepository;
import com.controlerotina.shared.exception.PlanoNaoAprovavelException;
import com.controlerotina.shared.exception.PlanoNaoEncontradoException;

@ExtendWith(MockitoExtension.class)
class PlanoAprovacaoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-10-20T12:00:00Z");
    private static final UUID USUARIO = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLANO_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final LocalDate DIA = LocalDate.of(2026, 10, 20);

    @Mock
    PlanoRepository planoRepository;
    @Mock
    PlanoConsultaService planoConsultaService;
    @Mock
    PropostaService propostaService;

    private PlanoAprovacaoService service;

    @BeforeEach
    void setUp() {
        service = new PlanoAprovacaoService(
                planoRepository,
                planoConsultaService,
                propostaService,
                Clock.fixed(AGORA, ZoneOffset.UTC)
        );
    }

    @Test
    void deveAprovarRascunhoCorrente() {
        Plano plano = rascunho(null);
        when(planoRepository.findByIdAndUsuarioId(PLANO_ID, USUARIO)).thenReturn(Optional.of(plano));
        when(planoRepository.findFirstByUsuarioIdAndDataAndStatusInOrderByVersaoDesc(any(), any(), any()))
                .thenReturn(Optional.of(plano));
        PlanoResponse resposta = PlanoResponse.from(plano, List.of(), false);
        when(planoConsultaService.buscarPorId(USUARIO, PLANO_ID)).thenReturn(Optional.of(resposta));

        PlanoResponse aprovado = service.aprovar(USUARIO, PLANO_ID);

        assertThat(plano.getStatus()).isEqualTo(StatusPlano.APROVADO);
        assertThat(aprovado.id()).isEqualTo(PLANO_ID);
        verify(planoRepository).save(plano);
        verify(propostaService, never()).aceitar(any(), any());
    }

    @Test
    void naoDeveAprovarPlanoDeOutroUsuario() {
        when(planoRepository.findByIdAndUsuarioId(PLANO_ID, USUARIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.aprovar(USUARIO, PLANO_ID))
                .isInstanceOf(PlanoNaoEncontradoException.class);
        verify(planoRepository, never()).save(any());
    }

    @Test
    void naoDeveAprovarPlanoJaAprovado() {
        Plano plano = new Plano(
                PLANO_ID, USUARIO, DIA, 1, StatusPlano.APROVADO,
                false, null, null, AGORA, AGORA, AGORA);
        when(planoRepository.findByIdAndUsuarioId(PLANO_ID, USUARIO)).thenReturn(Optional.of(plano));

        assertThatThrownBy(() -> service.aprovar(USUARIO, PLANO_ID))
                .isInstanceOf(PlanoNaoAprovavelException.class);
        assertThat(plano.getStatus()).isEqualTo(StatusPlano.APROVADO);
        verify(planoRepository, never()).save(any());
    }

    @Test
    void naoDeveAprovarRascunhoQueNaoECorrente() {
        Plano plano = rascunho(null);
        Plano corrente = new Plano(
                UUID.randomUUID(), USUARIO, DIA, 2, StatusPlano.RASCUNHO,
                false, null, null, AGORA, AGORA, AGORA);
        when(planoRepository.findByIdAndUsuarioId(PLANO_ID, USUARIO)).thenReturn(Optional.of(plano));
        when(planoRepository.findFirstByUsuarioIdAndDataAndStatusInOrderByVersaoDesc(any(), any(), any()))
                .thenReturn(Optional.of(corrente));

        assertThatThrownBy(() -> service.aprovar(USUARIO, PLANO_ID))
                .isInstanceOf(PlanoNaoAprovavelException.class);
        assertThat(plano.getStatus()).isEqualTo(StatusPlano.RASCUNHO);
        verify(planoRepository, never()).save(any());
    }

    @Test
    void deveDelegarAceiteQuandoRascunhoTemProposta() {
        UUID propostaId = UUID.randomUUID();
        Plano plano = rascunho(propostaId);
        when(planoRepository.findByIdAndUsuarioId(PLANO_ID, USUARIO)).thenReturn(Optional.of(plano));
        when(planoRepository.findFirstByUsuarioIdAndDataAndStatusInOrderByVersaoDesc(any(), any(), any()))
                .thenReturn(Optional.of(plano));
        PlanoResponse resposta = PlanoResponse.from(plano, List.of(), false);
        when(propostaService.aceitar(USUARIO, propostaId)).thenReturn(resposta);

        PlanoResponse aprovado = service.aprovar(USUARIO, PLANO_ID);

        assertThat(aprovado).isSameAs(resposta);
        verify(propostaService).aceitar(USUARIO, propostaId);
        verify(planoRepository, never()).save(any());
    }

    @Test
    void naoDeveAprovarPlanoInexistente() {
        when(planoRepository.findByIdAndUsuarioId(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.aprovar(USUARIO, UUID.randomUUID()))
                .isInstanceOf(PlanoNaoEncontradoException.class);
    }

    @Test
    void naoDeveAprovarPlanoDescartado() {
        Plano plano = new Plano(
                PLANO_ID, USUARIO, DIA, 1, StatusPlano.DESCARTADO,
                false, null, null, AGORA, AGORA, AGORA);
        when(planoRepository.findByIdAndUsuarioId(PLANO_ID, USUARIO)).thenReturn(Optional.of(plano));

        assertThatThrownBy(() -> service.aprovar(USUARIO, PLANO_ID))
                .isInstanceOf(PlanoNaoAprovavelException.class);
        verify(planoRepository, never()).save(any());
    }

    private static Plano rascunho(UUID propostaId) {
        return new Plano(
                PLANO_ID, USUARIO, DIA, 1, StatusPlano.RASCUNHO,
                false, null, propostaId, AGORA, AGORA, AGORA);
    }
}
