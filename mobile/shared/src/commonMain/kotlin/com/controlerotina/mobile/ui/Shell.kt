package com.controlerotina.mobile.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.controlerotina.mobile.AppDependencias
import com.controlerotina.mobile.dominio.hojeIso
import com.controlerotina.mobile.estado.Aba
import com.controlerotina.mobile.estado.Destino
import kotlinx.coroutines.delay

private val Aba.icone: ImageVector
    get() = when (this) {
        Aba.TAREFAS -> Icons.AutoMirrored.Filled.List
        Aba.PLANO -> Icons.Filled.DateRange
        Aba.HABITOS -> Icons.Filled.Star
        Aba.PROJETOS -> Icons.Filled.Build
        Aba.DECIDIR -> Icons.Filled.Create
    }

/** Telas autenticadas: abas, detalhes empilhados, barra de sessão ativa e snackbar global. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Shell(deps: AppDependencias) {
    val nav = deps.navegacao
    val aba by nav.aba.collectAsState()
    val pilha by nav.pilha.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val sessaoAtiva by deps.sessaoAtiva.sessao.collectAsState()
    val retornos by deps.retornosAoPrimeiroPlano.collectAsState()

    LaunchedEffect(Unit) {
        deps.sessaoAtiva.avisos.collect { snackbar.showSnackbar(it) }
    }
    // Sessão ativa vem da API no boot e sempre que o app volta ao primeiro plano.
    LaunchedEffect(retornos) { deps.sessaoAtiva.recarregar() }

    // Virada do dia com o app aberto: as telas trocam a data sozinhas no próximo carregamento.
    var dia by remember { mutableStateOf(hojeIso()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            val agora = hojeIso()
            if (agora != dia) {
                dia = agora
                deps.aoVoltarAoPrimeiroPlano()
            }
        }
    }

    val destino = pilha.lastOrNull()
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            // Detalhes têm a própria barra (com voltar); nas abas, o Perfil fica aqui.
            if (destino == null) {
                TopAppBar(
                    title = { Text(aba.rotulo) },
                    actions = {
                        IconButton(onClick = { nav.abrir(Destino.Perfil) }) {
                            Icon(Icons.Filled.AccountCircle, contentDescription = "Perfil")
                        }
                    },
                )
            }
        },
        bottomBar = {
            Column {
                sessaoAtiva?.let { SessaoBarra(it, deps.sessaoAtiva) }
                NavigationBar {
                    Aba.entries.forEach { item ->
                        NavigationBarItem(
                            selected = aba == item && destino == null,
                            onClick = { nav.selecionar(item) },
                            icon = { Icon(item.icone, contentDescription = null) },
                            label = { Text(item.rotulo) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        val mod = Modifier.padding(padding)
        when (destino) {
            null -> when (aba) {
                Aba.TAREFAS -> TarefasTela(deps.tarefasVm, deps, mod)
                Aba.PLANO -> HojeTela(deps.hojeVm, deps, mod)
                Aba.HABITOS -> HabitosTela(deps.habitosVm, deps, mod)
                Aba.PROJETOS -> ProjetosTela(deps.projetosVm, deps, mod)
                Aba.DECIDIR -> DecidirTela(deps.decidirVm, deps, mod)
            }
            Destino.Perfil -> PerfilTela(deps, mod)
            Destino.Lembretes -> LembretesTela(deps, mod)
            is Destino.TarefaDetalhe -> TarefaDetalheTela(destino.id, deps, mod)
            is Destino.ProjetoDetalhe -> ProjetoDetalheTela(destino.id, deps, mod)
            is Destino.DecidirDetalhe -> DecidirDetalheTela(destino.id, deps, mod)
        }
    }
}
