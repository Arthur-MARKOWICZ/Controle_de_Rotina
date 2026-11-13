package com.controlerotina.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.controlerotina.mobile.AppDependencias
import com.controlerotina.mobile.api.ApiErro
import kotlinx.coroutines.launch

@Composable
fun PerfilTela(deps: AppDependencias, modifier: Modifier) {
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
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(email ?: "…", style = MaterialTheme.typography.bodyLarge)
        Text(
            "Tags, calendário e histórico continuam na versão web.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        OutlinedButton(
            onClick = {
                saindo = true
                deps.escopo.launch { deps.sessao.sair() }
            },
            enabled = !saindo,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Sair") }
    }
    }
}
