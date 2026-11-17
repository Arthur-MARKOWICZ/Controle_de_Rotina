package com.controlerotina.mobile.api

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MetadadosPaginaTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun deveLerPaginaNoFormatoPagedModel() {
        val pagina = json.decodeFromString<Pagina<Projeto>>(
            """{"content":[],"page":{"size":20,"number":1,"totalElements":45,"totalPages":3}}""",
        )
        assertEquals(MetadadosPagina(size = 20, number = 1, totalElements = 45, totalPages = 3), pagina.page)
        assertFalse(pagina.ultima)
    }

    @Test
    fun deveSerUltimaNaPaginaFinal() {
        assertTrue(MetadadosPagina(size = 20, number = 2, totalElements = 45, totalPages = 3).ultima)
    }

    @Test
    fun deveSerUltimaQuandoListaVazia() {
        assertTrue(MetadadosPagina(size = 20, number = 0, totalElements = 0, totalPages = 0).ultima)
    }

    @Test
    fun deveSerUltimaQuandoPaginaPassaDoFim() {
        assertTrue(MetadadosPagina(size = 20, number = 5, totalElements = 3, totalPages = 1).ultima)
    }

    @Test
    fun deveIgnorarCamposAntigosNaRaizSemQuebrar() {
        val pagina = json.decodeFromString<PaginaTarefas>(
            """{"content":[],"totalElements":9,"last":false,"page":{"size":20,"number":0,"totalElements":0,"totalPages":0}}""",
        )
        assertEquals(0L, pagina.totalElements)
        assertTrue(pagina.ultima)
    }

    @Test
    fun deveFalharQuandoPageTemTipoInvalido() {
        assertFailsWith<SerializationException> {
            json.decodeFromString<PaginaTarefas>("""{"content":[],"page":{"number":"um"}}""")
        }
    }

    @Test
    fun deveFalharQuandoContentNaoELista() {
        assertFailsWith<SerializationException> {
            json.decodeFromString<PaginaTarefas>("""{"content":{},"page":{}}""")
        }
    }

    @Test
    fun deveFalharQuandoJsonMalformado() {
        assertFailsWith<SerializationException> {
            json.decodeFromString<PaginaTarefas>("""<!doctype html>""")
        }
    }
}
