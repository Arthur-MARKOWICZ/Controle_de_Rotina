package com.controlerotina.mobile.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Precisa de aparelho/emulador: o AndroidKeyStore não existe na JVM. */
@RunWith(AndroidJUnit4::class)
class KeystoreTokenStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = KeystoreTokenStore(context)

    @After
    fun limparStore() = runBlocking {
        store.limpar()
    }

    @Test
    fun deveSalvarLerELimparSemGuardarEmClaro() = runBlocking {
        val segredo = "refresh_token=valor-que-nao-pode-aparecer-em-disco"

        store.salvar(segredo)
        assertEquals(segredo, store.ler())

        val arquivos = File(context.filesDir, "datastore").listFiles().orEmpty()
        assertFalse(arquivos.any { it.readText(Charsets.ISO_8859_1).contains("valor-que-nao-pode-aparecer") })

        store.limpar()
        assertNull(store.ler())
    }

    @Test
    fun deveDevolverNuloQuandoNaoHaValor() = runBlocking {
        store.limpar()
        assertNull(store.ler())
    }

    @Test
    fun deveSubstituirValorAnteriorAoSalvarDeNovo() = runBlocking {
        store.salvar("refresh_token=um")
        store.salvar("refresh_token=dois")
        assertEquals("refresh_token=dois", store.ler())
        val arquivos = File(context.filesDir, "datastore").listFiles().orEmpty()
        assertFalse(arquivos.any { it.readText(Charsets.ISO_8859_1).contains("refresh_token=um") })
        assertFalse(arquivos.any { it.readText(Charsets.ISO_8859_1).contains("refresh_token=dois") })
    }

    @Test
    fun naoDeveFalharAoLimparQuandoJaVazio() = runBlocking {
        store.limpar()
        store.limpar()
        assertNull(store.ler())
    }

    @Test
    fun deveRoundTripValorComCaracteresEspeciais() = runBlocking {
        val segredo = "refresh_token=a=b; Path=/auth"
        store.salvar(segredo)
        assertEquals(segredo, store.ler())
    }

    @Test
    fun deveAceitarStringVaziaComoValorPersistido() = runBlocking {
        store.salvar("")
        assertEquals("", store.ler())
    }

    @Test
    fun deveContinuarNuloAposSalvarELimpar() = runBlocking {
        store.salvar("refresh_token=tmp")
        store.limpar()
        assertNull(store.ler())
        assertNull(store.ler())
    }
}
