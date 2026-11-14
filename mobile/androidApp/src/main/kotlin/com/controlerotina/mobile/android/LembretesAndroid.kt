package com.controlerotina.mobile.android

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.controlerotina.mobile.dominio.Notificacao
import com.controlerotina.mobile.dominio.TipoLembrete
import com.controlerotina.mobile.lembrete.AgendadorLembretes
import com.controlerotina.mobile.lembrete.Notificador
import com.controlerotina.mobile.lembrete.PermissaoNotificacao
import com.controlerotina.mobile.lembrete.atrasoAte
import com.controlerotina.mobile.lembrete.notificacaoPermitida
import com.controlerotina.mobile.lembrete.precisaDialogoDePermissao
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlin.time.Instant

// Casca Android dos lembretes locais (design L3, L6). A regra fica toda em `shared`.

private const val TAG_LEMBRETE = "lembrete"
private const val CHAVE_TIPO = "tipo"
private const val CANAL = "lembretes"

/** Extra da notificação: aba a abrir (`PLANO`/`TAREFAS`). */
const val EXTRA_DESTINO = "com.controlerotina.mobile.DESTINO"

private fun TipoLembrete.idNotificacao() = when (this) {
    TipoLembrete.RESUMO -> 1001
    TipoLembrete.PRAZO -> 1002
}

/**
 * Executa um lembrete no horário. Usa o grafo do processo (design L2): mesmo `ApiHttp`,
 * mesmo Mutex de renovação da UI, então nunca reusa um refresh já rotacionado.
 * Sempre `success`: falhas são puladas pelo executor, e ele mesmo agenda o próximo dia.
 */
class LembreteWorker(contexto: Context, parametros: WorkerParameters) : CoroutineWorker(contexto, parametros) {
    override suspend fun doWork(): Result {
        val tipo = inputData.getString(CHAVE_TIPO)?.let { nome -> TipoLembrete.entries.firstOrNull { it.name == nome } }
            ?: return Result.success()
        (applicationContext as RotinaApplication).deps.executorLembrete.executar(tipo)
        return Result.success()
    }
}

/** Um trabalho único por tipo; agendar de novo substitui (inclusive o próprio worker ao reencadear). */
class AgendadorWorkManager(contexto: Context, private val clock: Clock = Clock.System) : AgendadorLembretes {
    private val workManager = WorkManager.getInstance(contexto.applicationContext)

    private fun nome(tipo: TipoLembrete) = "lembrete-${tipo.chave}"

    override fun agendar(tipo: TipoLembrete, em: Instant) {
        val atraso = atrasoAte(em, clock.now()).inWholeMilliseconds
        val pedido = OneTimeWorkRequestBuilder<LembreteWorker>()
            .setInitialDelay(atraso, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(CHAVE_TIPO to tipo.name))
            .addTag(TAG_LEMBRETE)
            .build()
        workManager.enqueueUniqueWork(nome(tipo), ExistingWorkPolicy.REPLACE, pedido)
    }

    override fun cancelar(tipo: TipoLembrete) {
        workManager.cancelUniqueWork(nome(tipo))
    }

    override fun cancelarTodos() {
        workManager.cancelAllWorkByTag(TAG_LEMBRETE)
    }
}

/** Canal único, IDs fixos por tipo e texto genérico na tela bloqueada (spec "Privacidade"). */
class NotificadorAndroid(contexto: Context) : Notificador {
    private val contexto = contexto.applicationContext
    private val gerenciador = NotificationManagerCompat.from(this.contexto)

    init {
        val canal = NotificationChannel(CANAL, "Lembretes do plano", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Resumo do dia e tarefas que vencem amanhã"
            lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
        }
        this.contexto.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }

    override fun permitido(): Boolean = notificacaoAtivaNoSistema(contexto)

    @SuppressLint("MissingPermission") // checado em permitido()
    override fun exibir(notificacao: Notificacao) {
        if (!permitido()) return
        val abrir = Intent(contexto, MainActivity::class.java)
            .putExtra(EXTRA_DESTINO, notificacao.destino.name)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val id = notificacao.tipo.idNotificacao()
        val toque = PendingIntent.getActivity(
            contexto,
            id,
            abrir,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val publica = NotificationCompat.Builder(contexto, CANAL)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setContentTitle("Rotina")
            .setContentText("Você tem um lembrete do Rotina")
            .build()
        val completa = NotificationCompat.Builder(contexto, CANAL)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setContentTitle(notificacao.titulo)
            .setContentText(notificacao.texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificacao.texto))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publica)
            .setContentIntent(toque)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        runCatching { gerenciador.notify(id, completa) } // SecurityException se revogada no meio
    }

    override fun limparTodas() {
        TipoLembrete.entries.forEach { gerenciador.cancel(it.idNotificacao()) }
    }
}

@SuppressLint("InlinedApi") // abaixo do 13 a regra ignora a permissão de tempo de execução
private fun permissaoRuntimeConcedida(contexto: Context): Boolean =
    ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun notificacaoAtivaNoSistema(contexto: Context): Boolean = notificacaoPermitida(
    sdk = Build.VERSION.SDK_INT,
    permissaoConcedida = permissaoRuntimeConcedida(contexto),
    notificacoesAtivas = NotificationManagerCompat.from(contexto).areNotificationsEnabled(),
)

/**
 * Permissão de notificação. O diálogo do sistema precisa de uma Activity: a [MainActivity]
 * preenche [solicitante] enquanto está viva. Abaixo do Android 13 não há diálogo.
 */
class PermissaoAndroid(contexto: Context) : PermissaoNotificacao {
    private val contexto = contexto.applicationContext

    /** Abre o diálogo do sistema e devolve a resposta; `null` sem Activity em primeiro plano. */
    var solicitante: (suspend () -> Boolean)? = null

    override fun concedida(): Boolean = notificacaoAtivaNoSistema(contexto)

    override suspend fun solicitar(): Boolean {
        if (!precisaDialogoDePermissao(Build.VERSION.SDK_INT, permissaoRuntimeConcedida(contexto))) return concedida()
        return (solicitante?.invoke() ?: false) && concedida()
    }

    override fun abrirConfiguracoes() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, contexto.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        contexto.startActivity(intent)
    }
}
