package com.controlerotina.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.controlerotina.mobile.AppDependencias
import com.controlerotina.mobile.estado.EstadoApp
import com.controlerotina.mobile.estado.MSG_SEM_CONEXAO
import kotlinx.coroutines.launch

@Composable
fun App(deps: AppDependencias) {
    TemaRotina {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val estado by deps.sessao.estado.collectAsState()
            LaunchedEffect(Unit) {
                if (deps.sessao.estado.value == EstadoApp.Verificando) deps.sessao.iniciar()
            }
            when (val e = estado) {
                EstadoApp.Verificando -> Centro { CircularProgressIndicator() }
                EstadoApp.SemConexaoNoBoot -> Centro {
                    Text(MSG_SEM_CONEXAO, textAlign = TextAlign.Center)
                    Button(onClick = { deps.escopo.launch { deps.sessao.iniciar() } }) { Text("Tentar de novo") }
                }
                is EstadoApp.Login -> LoginTela(deps.sessao, e.aviso)
                EstadoApp.Autenticado -> Shell(deps)
            }
        }
    }
}

@Composable
internal fun Centro(conteudo: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { conteudo() }
}
