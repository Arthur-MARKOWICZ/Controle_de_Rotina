package com.controlerotina.planner.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.controlerotina.planner.entity.StatusPlano;
import com.controlerotina.planner.repository.PlanoRepository;
import com.controlerotina.planner.service.PlannerGeracaoService;
import com.controlerotina.usuario.entity.Usuario;
import com.controlerotina.usuario.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class PlannerGeracaoDiariaJobTest {

    @Mock
    UsuarioRepository usuarioRepository;

    @Mock
    PlanoRepository planoRepository;

    @Mock
    PlannerGeracaoService plannerGeracaoService;

    @Mock
    Clock clock;

    @InjectMocks
    PlannerGeracaoDiariaJob job;

    @Test
    void gerarPlanosDoDiaUsaDataCorrenteEmSaoPaulo() {
        Instant instante = Instant.parse("2026-10-21T03:00:00Z"); // 00:00 America/Sao_Paulo
        Clock fixedUtc = Clock.fixed(instante, ZoneOffset.UTC);
        when(clock.withZone(PlannerGeracaoDiariaJob.ZONE_SAO_PAULO))
                .thenReturn(fixedUtc.withZone(PlannerGeracaoDiariaJob.ZONE_SAO_PAULO));

        UUID usuarioId = UUID.randomUUID();
        Usuario usuario = new Usuario(
                usuarioId, "a@email.com", "hash", "Nome", instante, instante
        );
        when(usuarioRepository.findAll()).thenReturn(List.of(usuario));
        when(planoRepository.existsByUsuarioIdAndDataAndStatus(
                eq(usuarioId), eq(LocalDate.of(2026, 10, 21)), eq(StatusPlano.APROVADO)
        )).thenReturn(false);

        job.gerarPlanosDoDia();

        verify(plannerGeracaoService).gerar(usuarioId, LocalDate.of(2026, 10, 21));
    }

    @Test
    void naoChamaGeracaoQuandoJaAprovado() {
        UUID usuarioId = UUID.randomUUID();
        Instant instante = Instant.parse("2026-10-20T12:00:00Z");
        Usuario usuario = new Usuario(
                usuarioId, "a@email.com", "hash", "Nome", instante, instante
        );
        when(usuarioRepository.findAll()).thenReturn(List.of(usuario));
        when(planoRepository.existsByUsuarioIdAndDataAndStatus(
                eq(usuarioId), any(LocalDate.class), eq(StatusPlano.APROVADO)
        )).thenReturn(true);

        job.gerarPlanosPara(LocalDate.of(2026, 10, 20));

        verify(plannerGeracaoService, never()).gerar(any(), any());
    }
}
