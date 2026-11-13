package com.controlerotina.mobile.android

import android.app.Application
import com.controlerotina.mobile.AppDependencias
import io.ktor.client.engine.okhttp.OkHttp

class RotinaApplication : Application() {
    /** Grafo único do processo: view-models sobrevivem à rotação de tela. */
    val deps: AppDependencias by lazy {
        AppDependencias(
            engine = OkHttp.create(),
            baseUrl = BuildConfig.API_BASE_URL,
            tokenStore = KeystoreTokenStore(this),
            preferencias = DataStorePreferencias(this),
        )
    }
}
