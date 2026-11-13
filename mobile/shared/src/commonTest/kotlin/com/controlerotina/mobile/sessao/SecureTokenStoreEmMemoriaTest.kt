package com.controlerotina.mobile.sessao

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SecureTokenStoreEmMemoriaTest {
    @Test
    fun deveSalvarELerValor() = runTest {
        val store = SecureTokenStoreEmMemoria()
        store.salvar("refresh_token=abc")
        assertEquals("refresh_token=abc", store.ler())
    }

    @Test
    fun deveDevolverNuloQuandoVazio() = runTest {
        assertNull(SecureTokenStoreEmMemoria().ler())
    }

    @Test
    fun deveCarregarValorInicial() = runTest {
        val store = SecureTokenStoreEmMemoria("refresh_token=inicial")
        assertEquals("refresh_token=inicial", store.ler())
    }

    @Test
    fun deveSubstituirValorAnteriorAoSalvarDeNovo() = runTest {
        val store = SecureTokenStoreEmMemoria("refresh_token=um")
        store.salvar("refresh_token=dois")
        assertEquals("refresh_token=dois", store.ler())
    }

    @Test
    fun deveLimparValorPersistido() = runTest {
        val store = SecureTokenStoreEmMemoria("refresh_token=abc")
        store.limpar()
        assertNull(store.ler())
    }

    @Test
    fun naoDeveAlterarEstadoAoLimparQuandoJaVazio() = runTest {
        val store = SecureTokenStoreEmMemoria()
        store.limpar()
        store.limpar()
        assertNull(store.ler())
    }

    @Test
    fun deveAceitarStringVaziaComoValorPersistido() = runTest {
        val store = SecureTokenStoreEmMemoria()
        store.salvar("")
        assertEquals("", store.ler())
    }
}

class PreferenciasEmMemoriaTest {
    @Test
    fun deveSalvarELerChave() = runTest {
        val prefs = PreferenciasEmMemoria()
        prefs.salvar("sessao", """{"id":"e1"}""")
        assertEquals("""{"id":"e1"}""", prefs.ler("sessao"))
    }

    @Test
    fun deveDevolverNuloQuandoChaveAusente() = runTest {
        assertNull(PreferenciasEmMemoria().ler("inexistente"))
    }

    @Test
    fun deveRemoverChaveQuandoValorNulo() = runTest {
        val prefs = PreferenciasEmMemoria()
        prefs.salvar("sessao", "x")
        prefs.salvar("sessao", null)
        assertNull(prefs.ler("sessao"))
    }

    @Test
    fun naoDeveApagarOutraChaveAoRemoverUma() = runTest {
        val prefs = PreferenciasEmMemoria()
        prefs.salvar("a", "1")
        prefs.salvar("b", "2")
        prefs.salvar("a", null)
        assertNull(prefs.ler("a"))
        assertEquals("2", prefs.ler("b"))
    }

    @Test
    fun deveSubstituirValorDaMesmaChave() = runTest {
        val prefs = PreferenciasEmMemoria()
        prefs.salvar("k", "1")
        prefs.salvar("k", "2")
        assertEquals("2", prefs.ler("k"))
    }

    @Test
    fun naoDeveCriarChaveAoSalvarNuloEmAusente() = runTest {
        val prefs = PreferenciasEmMemoria()
        prefs.salvar("k", null)
        assertNull(prefs.ler("k"))
    }

    @Test
    fun deveIsolarChavesComNomesDiferentes() = runTest {
        val prefs = PreferenciasEmMemoria()
        prefs.salvar("k", "")
        assertEquals("", prefs.ler("k"))
        assertNull(prefs.ler("K"))
    }
}
