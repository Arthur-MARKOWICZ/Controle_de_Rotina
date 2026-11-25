package com.controlerotina.mobile.arquivo

/** Salva um arquivo escolhido pelo usuário (no Android, seletor do sistema). Implementado por plataforma. */
interface SalvadorArquivo {
    /** `true` se salvou; `false` se o usuário cancelou a escolha do destino. Falha de escrita lança. */
    suspend fun salvar(nomeSugerido: String, mime: String, conteudo: ByteArray): Boolean
}

/** Plataforma sem como salvar arquivo (testes, JVM): sempre "cancelado". */
object SemSalvadorArquivo : SalvadorArquivo {
    override suspend fun salvar(nomeSugerido: String, mime: String, conteudo: ByteArray) = false
}
