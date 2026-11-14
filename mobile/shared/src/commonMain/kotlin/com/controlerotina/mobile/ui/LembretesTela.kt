package com.controlerotina.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.unit.dp
import com.controlerotina.mobile.AppDependencias
import com.controlerotina.mobile.dominio.TipoLembrete
import com.controlerotina.mobile.dominio.formatarHorario
import com.controlerotina.mobile.lembrete.EstadoLembrete
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime

private fun TipoLembrete.titulo() = when (this) {
    TipoLembrete.RESUMO -> "Resumo do dia"
    TipoLembrete.PRAZO -> "Prazo amanhã"
}

private fun TipoLembrete.descricao() = when (this) {
    TipoLembrete.RESUMO -> "Como está o plano de hoje e qual o próximo bloco."
    TipoLembrete.PRAZO -> "Tarefas abertas que vencem no dia seguinte."
}

/** Perfil > Lembretes (spec mobile-lembretes). */
@Composable
fun LembretesTela(deps: AppDependencias, modifier: Modifier) {
    val vm = deps.lembretesVm
    val estado by vm.estado.collectAsState()
    val retornos by deps.retornosAoPrimeiroPlano.collectAsState()
    // Recarrega ao voltar das configurações do sistema (a permissão pode ter mudado).
    LaunchedEffect(retornos) { vm.carregar() }

    var explicarPara by remember { mutableStateOf<TipoLembrete?>(null) }
    var horarioPara by remember { mutableStateOf<TipoLembrete?>(null) }

    TelaDetalhe("Lembretes", onVoltar = { deps.navegacao.voltar() }, modifier = modifier) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "No máximo um aviso de cada tipo por dia, e só quando há algo a avisar. Sem internet no horário, o aviso do dia é pulado.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            if (estado.bloqueado) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Notificações bloqueadas", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "O Android está bloqueando as notificações do Rotina. Libere nas configurações para receber os lembretes.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedButton(onClick = vm::abrirConfiguracoes) { Text("Abrir configurações") }
                    }
                }
            }
            TipoLembrete.entries.forEach { tipo ->
                CartaoLembrete(
                    tipo = tipo,
                    estado = estado[tipo],
                    onAlternar = { ligar ->
                        when {
                            !ligar -> deps.escopo.launch { vm.desligar(tipo) }
                            vm.precisaPedirPermissao() -> explicarPara = tipo
                            else -> deps.escopo.launch { vm.ligar(tipo) }
                        }
                    },
                    onHorario = { horarioPara = tipo },
                )
            }
        }
    }

    explicarPara?.let { tipo ->
        Confirmar(
            titulo = "Permitir notificações?",
            texto = "Para avisar no horário que você escolheu, o Rotina precisa poder mostrar notificações. Você pode desligar quando quiser.",
            confirmar = "Continuar",
            cancelar = "Agora não",
            onConfirmar = {
                explicarPara = null
                deps.escopo.launch { vm.ligar(tipo) }
            },
            onCancelar = { explicarPara = null },
        )
    }

    horarioPara?.let { tipo ->
        DialogoHorario(
            inicial = estado[tipo].horario,
            onEscolher = { h ->
                horarioPara = null
                deps.escopo.launch { vm.mudarHorario(tipo, h) }
            },
            onCancelar = { horarioPara = null },
        )
    }
}

@Composable
private fun CartaoLembrete(
    tipo: TipoLembrete,
    estado: EstadoLembrete,
    onAlternar: (Boolean) -> Unit,
    onHorario: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tipo.titulo(), style = MaterialTheme.typography.titleSmall)
                    Text(tipo.descricao(), style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = estado.ligado,
                    onCheckedChange = onAlternar,
                    modifier = Modifier.semantics { contentDescription = "${tipo.titulo()}: ${if (estado.ligado) "ligado" else "desligado"}" },
                )
            }
            OutlinedButton(onClick = onHorario) { Text("Horário: ${formatarHorario(estado.horario)}") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogoHorario(inicial: LocalTime, onEscolher: (LocalTime) -> Unit, onCancelar: () -> Unit) {
    val picker = rememberTimePickerState(initialHour = inicial.hour, initialMinute = inicial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("Horário do lembrete") },
        text = { TimePicker(picker) },
        confirmButton = { TextButton(onClick = { onEscolher(LocalTime(picker.hour, picker.minute)) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } },
    )
}
