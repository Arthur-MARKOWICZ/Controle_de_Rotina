package com.controlerotina.mobile.android

import android.content.Context
import android.net.Uri
import com.controlerotina.mobile.arquivo.SalvadorArquivo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Salva pelo seletor do sistema (Storage Access Framework, sem `FileProvider`).
 * A [MainActivity] preenche [escolherDestino] enquanto existe; sem Activity, nada é salvo.
 */
class SalvadorArquivoAndroid(private val context: Context) : SalvadorArquivo {
    var escolherDestino: (suspend (nomeSugerido: String, mime: String) -> Uri?)? = null

    override suspend fun salvar(nomeSugerido: String, mime: String, conteudo: ByteArray): Boolean {
        val escolher = escolherDestino ?: return false
        val destino = escolher(nomeSugerido, mime) ?: return false
        withContext(Dispatchers.IO) {
            val saida = context.contentResolver.openOutputStream(destino)
                ?: throw IOException("Não foi possível abrir o arquivo escolhido")
            saida.use { it.write(conteudo) }
        }
        return true
    }
}
