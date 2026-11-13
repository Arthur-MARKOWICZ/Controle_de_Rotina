package com.controlerotina.auth.job;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.controlerotina.auth.service.RefreshTokenService;

@ExtendWith(MockitoExtension.class)
class RefreshTokenLimpezaJobTest {

    @Mock
    RefreshTokenService refreshTokenService;

    @Test
    void deveDelegarLimpezaAoService() {
        when(refreshTokenService.limparTokensAntigos()).thenReturn(4);

        new RefreshTokenLimpezaJob(refreshTokenService).limparTokensAntigos();

        verify(refreshTokenService).limparTokensAntigos();
    }

    @Test
    void listaVaziaNaoDeveLancar() {
        when(refreshTokenService.limparTokensAntigos()).thenReturn(0);

        assertThatCode(() -> new RefreshTokenLimpezaJob(refreshTokenService).limparTokensAntigos())
                .doesNotThrowAnyException();
        verify(refreshTokenService).limparTokensAntigos();
    }

    @Test
    void deveAceitarNenhumTokenRemovidoEmChamadaSeguinte() {
        when(refreshTokenService.limparTokensAntigos()).thenReturn(2, 0);
        RefreshTokenLimpezaJob job = new RefreshTokenLimpezaJob(refreshTokenService);

        job.limparTokensAntigos();
        job.limparTokensAntigos();

        verify(refreshTokenService, times(2)).limparTokensAntigos();
    }

    @Test
    void devePropagarFalhaDoServiceSemEngolir() {
        when(refreshTokenService.limparTokensAntigos()).thenThrow(new IllegalStateException("falha no banco"));

        assertThatThrownBy(() -> new RefreshTokenLimpezaJob(refreshTokenService).limparTokensAntigos())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("falha no banco");
    }

    @Test
    void naoDeveChamarLimpezaAntesDeExecutarOJob() {
        new RefreshTokenLimpezaJob(refreshTokenService);
        verify(refreshTokenService, times(0)).limparTokensAntigos();
    }

    @Test
    void deveTratarRemocaoDeUmUnicoTokenExpirado() {
        when(refreshTokenService.limparTokensAntigos()).thenReturn(1);

        assertThatCode(() -> new RefreshTokenLimpezaJob(refreshTokenService).limparTokensAntigos())
                .doesNotThrowAnyException();
        verify(refreshTokenService).limparTokensAntigos();
    }

    @Test
    void devePermitirRelimparAposFalha() {
        when(refreshTokenService.limparTokensAntigos())
                .thenThrow(new IllegalStateException("timeout"))
                .thenReturn(0);
        RefreshTokenLimpezaJob job = new RefreshTokenLimpezaJob(refreshTokenService);

        assertThatThrownBy(job::limparTokensAntigos).isInstanceOf(IllegalStateException.class);
        assertThatCode(job::limparTokensAntigos).doesNotThrowAnyException();
        verify(refreshTokenService, times(2)).limparTokensAntigos();
    }
}
