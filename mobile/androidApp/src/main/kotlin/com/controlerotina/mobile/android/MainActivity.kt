package com.controlerotina.mobile.android

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.controlerotina.mobile.lembrete.abaDoDestino
import com.controlerotina.mobile.lembrete.destinoDoExtra
import com.controlerotina.mobile.ui.App
import kotlinx.coroutines.CompletableDeferred

class MainActivity : ComponentActivity() {
    private val app get() = application as RotinaApplication
    private val deps get() = app.deps
    private var primeiroResume = true

    private var respostaPermissao: CompletableDeferred<Boolean>? = null
    private val pedirPermissao = registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedida ->
        respostaPermissao?.complete(concedida)
        respostaPermissao = null
    }

    @SuppressLint("InlinedApi") // o solicitante só é chamado no Android 13+ (PermissaoAndroid.solicitar)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        app.permissaoNotificacao.solicitante = {
            val resposta = CompletableDeferred<Boolean>()
            respostaPermissao = resposta
            pedirPermissao.launch(Manifest.permission.POST_NOTIFICATIONS)
            resposta.await()
        }
        // Na recriação (rotação) o intent é o mesmo: não reabre a aba da notificação.
        if (savedInstanceState == null) abrirDestinoDaNotificacao(intent)
        setContent { App(deps) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        abrirDestinoDaNotificacao(intent)
    }

    override fun onResume() {
        super.onResume()
        // Ao voltar do segundo plano: recarrega plano, execuções e sessão ativa (spec mobile-hoje).
        if (primeiroResume) primeiroResume = false else deps.aoVoltarAoPrimeiroPlano()
    }

    override fun onDestroy() {
        respostaPermissao?.complete(false)
        respostaPermissao = null
        app.permissaoNotificacao.solicitante = null
        super.onDestroy()
    }

    /** Toque na notificação de lembrete: Plano para o resumo, Tarefas para o prazo (spec mobile-lembretes). */
    private fun abrirDestinoDaNotificacao(intent: Intent?) {
        val destino = destinoDoExtra(intent?.getStringExtra(EXTRA_DESTINO)) ?: return
        intent?.removeExtra(EXTRA_DESTINO)
        deps.navegacao.selecionar(abaDoDestino(destino))
    }
}
