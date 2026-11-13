package com.controlerotina.mobile.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.controlerotina.mobile.sessao.PreferenciasLocais
import com.controlerotina.mobile.sessao.SecureTokenStore
import kotlinx.coroutines.flow.first
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.sessaoDataStore: DataStore<Preferences> by preferencesDataStore(name = "sessao")
private val Context.preferenciasDataStore: DataStore<Preferences> by preferencesDataStore(name = "preferencias")

/**
 * Refresh cifrado com AES-GCM; a chave nasce e fica no Android Keystore, nunca sai do aparelho.
 * O DataStore só vê `iv + texto cifrado` em Base64 (design D3). EncryptedSharedPreferences
 * está deprecated e não é usado.
 */
class KeystoreTokenStore(context: Context) : SecureTokenStore {
    private val dataStore = context.applicationContext.sessaoDataStore

    override suspend fun ler(): String? {
        val cifrado = dataStore.data.first()[CHAVE] ?: return null
        // Chave do Keystore apagada/invalidada (ex.: restauração do aparelho): trata como sem sessão.
        return runCatching { decifrar(cifrado) }.getOrElse {
            limpar()
            null
        }
    }

    override suspend fun salvar(valor: String) {
        val cifrado = cifrar(valor)
        dataStore.edit { it[CHAVE] = cifrado }
    }

    override suspend fun limpar() {
        dataStore.edit { it.remove(CHAVE) }
    }

    private fun chaveSecreta(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gerador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        gerador.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gerador.generateKey()
    }

    private fun cifrar(texto: String): String {
        val cipher = Cipher.getInstance(TRANSFORMACAO)
        cipher.init(Cipher.ENCRYPT_MODE, chaveSecreta())
        val iv = cipher.iv
        val corpo = cipher.doFinal(texto.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + corpo, Base64.NO_WRAP)
    }

    private fun decifrar(base64: String): String {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMACAO)
        cipher.init(Cipher.DECRYPT_MODE, chaveSecreta(), GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "controle_rotina_refresh"
        const val TRANSFORMACAO = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        val CHAVE = stringPreferencesKey("refresh_cifrado")
    }
}

/** Valores não sensíveis (ex.: resumo da sessão avulsa em andamento). */
class DataStorePreferencias(context: Context) : PreferenciasLocais {
    private val dataStore = context.applicationContext.preferenciasDataStore

    override suspend fun ler(chave: String): String? = dataStore.data.first()[stringPreferencesKey(chave)]

    override suspend fun salvar(chave: String, valor: String?) {
        dataStore.edit {
            val k = stringPreferencesKey(chave)
            if (valor == null) it.remove(k) else it[k] = valor
        }
    }
}
