package com.controlerotina.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
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

import com.controlerotina.auth.entity.RefreshToken;
import com.controlerotina.auth.repository.RefreshTokenRepository;
import com.controlerotina.config.SecurityProperties;
import com.controlerotina.shared.exception.CredenciaisInvalidasException;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Instant AGORA = Instant.parse("2026-10-29T12:00:00Z");
    private static final UUID USUARIO_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID USUARIO_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Mock
    RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        SecurityProperties properties = new SecurityProperties(
                new SecurityProperties.Jwt(
                        "controle-rotina-test-secret-key-min-32-chars!",
                        Duration.ofMinutes(15),
                        Duration.ofDays(5),
                        "controle-rotina",
                        "controle-rotina-api"
                ),
                new SecurityProperties.RefreshCookie("refresh_token", "/auth", true, false, "Lax")
        );
        service = new RefreshTokenService(
                refreshTokenRepository,
                properties,
                Clock.fixed(AGORA, ZoneOffset.UTC)
        );
    }

    @Test
    void deveEmitirTokenOpacoEPersistirHash() {
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        String token = service.emitir(USUARIO_A);

        assertThat(token).isNotBlank();
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken salvo = captor.getValue();
        assertThat(salvo.getUsuarioId()).isEqualTo(USUARIO_A);
        assertThat(salvo.getTokenHash()).isEqualTo(RefreshTokenService.hash(token));
        assertThat(salvo.getExpiresAt()).isEqualTo(AGORA.plus(Duration.ofDays(5)));
        assertThat(salvo.getRevokedAt()).isNull();
    }

    @Test
    void deveRejeitarRefreshEmBranco() {
        assertThatThrownBy(() -> service.exigirAtivo("  "))
                .isInstanceOf(CredenciaisInvalidasException.class);
        assertThatThrownBy(() -> service.exigirAtivo(null))
                .isInstanceOf(CredenciaisInvalidasException.class);
        verify(refreshTokenRepository, never()).findByTokenHash(any());
    }

    @Test
    void deveRejeitarRefreshDesconhecido() {
        when(refreshTokenRepository.findByTokenHash(RefreshTokenService.hash("sumido")))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.exigirAtivo("sumido"))
                .isInstanceOf(CredenciaisInvalidasException.class);
    }

    @Test
    void deveRejeitarRefreshExpiradoSemRotacionar() {
        RefreshToken expirado = token(USUARIO_A, "velho", AGORA.minusSeconds(1), null);
        when(refreshTokenRepository.findByTokenHash(RefreshTokenService.hash("velho")))
                .thenReturn(Optional.of(expirado));

        assertThatThrownBy(() -> service.rotacionar("velho"))
                .isInstanceOf(CredenciaisInvalidasException.class);
        assertThat(expirado.getRevokedAt()).isNull();
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void reusoDeRefreshRevogadoDeveRevogarFamiliaDoUsuario() {
        RefreshToken revogado = token(USUARIO_A, "reusado", AGORA.plus(Duration.ofDays(1)), AGORA.minusSeconds(10));
        RefreshToken irmaoAtivo = token(USUARIO_A, "irmao", AGORA.plus(Duration.ofDays(1)), null);
        when(refreshTokenRepository.findByTokenHash(RefreshTokenService.hash("reusado")))
                .thenReturn(Optional.of(revogado));
        when(refreshTokenRepository.findByUsuarioIdAndRevokedAtIsNull(USUARIO_A))
                .thenReturn(List.of(irmaoAtivo));

        assertThatThrownBy(() -> service.rotacionar("reusado"))
                .isInstanceOf(CredenciaisInvalidasException.class);

        assertThat(irmaoAtivo.getRevokedAt()).isEqualTo(AGORA);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void rotacionarTokenDeOutroUsuarioNaoDeveAlterarFamiliaDoUsuarioA() {
        RefreshToken tokenB = token(USUARIO_B, "do-b", AGORA.plus(Duration.ofDays(1)), null);
        when(refreshTokenRepository.findByTokenHash(RefreshTokenService.hash("do-b")))
                .thenReturn(Optional.of(tokenB));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
        when(refreshTokenRepository.deleteExpiradosOuRevogadosAntesDe(any())).thenReturn(0);

        RefreshTokenService.Rotacao rotacao = service.rotacionar("do-b");

        assertThat(rotacao.usuarioId()).isEqualTo(USUARIO_B);
        assertThat(tokenB.getRevokedAt()).isEqualTo(AGORA);
        verify(refreshTokenRepository, never()).findByUsuarioIdAndRevokedAtIsNull(USUARIO_A);
    }

    @Test
    void revogarTokenAusenteOuEmBrancoNaoDeveTocarNoRepositorio() {
        service.revogar(null);
        service.revogar("  ");

        verify(refreshTokenRepository, never()).findByTokenHash(any());
    }

    @Test
    void limparTokensAntigosComListaVaziaDeveRetornarZero() {
        when(refreshTokenRepository.deleteExpiradosOuRevogadosAntesDe(AGORA.minus(RefreshTokenService.JANELA_RETENCAO)))
                .thenReturn(0);

        assertThat(service.limparTokensAntigos()).isZero();
    }

    @Test
    void limparTokensAntigosDeveDelegarLimiteDeRetencao() {
        when(refreshTokenRepository.deleteExpiradosOuRevogadosAntesDe(AGORA.minus(RefreshTokenService.JANELA_RETENCAO)))
                .thenReturn(3);

        assertThat(service.limparTokensAntigos()).isEqualTo(3);
    }

    private static RefreshToken token(UUID usuarioId, String bruto, Instant expiresAt, Instant revokedAt) {
        RefreshToken token = new RefreshToken(
                UUID.randomUUID(),
                usuarioId,
                RefreshTokenService.hash(bruto),
                expiresAt,
                AGORA.minus(Duration.ofDays(1))
        );
        if (revokedAt != null) {
            token.revogar(revokedAt);
        }
        return token;
    }
}
