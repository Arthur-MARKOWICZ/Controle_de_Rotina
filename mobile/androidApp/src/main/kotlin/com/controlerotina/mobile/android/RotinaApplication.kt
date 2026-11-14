package com.controlerotina.mobile.android

import android.app.Application
import com.controlerotina.mobile.AppDependencias
import io.ktor.client.engine.okhttp.OkHttp

class RotinaApplication : Application() {
    /** Preenchida pela [MainActivity] enquanto ela existe (diálogo de permissão). */
    val permissaoNotificacao: PermissaoAndroid by lazy { PermissaoAndroid(this) }

    /** Grafo único do processo: view-models sobrevivem à rotação de tela e o worker de lembrete reusa a sessão. */
    val deps: AppDependencias by lazy {
        AppDependencias(
            engine = OkHttp.create(),
            baseUrl = BuildConfig.API_BASE_URL,
            tokenStore = KeystoreTokenStore(this),
            preferencias = DataStorePreferencias(this),
            notificador = NotificadorAndroid(this),
            agendador = AgendadorWorkManager(this),
            permissao = permissaoNotificacao,
        )
    }
}
