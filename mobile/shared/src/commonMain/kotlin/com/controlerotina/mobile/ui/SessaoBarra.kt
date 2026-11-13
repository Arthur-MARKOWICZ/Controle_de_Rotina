package com.controlerotina.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.controlerotina.mobile.dominio.formatarMinutos
import com.controlerotina.mobile.dominio.minutosExcedidos
import com.controlerotina.mobile.dominio.minutosRestantes
import com.controlerotina.mobile.estado.SessaoAtiva
import com.controlerotina.mobile.estado.SessaoAtivaStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock

/** Barra fixa acima da navegação enquanto há execução em andamento (spec mobile-hoje). */
@Composable
fun SessaoBarra(sessao: SessaoAtiva, store: SessaoAtivaStore) {
    val ocupado by store.ocupado.collectAsState()
    var agora by remember { mutableStateOf(Clock.System.now()) }
    var confirmarAbortar by remember { mutableStateOf(false) }
    val escopo = androidx.compose.runtime.rememberCoroutineScope()

    // Tempo sempre derivado de iniciadoEm (API), nunca de um contador local.
    LaunchedEffect(sessao.execucaoId) {
        while (true) {
            agora = Clock.System.now()
            delay(15_000)
        }
    }
    val excedido = minutosExcedidos(sessao.iniciadoEm, sessao.duracaoMinutos, agora)
    val texto = if (excedido > 0) "+${formatarMinutos(excedido)} além do previsto"
    else "${formatarMinutos(minutosRestantes(sessao.iniciadoEm, sessao.duracaoMinutos, agora))} restantes"

    Surface(color = MaterialTheme.colorScheme.primaryContainer, tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f).semantics { contentDescription = "Sessão em andamento: ${sessao.titulo}, $texto" }) {
                Text(sessao.titulo, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    texto,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (excedido > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            TextButton(onClick = { confirmarAbortar = true }, enabled = !ocupado) { Text("Abortar") }
            Button(onClick = { escopo.launch { store.concluir() } }, enabled = !ocupado) { Text("Concluir") }
        }
    }

    if (confirmarAbortar) {
        AlertDialog(
            onDismissRequest = { confirmarAbortar = false },
            title = { Text("Abortar sessão?") },
            text = { Text("O tempo desta sessão não conta. Você pode iniciar de novo depois.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmarAbortar = false
                    escopo.launch { store.abortar() }
                }) { Text("Abortar") }
            },
            dismissButton = { TextButton(onClick = { confirmarAbortar = false }) { Text("Continuar") } },
        )
    }
}
