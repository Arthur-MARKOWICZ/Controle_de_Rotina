package com.controlerotina.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.controlerotina.mobile.AppDependencias
import com.controlerotina.mobile.api.ApiErro
import com.controlerotina.mobile.estado.Destino
import com.controlerotina.mobile.estado.EstadoPerfil
import com.controlerotina.mobile.estado.PALAVRA_CONFIRMACAO_EXCLUSAO
import com.controlerotina.mobile.estado.PerfilViewModel
import kotlinx.coroutines.launch

@Composable
fun PerfilTela(deps: AppDependencias, modifier: Modifier) {
    val vm = deps.perfilVm
    val estado by vm.estado.collectAsState()
    var email by remember { mutableStateOf<String?>(null) }
    var saindo by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        email = try {
            deps.api.me().email
        } catch (_: ApiErro) {
            null
        }
    }
    TelaDetalhe("Perfil", onVoltar = { deps.navegacao.voltar() }, modifier = modifier) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(email ?: "…", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Tags, calendário e histórico continuam na versão web.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            OutlinedButton(onClick = { deps.navegacao.abrir(Destino.Lembretes) }, modifier = Modifier.fillMaxWidth()) {
                Text("Lembretes")
            }
            OutlinedButton(
                onClick = {
                    saindo = true
                    deps.escopo.launch { deps.sessao.sair() }
                },
                enabled = !saindo,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Sair") }

            HorizontalDivider()
            SeusDados(estado, vm)
        }
    }
    if (estado.dialogoExclusaoAberto) DialogoExcluirConta(estado, vm)
}

@Composable
private fun SeusDados(estado: EstadoPerfil, vm: PerfilViewModel) {
    Text("Seus dados", style = MaterialTheme.typography.titleMedium)
    Text(
        "Baixe uma cópia de tudo que você registrou, em JSON.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
    )
    OutlinedButton(onClick = vm::exportar, enabled = !estado.exportando, modifier = Modifier.fillMaxWidth()) {
        Text(if (estado.exportando) "Exportando…" else "Exportar meus dados")
    }
    estado.avisoExport?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    estado.erroExport?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

    Text(
        "Excluir conta",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.error,
    )
    Text(
        "Apaga a conta e todos os dados para sempre. Não dá para desfazer.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
    )
    OutlinedButton(
        onClick = vm::abrirExclusao,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Excluir conta") }
}

@Composable
private fun DialogoExcluirConta(estado: EstadoPerfil, vm: PerfilViewModel) {
    AlertDialog(
        onDismissRequest = vm::fecharExclusao,
        title = { Text("Excluir conta") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Isso apaga sua conta e todos os seus dados: tarefas, projetos, hábitos, planos, " +
                        "histórico e XP. Não dá para desfazer.",
                )
                TextButton(onClick = vm::exportar, enabled = !estado.excluindo && !estado.exportando) {
                    Text("Exportar meus dados antes")
                }
                OutlinedTextField(
                    value = estado.senha,
                    onValueChange = vm::alterarSenha,
                    label = { Text("Senha atual") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = estado.erroSenha != null,
                    supportingText = estado.erroSenha?.let { { Text(it) } },
                    enabled = !estado.excluindo,
                )
                OutlinedTextField(
                    value = estado.confirmacao,
                    onValueChange = vm::alterarConfirmacao,
                    label = { Text("Digite $PALAVRA_CONFIRMACAO_EXCLUSAO para confirmar") },
                    singleLine = true,
                    enabled = !estado.excluindo,
                )
                estado.erroExclusao?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = vm::confirmarExclusao,
                enabled = estado.exclusaoLiberada,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(if (estado.excluindo) "Excluindo…" else "Excluir definitivamente") }
        },
        dismissButton = {
            TextButton(onClick = vm::fecharExclusao, enabled = !estado.excluindo) { Text("Cancelar") }
        },
    )
}
