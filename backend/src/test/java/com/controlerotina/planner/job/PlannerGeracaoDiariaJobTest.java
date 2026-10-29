package com.controlerotina.planner.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.controlerotina.historico.PeriodoBRT;
import com.controlerotina.planner.entity.StatusPlano;
import com.controlerotina.planner.repository.PlanoRepository;
import com.controlerotina.planner.service.PlannerGeracaoService;
import com.controlerotina.shared.lock.PostgresAdvisoryLock;
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
    com.controlerotina.planner.service.PropostaGeracaoService propostaGeracaoService;

    @Mock
    PostgresAdvisoryLock advisoryLock;

    @Mock
    Clock clock;

    @InjectMocks
    PlannerGeracaoDiariaJob job;

    @BeforeEach
    void stubLockQueExecuta() {
        lenient().when(advisoryLock.tryWithLock(anyLong(), any())).thenAnswer(inv -> {
            inv.getArgument(1, Runnable.class).run();
            return true;
        });
    }

    @Test
    void gerarPlanosDoDiaUsaDataCorrenteEmSaoPaulo() {
        Instant instante = Instant.parse("2026-10-21T03:00:00Z"); // 00:00 America/Sao_Paulo
        Clock fixedUtc = Clock.fixed(instante, ZoneOffset.UTC);
        when(clock.withZone(PeriodoBRT.ZONE))
                .thenReturn(fixedUtc.withZone(PeriodoBRT.ZONE));

        UUID usuarioId = UUID.randomUUID();
        Usuario usuario = usuario(usuarioId, instante);
        stubPaginaUnica(List.of(usuario));
        when(planoRepository.existsByUsuarioIdAndDataAndStatus(
                eq(usuarioId), eq(LocalDate.of(2026, 10, 21)), eq(StatusPlano.APROVADO)
        )).thenReturn(false);

        job.gerarPlanosDoDia();

        verify(plannerGeracaoService).gerar(usuarioId, LocalDate.of(2026, 10, 21));
        verify(advisoryLock).tryWithLock(eq(PlannerGeracaoDiariaJob.LOCK_GERACAO_DIARIA), any());
    }

    @Test
    void naoChamaGeracaoQuandoJaAprovadoSemGatilho() {
        UUID usuarioId = UUID.randomUUID();
        Instant instante = Instant.parse("2026-10-20T12:00:00Z");
        Usuario usuario = usuario(usuarioId, instante);
        stubPaginaUnica(List.of(usuario));
        when(planoRepository.existsByUsuarioIdAndDataAndStatus(
                eq(usuarioId), any(LocalDate.class), eq(StatusPlano.APROVADO)
        )).thenReturn(true);
        when(propostaGeracaoService.gerarSeDiaAnteriorIncompleto(eq(usuarioId), any(LocalDate.class)))
                .thenReturn(false);

        job.gerarPlanosPara(LocalDate.of(2026, 10, 20));

        verify(plannerGeracaoService, never()).gerar(any(), any());
        verify(propostaGeracaoService).gerarSeDiaAnteriorIncompleto(eq(usuarioId), eq(LocalDate.of(2026, 10, 20)));
    }

    @Test
    void falhaEmUmUsuarioNaoImpedeOSeguinte() {
        Instant instante = Instant.parse("2026-10-20T12:00:00Z");
        LocalDate data = LocalDate.of(2026, 10, 20);
        UUID idFalha = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID idOk = UUID.fromString("22222222-2222-2222-2222-222222222222");
        stubPaginaUnica(List.of(usuario(idFalha, instante), usuario(idOk, instante)));
        when(planoRepository.existsByUsuarioIdAndDataAndStatus(any(), eq(data), eq(StatusPlano.APROVADO)))
                .thenReturn(false);
        doThrow(new RuntimeException("falha simulada"))
                .when(plannerGeracaoService).gerar(idFalha, data);

        job.gerarPlanosPara(data);

        verify(plannerGeracaoService).gerar(idFalha, data);
        verify(plannerGeracaoService).gerar(idOk, data);
    }

    @Test
    void naoGeraQuandoNaoObtemAdvisoryLock() {
        doReturn(false).when(advisoryLock).tryWithLock(anyLong(), any());

        job.gerarPlanosPara(LocalDate.of(2026, 10, 20));

        verify(usuarioRepository, never()).findAll(any(Pageable.class));
        verify(plannerGeracaoService, never()).gerar(any(), any());
    }

    @Test
    void segundaExecucaoSobrepostaNaoGeraDeNovoQuandoLockOcupado() {
        Instant instante = Instant.parse("2026-10-20T12:00:00Z");
        LocalDate data = LocalDate.of(2026, 10, 20);
        UUID usuarioId = UUID.randomUUID();
        stubPaginaUnica(List.of(usuario(usuarioId, instante)));
        when(planoRepository.existsByUsuarioIdAndDataAndStatus(
                eq(usuarioId), eq(data), eq(StatusPlano.APROVADO)
        )).thenReturn(false);

        AtomicBoolean lockOcupado = new AtomicBoolean(false);
        doAnswer(inv -> {
            if (!lockOcupado.compareAndSet(false, true)) {
                return false;
            }
            try {
                inv.getArgument(1, Runnable.class).run();
                return true;
            } finally {
                lockOcupado.set(false);
            }
        }).when(advisoryLock).tryWithLock(eq(PlannerGeracaoDiariaJob.LOCK_GERACAO_DIARIA), any());

        job.gerarPlanosPara(data);
        job.gerarPlanosPara(data);

        verify(plannerGeracaoService, times(2)).gerar(usuarioId, data);

        // Sobreposição: enquanto a 1ª segura o lock, a 2ª sai sem gerar
        lockOcupado.set(true);
        job.gerarPlanosPara(data);
        verify(plannerGeracaoService, times(2)).gerar(usuarioId, data);
    }

    private void stubPaginaUnica(List<Usuario> usuarios) {
        when(usuarioRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(
                        usuarios,
                        PageRequest.of(0, PlannerGeracaoDiariaJob.TAMANHO_PAGINA),
                        usuarios.size()
                ));
    }

    private static Usuario usuario(UUID id, Instant instante) {
        return new Usuario(id, id + "@email.com", "hash", "Nome", instante, instante);
    }
}
