package com.controlerotina.mobile.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
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

    @Test
    fun salvaLeELimpaSemGuardarEmClaro() = runBlocking {
        val store = KeystoreTokenStore(context)
        val segredo = "refresh_token=valor-que-nao-pode-aparecer-em-disco"

        store.salvar(segredo)
        assertEquals(segredo, store.ler())

        val arquivos = File(context.filesDir, "datastore").listFiles().orEmpty()
        assertFalse(arquivos.any { it.readText(Charsets.ISO_8859_1).contains("valor-que-nao-pode-aparecer") })

        store.limpar()
        assertNull(store.ler())
    }
}
