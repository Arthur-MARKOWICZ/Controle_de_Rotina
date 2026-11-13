package com.controlerotina.mobile.sessao

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RefreshCookieTest {
    @Test
    fun deveLerCookieLocalRefreshToken() {
        assertEquals(
            MudancaRefresh.Novo(RefreshCookie("refresh_token", "abc.def")),
            lerRefreshDeSetCookie(listOf("refresh_token=abc.def; Path=/auth; HttpOnly; SameSite=Lax")),
        )
    }

    @Test
    fun deveLerCookieSecureRefreshToken() {
        assertEquals(
            MudancaRefresh.Novo(RefreshCookie("__Secure-refresh_token", "xyz")),
            lerRefreshDeSetCookie(listOf("__Secure-refresh_token=xyz; Path=/auth; Secure; HttpOnly; SameSite=Lax")),
        )
    }

    @Test
    fun deveReconhecerNomeQueContemRefresh() {
        assertEquals(
            MudancaRefresh.Novo(RefreshCookie("X-Refresh", "abc123")),
            lerRefreshDeSetCookie(listOf("X-Refresh=abc123")),
        )
    }

    @Test
    fun deveIgnorarCookieNaoRelacionado() {
        assertNull(lerRefreshDeSetCookie(listOf("session=1; Path=/")))
    }

    @Test
    fun deveIgnorarListaVaziaDeCabecalhos() {
        assertNull(lerRefreshDeSetCookie(emptyList()))
    }

    @Test
    fun deveIgnorarCabecalhoSemIgual() {
        assertNull(lerRefreshDeSetCookie(listOf("HttpOnly", "session=1")))
    }

    @Test
    fun devePegarRefreshDepoisDeCookieNaoRelacionado() {
        assertEquals(
            MudancaRefresh.Novo(RefreshCookie("refresh_token", "token")),
            lerRefreshDeSetCookie(listOf("session=abc; Path=/", "refresh_token=token; Path=/auth")),
        )
    }

    @Test
    fun deveLimparQuandoValorVazio() {
        assertEquals(MudancaRefresh.Limpo, lerRefreshDeSetCookie(listOf("refresh_token=; Path=/auth")))
    }

    @Test
    fun deveLimparQuandoMaxAgeZero() {
        assertEquals(
            MudancaRefresh.Limpo,
            lerRefreshDeSetCookie(
                listOf("refresh_token=abc; Path=/auth; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT"),
            ),
        )
    }

    @Test
    fun deveRoundTripCabecalho() {
        val cookie = RefreshCookie("refresh_token", "a=b")
        assertEquals(cookie, RefreshCookie.deCabecalho(cookie.cabecalho))
    }

    @Test
    fun deveRecusarCabecalhoComValorVazio() {
        assertNull(RefreshCookie.deCabecalho("refresh_token="))
        assertNull(RefreshCookie.deCabecalho("refresh_token=   "))
    }

    @Test
    fun deveRecusarCabecalhoSemIgual() {
        assertNull(RefreshCookie.deCabecalho("refresh_token"))
        assertNull(RefreshCookie.deCabecalho("=valor"))
    }
}
