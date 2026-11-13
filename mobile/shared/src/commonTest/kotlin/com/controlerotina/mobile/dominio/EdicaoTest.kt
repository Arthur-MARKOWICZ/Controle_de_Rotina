package com.controlerotina.mobile.dominio

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class EdicaoTest {
    private val original = mapOf(
        "titulo" to JsonPrimitive("Estudar"),
        "prazo" to JsonPrimitive("2026-11-20T02:59:00Z"),
        "projetoId" to JsonNull,
    )

    @Test
    fun semMudancaGeraCorpoVazio() {
        assertTrue(diffPatch(original, original).isEmpty())
    }

    @Test
    fun soOTituloMudou() {
        val corpo = diffPatch(original, original + ("titulo" to JsonPrimitive("Estudar Kotlin")))
        assertEquals(setOf("titulo"), corpo.keys)
        assertEquals(JsonPrimitive("Estudar Kotlin"), corpo["titulo"])
    }

    @Test
    fun limparViraNullExplicito() {
        val corpo = diffPatch(original, original + ("prazo" to JsonNull))
        assertEquals(JsonNull, corpo["prazo"])
        assertEquals("""{"prazo":null}""", corpo.toString())
    }

    @Test
    fun trocarProjeto() {
        val corpo = diffPatch(original, original + ("projetoId" to JsonPrimitive("p1")))
        assertEquals(mapOf("projetoId" to JsonPrimitive("p1")), corpo.toMap())
    }

    @Test
    fun campoNovoAusenteNoOriginalContaComoNull() {
        assertTrue(diffPatch(original, original + ("descricao" to JsonNull)).isEmpty())
        assertEquals(setOf("descricao"), diffPatch(original, original + ("descricao" to JsonPrimitive("x"))).keys)
    }

    @Test
    fun decidirVencidoSoComDataNoPassado() {
        val agora = Instant.parse("2026-11-12T12:00:00Z")
        assertFalse(decidirVencido(null, agora))
        assertTrue(decidirVencido("2026-11-11T23:59:00Z", agora))
        assertFalse(decidirVencido("2026-11-12T23:59:00Z", agora))
        assertFalse(decidirVencido("lixo", agora))
    }
}
