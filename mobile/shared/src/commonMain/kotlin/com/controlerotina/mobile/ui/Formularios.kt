package com.controlerotina.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.controlerotina.mobile.api.Prioridade
import com.controlerotina.mobile.api.Projeto
import com.controlerotina.mobile.dominio.prazoFimDoDia
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Tela empilhada sobre a aba (design D5): barra superior com voltar e o voltar do sistema.
 * [onVoltar] decide se fecha (ex.: pergunta antes de descartar alterações).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
internal fun TelaDetalhe(
    titulo: String,
    onVoltar: () -> Unit,
    modifier: Modifier = Modifier,
    acoes: @Composable () -> Unit = {},
    conteudo: @Composable () -> Unit,
) {
    BackHandler(onBack = onVoltar)
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(titulo, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = onVoltar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
            },
            actions = { acoes() },
        )
        Box(Modifier.fillMaxSize()) { conteudo() }
    }
}

@Composable
internal fun Confirmar(
    titulo: String,
    texto: String,
    confirmar: String,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit,
    cancelar: String = "Voltar",
) {
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(titulo) },
        text = { Text(texto) },
        confirmButton = { TextButton(onClick = onConfirmar) { Text(confirmar) } },
        dismissButton = { TextButton(onClick = onCancelar) { Text(cancelar) } },
    )
}

/** `ISO UTC` do fim do dia escolhido + rótulo `dd/mm`. O DatePicker devolve meia-noite UTC. */
internal fun prazoDoPicker(millis: Long): Pair<String, String> {
    val data: LocalDate = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date
    val rotulo = "${data.day.toString().padStart(2, '0')}/${data.month.number.toString().padStart(2, '0')}"
    return prazoFimDoDia(data) to rotulo
}

/** Botão que abre o calendário. [onLimpar] nulo esconde o "remover". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CampoData(
    rotulo: String?,
    vazio: String,
    prefixo: String,
    onEscolher: (iso: String, rotulo: String) -> Unit,
    onLimpar: (() -> Unit)?,
) {
    var aberto by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { aberto = true }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.DateRange, null, Modifier.size(18.dp))
            Text("  " + (rotulo?.let { "$prefixo: $it" } ?: vazio))
        }
        if (rotulo != null && onLimpar != null) {
            IconButton(onClick = onLimpar) { Icon(Icons.Filled.Close, "Remover $prefixo".lowercase()) }
        }
    }
    if (aberto) {
        val estado = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { aberto = false },
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedDateMillis?.let { val (iso, r) = prazoDoPicker(it); onEscolher(iso, r) }
                    aberto = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { aberto = false }) { Text("Cancelar") } },
        ) { DatePicker(estado) }
    }
}

internal fun rotuloPrioridade(p: Prioridade) = when (p) {
    Prioridade.ALTA -> "Alta"
    Prioridade.MEDIA -> "Média"
    Prioridade.BAIXA -> "Baixa"
}

/** Chips de prioridade; [permiteNenhuma] mostra "Padrão" (a API escolhe). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SeletorPrioridade(valor: Prioridade?, permiteNenhuma: Boolean, onEscolher: (Prioridade?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Prioridade", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (permiteNenhuma) FilterChip(selected = valor == null, onClick = { onEscolher(null) }, label = { Text("Padrão") })
            Prioridade.entries.forEach { p ->
                FilterChip(selected = valor == p, onClick = { onEscolher(p) }, label = { Text(rotuloPrioridade(p)) })
            }
        }
    }
}

/** Projetos ativos + "Sem projeto" (spec `mobile-projetos`). */
@Composable
internal fun SeletorProjeto(projetoId: String?, ativos: List<Projeto>, nomeAtual: String?, onEscolher: (String?) -> Unit) {
    var aberto by remember { mutableStateOf(false) }
    val nome = ativos.firstOrNull { it.id == projetoId }?.nome ?: nomeAtual.takeIf { projetoId != null }
    Box {
        OutlinedButton(onClick = { aberto = true }, modifier = Modifier.fillMaxWidth()) {
            Text(nome?.let { "Projeto: $it" } ?: "Sem projeto", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Filled.ArrowDropDown, null)
        }
        DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
            DropdownMenuItem(text = { Text("Sem projeto") }, onClick = { onEscolher(null); aberto = false })
            ativos.forEach { p ->
                DropdownMenuItem(text = { Text(p.nome) }, onClick = { onEscolher(p.id); aberto = false })
            }
        }
    }
}
