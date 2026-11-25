package com.controlerotina.mobile.android

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Precisa de aparelho/emulador: escreve pelo `ContentResolver` real. O seletor do sistema
 * (`CreateDocument`) é trocado por um destino fixo, já que ele exige interação na tela.
 */
@RunWith(AndroidJUnit4::class)
class SalvadorArquivoAndroidTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val arquivo = File(context.cacheDir, "export-teste.json")
    private val salvador = SalvadorArquivoAndroid(context)

    @After
    fun limpar() {
        arquivo.delete()
        salvador.escolherDestino = null
    }

    @Test
    fun deveGravarOConteudoNoDestinoEscolhido() = runBlocking {
        var pedido: Pair<String, String>? = null
        salvador.escolherDestino = { nome, mime ->
            pedido = nome to mime
            Uri.fromFile(arquivo)
        }
        val conteudo = """{"versao":1}""".encodeToByteArray()

        val salvo = salvador.salvar("controle-rotina-export-2026-11-25.json", "application/json", conteudo)

        assertTrue(salvo)
        assertEquals("controle-rotina-export-2026-11-25.json" to "application/json", pedido)
        assertArrayEquals(conteudo, arquivo.readBytes())
    }

    @Test
    fun usuarioCancelaOSeletorNadaEGravado() = runBlocking {
        salvador.escolherDestino = { _, _ -> null }

        val salvo = salvador.salvar("x.json", "application/json", byteArrayOf(1))

        assertFalse(salvo)
        assertFalse(arquivo.exists())
    }

    @Test
    fun semActivityNaoAbreSeletorNemGrava() = runBlocking {
        val salvo = salvador.salvar("x.json", "application/json", byteArrayOf(1))

        assertFalse(salvo)
        assertFalse(arquivo.exists())
    }

    @Test
    fun destinoInvalidoLancaErroDeEscrita() {
        salvador.escolherDestino = { _, _ -> Uri.parse("content://autoridade.inexistente/arquivo") }

        assertThrows(Exception::class.java) {
            runBlocking { salvador.salvar("x.json", "application/json", byteArrayOf(1)) }
        }
    }
}
