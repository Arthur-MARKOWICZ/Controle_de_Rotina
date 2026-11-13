package com.controlerotina.auth.cookie;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import com.controlerotina.config.SecurityProperties;

class RefreshCookieFactoryTest {

    @Test
    void deveCriarCookieHttpOnlyComPathAuthEMaxAgeDoTtl() {
        RefreshCookieFactory factory = factory(false, "Lax");

        ResponseCookie cookie = factory.criar("refresh-opaco");

        assertThat(cookie.getName()).isEqualTo("refresh_token");
        assertThat(cookie.getValue()).isEqualTo("refresh-opaco");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/auth");
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.isSecure()).isFalse();
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofDays(5));
    }

    @Test
    void deveMarcarSecureQuandoConfigurado() {
        RefreshCookieFactory factory = factory(true, "None");

        ResponseCookie cookie = factory.criar("segredo");

        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("None");
    }

    @Test
    void deveLimparCookieComMaxAgeZeroEValorVazio() {
        RefreshCookieFactory factory = factory(false, "Lax");

        ResponseCookie cookie = factory.limpar();

        assertThat(cookie.getName()).isEqualTo("refresh_token");
        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/auth");
    }

    @Test
    void limparNaoDeveManterOValorAnteriorDoRefresh() {
        RefreshCookieFactory factory = factory(false, "Lax");

        ResponseCookie criado = factory.criar("ainda-valido");
        ResponseCookie limpo = factory.limpar();

        assertThat(criado.getValue()).isEqualTo("ainda-valido");
        assertThat(limpo.getValue()).isNotEqualTo("ainda-valido");
        assertThat(limpo.getMaxAge().isZero()).isTrue();
    }

    @Test
    void criarComTokenVazioAindaDeveRespeitarAtributosDeSeguranca() {
        RefreshCookieFactory factory = factory(true, "Strict");

        ResponseCookie cookie = factory.criar("");

        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getPath()).isEqualTo("/auth");
    }

    @Test
    void deveUsarNomeConfiguradoDoCookie() {
        SecurityProperties properties = new SecurityProperties(
                jwt(),
                new SecurityProperties.RefreshCookie("__Secure-refresh_token", "/auth", true, true, "Lax")
        );
        RefreshCookieFactory factory = new RefreshCookieFactory(properties);

        assertThat(factory.criar("x").getName()).isEqualTo("__Secure-refresh_token");
        assertThat(factory.limpar().getName()).isEqualTo("__Secure-refresh_token");
    }

    @Test
    void maxAgeDeCriarDeveSeguirTtlDoRefreshENaoDoAccess() {
        SecurityProperties properties = new SecurityProperties(
                new SecurityProperties.Jwt(
                        "controle-rotina-test-secret-key-min-32-chars!",
                        Duration.ofMinutes(15),
                        Duration.ofHours(12),
                        "controle-rotina",
                        "controle-rotina-api"
                ),
                new SecurityProperties.RefreshCookie("refresh_token", "/auth", true, false, "Lax")
        );

        ResponseCookie cookie = new RefreshCookieFactory(properties).criar("tok");

        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofHours(12));
        assertThat(cookie.getMaxAge()).isNotEqualTo(Duration.ofMinutes(15));
    }

    private static RefreshCookieFactory factory(boolean secure, String sameSite) {
        return new RefreshCookieFactory(new SecurityProperties(
                jwt(),
                new SecurityProperties.RefreshCookie("refresh_token", "/auth", true, secure, sameSite)
        ));
    }

    private static SecurityProperties.Jwt jwt() {
        return new SecurityProperties.Jwt(
                "controle-rotina-test-secret-key-min-32-chars!",
                Duration.ofMinutes(15),
                Duration.ofDays(5),
                "controle-rotina",
                "controle-rotina-api"
        );
    }
}
