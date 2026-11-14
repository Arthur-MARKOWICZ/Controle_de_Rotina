package com.controlerotina.mobile.lembrete

import com.controlerotina.mobile.dominio.DestinoLembrete
import com.controlerotina.mobile.estado.Aba
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class RegrasPlataformaTest {

    // ---- notificacaoPermitida ----

    @Test
    fun devePermitirNoAndroid13ComPermissaoENotificacoesAtivas() {
        assertTrue(notificacaoPermitida(sdk = 34, permissaoConcedida = true, notificacoesAtivas = true))
    }

    @Test
    fun devePermitirNoAndroid12SemPermissaoDeTempoDeExecucao() {
        assertTrue(notificacaoPermitida(sdk = 31, permissaoConcedida = false, notificacoesAtivas = true))
    }

    @Test
    fun naoDevePermitirNoAndroid13SemPermissao() {
        assertFalse(notificacaoPermitida(sdk = 33, permissaoConcedida = false, notificacoesAtivas = true))
    }

    @Test
    fun naoDevePermitirQuandoUsuarioBloqueouNasConfiguracoesMesmoComPermissao() {
        assertFalse(notificacaoPermitida(sdk = 34, permissaoConcedida = true, notificacoesAtivas = false))
        assertFalse(notificacaoPermitida(sdk = 31, permissaoConcedida = false, notificacoesAtivas = false))
    }

    // ---- precisaDialogoDePermissao ----

    @Test
    fun devePedirDialogoNoAndroid13SemPermissao() {
        assertTrue(precisaDialogoDePermissao(sdk = 33, permissaoConcedida = false))
    }

    @Test
    fun naoDevePedirDialogoNoAndroid12OuComPermissaoJaConcedida() {
        assertFalse(precisaDialogoDePermissao(sdk = 32, permissaoConcedida = false))
        assertFalse(precisaDialogoDePermissao(sdk = 35, permissaoConcedida = true))
    }

    // ---- atrasoAte ----

    @Test
    fun deveCalcularAtrasoAteOHorario() {
        val agora = Instant.parse("2026-11-14T10:00:00Z")
        assertEquals(60.minutes, atrasoAte(Instant.parse("2026-11-14T11:00:00Z"), agora))
    }

    @Test
    fun atrasoDeHorarioJaPassadoDeveSerZero() {
        val agora = Instant.parse("2026-11-14T10:00:00Z")
        assertEquals(Duration.ZERO, atrasoAte(Instant.parse("2026-11-14T09:59:00Z"), agora))
        assertEquals(Duration.ZERO, atrasoAte(agora, agora))
    }

    // ---- destino ----

    @Test
    fun deveMapearExtraDaNotificacaoParaAAbaCerta() {
        assertEquals(Aba.PLANO, abaDoDestino(destinoDoExtra("PLANO")!!))
        assertEquals(Aba.TAREFAS, abaDoDestino(destinoDoExtra("TAREFAS")!!))
    }

    @Test
    fun deveIgnorarExtraAusenteDesconhecidoOuComCaixaDiferente() {
        assertNull(destinoDoExtra(null))
        assertNull(destinoDoExtra(""))
        assertNull(destinoDoExtra("HABITOS"))
        assertNull(destinoDoExtra("plano"))
        assertEquals(DestinoLembrete.PLANO, destinoDoExtra("PLANO"))
    }
}
