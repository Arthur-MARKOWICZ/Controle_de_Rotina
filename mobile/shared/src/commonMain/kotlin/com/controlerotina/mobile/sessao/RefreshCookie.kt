package com.controlerotina.mobile.sessao

/** Cookie de refresh no formato que o próximo `POST /auth/refresh` reenvia: `nome=valor`. */
data class RefreshCookie(val nome: String, val valor: String) {
    val cabecalho: String get() = "$nome=$valor"

    companion object {
        fun deCabecalho(cabecalho: String): RefreshCookie? {
            val eq = cabecalho.indexOf('=')
            if (eq <= 0) return null
            val valor = cabecalho.substring(eq + 1)
            return if (valor.isBlank()) null else RefreshCookie(cabecalho.substring(0, eq), valor)
        }
    }
}

/** O que os `Set-Cookie` de uma resposta de `/auth` dizem sobre o refresh. */
sealed interface MudancaRefresh {
    data class Novo(val cookie: RefreshCookie) : MudancaRefresh
    data object Limpo : MudancaRefresh
}

private fun ehNomeDeRefresh(nome: String): Boolean =
    nome == "refresh_token" || nome == "__Secure-refresh_token" || nome.contains("refresh", ignoreCase = true)

/**
 * Extrai o cookie de refresh dos `Set-Cookie` (mesmas regras de `mcp/src/cookies.ts`).
 * Valor vazio ou `Max-Age=0` significa que o servidor mandou apagar (logout).
 */
fun lerRefreshDeSetCookie(cabecalhos: List<String>): MudancaRefresh? {
    for (cabecalho in cabecalhos) {
        val partes = cabecalho.split(';')
        val par = partes.first()
        val eq = par.indexOf('=')
        if (eq <= 0) continue
        val nome = par.substring(0, eq).trim()
        if (!ehNomeDeRefresh(nome)) continue
        val valor = par.substring(eq + 1).trim()
        val expirado = partes.drop(1).any { atributo ->
            val (chave, v) = atributo.trim().split('=', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            chave.equals("Max-Age", ignoreCase = true) && (v.trim().toLongOrNull() ?: 1) <= 0
        }
        return if (valor.isEmpty() || expirado) MudancaRefresh.Limpo else MudancaRefresh.Novo(RefreshCookie(nome, valor))
    }
    return null
}
