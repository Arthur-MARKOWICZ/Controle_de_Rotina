package com.controlerotina.mobile.sessao

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// Casos portados de mcp/src/cookies.test.ts, mais Max-Age=0 do logout.
class RefreshCookieTest {
    @Test
    fun leNomeLocalESeguro() {
        assertEquals(
            MudancaRefresh.Novo(RefreshCookie("refresh_token", "abc.def")),
            lerRefreshDeSetCookie(listOf("refresh_token=abc.def; Path=/auth; HttpOnly; SameSite=Lax")),
        )
        assertEquals(
            MudancaRefresh.Novo(RefreshCookie("__Secure-refresh_token", "xyz")),
            lerRefreshDeSetCookie(listOf("__Secure-refresh_token=xyz; Path=/auth; Secure; HttpOnly; SameSite=Lax")),
        )
    }

    @Test
    fun ignoraCookiesNaoRelacionados() {
        assertNull(lerRefreshDeSetCookie(listOf("session=1; Path=/")))
        assertNull(lerRefreshDeSetCookie(emptyList()))
    }

    @Test
    fun valorVazioOuMaxAgeZeroLimpa() {
        assertEquals(MudancaRefresh.Limpo, lerRefreshDeSetCookie(listOf("refresh_token=; Path=/auth")))
        assertEquals(
            MudancaRefresh.Limpo,
            lerRefreshDeSetCookie(listOf("refresh_token=abc; Path=/auth; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT")),
        )
    }

    @Test
    fun cabecalhoIdaEVolta() {
        val cookie = RefreshCookie("refresh_token", "a=b")
        assertEquals(cookie, RefreshCookie.deCabecalho(cookie.cabecalho))
        assertNull(RefreshCookie.deCabecalho("refresh_token="))
    }
}
