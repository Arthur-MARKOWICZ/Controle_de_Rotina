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

import com.controlerotina.execucao.repository.ExecucaoRepository;
import com.controlerotina.planner.dto.PlanoResponse;
import com.controlerotina.planner.entity.Plano;
import com.controlerotina.planner.entity.StatusPlano;
import com.controlerotina.planner.repository.BlocoPlanoRepository;
import com.controlerotina.planner.repository.PlanoRepository;
import com.controlerotina.shared.exception.PropostaInvalidaException;

@ExtendWith(MockitoExtension.class)
class PropostaServiceTest {

    private static final Instant AGORA = Instant.parse("2026-10-20T12:00:00Z");
    private static final UUID USUARIO = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PROPOSTA_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final LocalDate DIA = LocalDate.of(2026, 10, 20);

    @Mock
    PlanoRepository planoRepository;
    @Mock
    BlocoPlanoRepository blocoPlanoRepository;
    @Mock
    ExecucaoRepository execucaoRepository;
    @Mock
    PlanoConsultaService planoConsultaService;

    private PropostaService service;

    @BeforeEach
    void setUp() {
        service = new PropostaService(
                planoRepository,
                blocoPlanoRepository,
                execucaoRepository,
                planoConsultaService,
                Clock.fixed(AGORA, ZoneOffset.UTC)
        );
    }

    @Test
    void deveAceitarPropostaSemPlanoVigente() {
        Plano rascunho = rascunho();
        when(planoRepository.findByUsuarioIdAndPropostaIdAndStatus(
                USUARIO, PROPOSTA_ID, StatusPlano.RASCUNHO)).thenReturn(List.of(rascunho));
        when(planoRepository.findFirstByUsuarioIdAndDataAndStatusOrderByVersaoDesc(
                USUARIO, DIA, StatusPlano.APROVADO)).thenReturn(Optional.empty());
        PlanoResponse resposta = PlanoResponse.from(rascunho, List.of(), false);
        when(planoConsultaService.buscarPorId(USUARIO, rascunho.getId())).thenReturn(Optional.of(resposta));

        PlanoResponse aceito = service.aceitar(USUARIO, PROPOSTA_ID);

        assertThat(rascunho.getStatus()).isEqualTo(StatusPlano.APROVADO);
        assertThat(aceito.id()).isEqualTo(rascunho.getId());
        verify(planoRepository).save(rascunho);
    }

    @Test
    void deveRejeitarPropostaInexistenteSemPersistir() {
        when(planoRepository.findByUsuarioIdAndPropostaIdAndStatus(
                USUARIO, PROPOSTA_ID, StatusPlano.RASCUNHO)).thenReturn(List.of());

        assertThatThrownBy(() -> service.aceitar(USUARIO, PROPOSTA_ID))
                .isInstanceOf(PropostaInvalidaException.class);
        verify(planoRepository, never()).save(any());
    }

    @Test
    void deveRejeitarPropostaDeOutroUsuario() {
        when(planoRepository.findByUsuarioIdAndPropostaIdAndStatus(
                any(), any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.aceitar(USUARIO, UUID.randomUUID()))
                .isInstanceOf(PropostaInvalidaException.class);
        verify(planoRepository, never()).save(any());
    }

    @Test
    void deveDescartarRascunhosDaProposta() {
        Plano rascunho = rascunho();
        when(planoRepository.findByUsuarioIdAndPropostaIdAndStatus(
                USUARIO, PROPOSTA_ID, StatusPlano.RASCUNHO)).thenReturn(List.of(rascunho));

        service.descartar(USUARIO, PROPOSTA_ID);

        assertThat(rascunho.getStatus()).isEqualTo(StatusPlano.DESCARTADO);
        verify(planoRepository).save(rascunho);
    }

    @Test
    void deveRejeitarDescarteDePropostaJaResolvida() {
        when(planoRepository.findByUsuarioIdAndPropostaIdAndStatus(
                USUARIO, PROPOSTA_ID, StatusPlano.RASCUNHO)).thenReturn(List.of());

        assertThatThrownBy(() -> service.descartar(USUARIO, PROPOSTA_ID))
                .isInstanceOf(PropostaInvalidaException.class);
        verify(planoRepository, never()).save(any());
    }

    @Test
    void deveAceitarSemCopiarBlocosQuandoVigenteNaoTemConcluidos() {
        Plano rascunho = rascunho();
        Plano vigente = new Plano(
                UUID.randomUUID(), USUARIO, DIA, 1, StatusPlano.APROVADO,
                false, null, null, AGORA, AGORA, AGORA);
        when(planoRepository.findByUsuarioIdAndPropostaIdAndStatus(
                USUARIO, PROPOSTA_ID, StatusPlano.RASCUNHO)).thenReturn(List.of(rascunho));
        when(planoRepository.findFirstByUsuarioIdAndDataAndStatusOrderByVersaoDesc(
                USUARIO, DIA, StatusPlano.APROVADO)).thenReturn(Optional.of(vigente));
        when(blocoPlanoRepository.findByPlanoIdOrderByOrdemAsc(vigente.getId())).thenReturn(List.of());
        PlanoResponse resposta = PlanoResponse.from(rascunho, List.of(), false);
        when(planoConsultaService.buscarPorId(USUARIO, rascunho.getId())).thenReturn(Optional.of(resposta));

        service.aceitar(USUARIO, PROPOSTA_ID);

        verify(blocoPlanoRepository, never()).delete(any());
        verify(execucaoRepository, never()).save(any());
        assertThat(rascunho.getStatus()).isEqualTo(StatusPlano.APROVADO);
    }

    @Test
    void deveEscolherPrimeiroDiaAoAceitarPropostaComVariosDias() {
        Plano dia1 = new Plano(
                UUID.randomUUID(), USUARIO, DIA, 2, StatusPlano.RASCUNHO,
                false, null, PROPOSTA_ID, AGORA, AGORA, AGORA);
        Plano dia2 = new Plano(
                UUID.randomUUID(), USUARIO, DIA.plusDays(1), 1, StatusPlano.RASCUNHO,
                false, null, PROPOSTA_ID, AGORA, AGORA, AGORA);
        when(planoRepository.findByUsuarioIdAndPropostaIdAndStatus(
                USUARIO, PROPOSTA_ID, StatusPlano.RASCUNHO)).thenReturn(List.of(dia2, dia1));
        when(planoRepository.findFirstByUsuarioIdAndDataAndStatusOrderByVersaoDesc(any(), any(), any()))
                .thenReturn(Optional.empty());
        PlanoResponse resposta = PlanoResponse.from(dia1, List.of(), false);
        when(planoConsultaService.buscarPorId(USUARIO, dia1.getId())).thenReturn(Optional.of(resposta));

        PlanoResponse aceito = service.aceitar(USUARIO, PROPOSTA_ID);

        assertThat(aceito.id()).isEqualTo(dia1.getId());
        assertThat(dia1.getStatus()).isEqualTo(StatusPlano.APROVADO);
        assertThat(dia2.getStatus()).isEqualTo(StatusPlano.APROVADO);
    }

    private Plano rascunho() {
        return new Plano(
                UUID.randomUUID(), USUARIO, DIA, 2, StatusPlano.RASCUNHO,
                false, null, PROPOSTA_ID, AGORA, AGORA, AGORA);
    }
}
