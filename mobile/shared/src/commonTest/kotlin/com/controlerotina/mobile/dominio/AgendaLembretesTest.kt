package com.controlerotina.mobile.dominio

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class AgendaLembretesTest {
    private val brt = TimeZone.of("America/Sao_Paulo")
    private val oito = LocalTime(8, 0)

    private fun t(iso: String) = Instant.parse(iso)

    // ---- proximoDisparo ----

    @Test
    fun deveAgendarHojeQuandoHorarioAindaNaoPassou() {
        // 07:00 BRT
        assertEquals(t("2026-11-13T11:00:00Z"), proximoDisparo(t("2026-11-13T10:00:00Z"), oito, brt))
    }

    @Test
    fun deveAgendarAmanhaQuandoHorarioJaPassou() {
        // 09:15 BRT
        assertEquals(t("2026-11-14T11:00:00Z"), proximoDisparo(t("2026-11-13T12:15:00Z"), oito, brt))
    }

    @Test
    fun deveAgendarAmanhaQuandoAgoraEExatamenteOHorario() {
        assertEquals(t("2026-11-14T11:00:00Z"), proximoDisparo(t("2026-11-13T11:00:00Z"), oito, brt))
    }

    @Test
    fun deveVirarMesEAnoNaMeiaNoiteBrt() {
        // 31/12 23:59 BRT, horário 00:00 → 01/01 00:00 BRT
        assertEquals(
            t("2027-01-01T03:00:00Z"),
            proximoDisparo(t("2027-01-01T02:59:00Z"), LocalTime(0, 0), brt),
        )
    }

    @Test
    fun deveManterHorarioLocalNaViradaDoHorarioDeVerao() {
        val ny = TimeZone.of("America/New_York")
        // 31/10/2026 09:00 EDT (-4). Próximo 08:00 é 01/11, já em EST (-5) → 13:00Z, não 12:00Z.
        assertEquals(t("2026-11-01T13:00:00Z"), proximoDisparo(t("2026-10-31T13:00:00Z"), oito, ny))
    }

    @Test
    fun deveUsarOFusoNovoQuandoAparelhoTrocaDeFuso() {
        val lisboa = TimeZone.of("Europe/Lisbon")
        // Mesmo instante, fuso diferente → 08:00 local de Lisboa (UTC+0 em novembro).
        assertEquals(t("2026-11-14T08:00:00Z"), proximoDisparo(t("2026-11-13T12:15:00Z"), oito, lisboa))
    }

    // ---- diaDoDisparo ----

    @Test
    fun deveAceitarExecucaoComAtrasoDentroDaTolerancia() {
        // 09:59 BRT
        assertEquals(LocalDate(2026, 11, 13), diaDoDisparo(t("2026-11-13T12:59:00Z"), oito, brt))
    }

    @Test
    fun deveRecusarExecucaoComAtrasoAcimaDeDuasHoras() {
        // 10:01 BRT
        assertNull(diaDoDisparo(t("2026-11-13T13:01:00Z"), oito, brt))
    }

    @Test
    fun deveRecusarExecucaoMuitoAntesDoHorario() {
        // 07:00 BRT
        assertNull(diaDoDisparo(t("2026-11-13T10:00:00Z"), oito, brt))
    }

    @Test
    fun deveAtribuirAOntemLembreteNoturnoExecutadoDepoisDaMeiaNoite() {
        // horário 23:30, execução 00:40 BRT de 14/11 → pertence a 13/11
        assertEquals(LocalDate(2026, 11, 13), diaDoDisparo(t("2026-11-14T03:40:00Z"), LocalTime(23, 30), brt))
    }

    // ---- intervaloAmanha ----

    @Test
    fun deveCobrirAmanhaInteiroComFimInclusivoUmMsAntesDeDepoisDeAmanha() {
        val (de, ate) = intervaloAmanha(t("2026-11-13T22:00:00Z"), brt)
        assertEquals(t("2026-11-14T03:00:00Z"), de)
        assertEquals(t("2026-11-15T02:59:59.999Z"), ate)
        // Prazo às 00:00 de depois de amanhã fica fora (limite do backend é inclusivo).
        val meiaNoiteDepois = t("2026-11-15T03:00:00Z")
        assertEquals(true, meiaNoiteDepois > ate)
    }

    @Test
    fun deveCalcularAmanhaPelaDataLocalNaoUtc() {
        // 23:30 BRT de 13/11 = 02:30Z de 14/11; amanhã local ainda é 14/11.
        val (de, _) = intervaloAmanha(t("2026-11-14T02:30:00Z"), brt)
        assertEquals(t("2026-11-14T03:00:00Z"), de)
    }

    // ---- lerHorario / formatarHorario ----

    @Test
    fun deveLerEFormatarHorarioValido() {
        assertEquals(LocalTime(7, 30), lerHorario("07:30"))
        assertEquals("07:05", formatarHorario(LocalTime(7, 5)))
    }

    @Test
    fun deveRecusarHorarioInvalido() {
        assertNull(lerHorario(null))
        assertNull(lerHorario(""))
        assertNull(lerHorario("24:00"))
        assertNull(lerHorario("08:60"))
        assertNull(lerHorario("8:00"))
        assertNull(lerHorario("ab:cd"))
    }
}
