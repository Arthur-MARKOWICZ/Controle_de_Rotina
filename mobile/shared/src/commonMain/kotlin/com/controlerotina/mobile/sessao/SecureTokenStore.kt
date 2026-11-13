package com.controlerotina.mobile.sessao

/**
 * Onde o refresh fica guardado entre aberturas do app. No Android, cifrado com chave do
 * Keystore (design D3). O valor é o cabeçalho `nome=valor` pronto para reenviar.
 */
interface SecureTokenStore {
    suspend fun ler(): String?
    suspend fun salvar(valor: String)
    suspend fun limpar()
}

/** Guarda pequenos valores não sensíveis entre aberturas (ex.: sessão avulsa em andamento). */
interface PreferenciasLocais {
    suspend fun ler(chave: String): String?
    suspend fun salvar(chave: String, valor: String?)
}

class SecureTokenStoreEmMemoria(private var valor: String? = null) : SecureTokenStore {
    override suspend fun ler(): String? = valor
    override suspend fun salvar(valor: String) {
        this.valor = valor
    }
    override suspend fun limpar() {
        valor = null
    }
}

class PreferenciasEmMemoria : PreferenciasLocais {
    private val mapa = mutableMapOf<String, String>()
    override suspend fun ler(chave: String): String? = mapa[chave]
    override suspend fun salvar(chave: String, valor: String?) {
        if (valor == null) mapa.remove(chave) else mapa[chave] = valor
    }
}
