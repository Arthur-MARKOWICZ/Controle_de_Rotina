package com.controlerotina.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.controlerotina.mobile.AppDependencias
import com.controlerotina.mobile.api.HabitoDoDia
import com.controlerotina.mobile.dominio.formatarMinutos
import com.controlerotina.mobile.estado.HabitosViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitosTela(vm: HabitosViewModel, deps: AppDependencias, modifier: Modifier) {
    val estado by vm.estado.collectAsState()
    val versao by deps.sessaoAtiva.versao.collectAsState()
    val retornos by deps.retornosAoPrimeiroPlano.collectAsState()
    val lancar: (suspend () -> Unit) -> Unit = { acao -> vm.escopo.launch { acao() } }

    LaunchedEffect(versao, retornos) { vm.carregar() }

    PullToRefreshBox(
        isRefreshing = estado.atualizando,
        onRefresh = { lancar { vm.carregar(puxando = true) } },
        modifier = modifier.fillMaxSize(),
    ) {
        when {
            estado.carregando -> Carregando()
            estado.erro != null && estado.habitos.isEmpty() ->
                Aviso("Não foi possível carregar os hábitos", estado.erro, "Tentar de novo") { lancar { vm.carregar() } }
            estado.habitos.isEmpty() ->
                Aviso("Nenhum hábito para hoje", "Hábitos são criados e agendados na versão web.")
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                val feitos = estado.habitos.count { it.cumprido }
                item { Subtitulo("$feitos de ${estado.habitos.size} cumpridos hoje") }
                items(estado.habitos, key = { it.id }) { h ->
                    HabitoCard(
                        h,
                        podeAgir = vm.podeAgir(h),
                        desabilitado = estado.ocupado != null,
                        onIniciar = { lancar { vm.iniciar(h) } },
                        onConcluir = { lancar { vm.concluir(h) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun HabitoCard(
    h: HabitoDoDia,
    podeAgir: Boolean,
    desabilitado: Boolean,
    onIniciar: () -> Unit,
    onConcluir: () -> Unit,
) {
    val cores = MaterialTheme.colorScheme
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(h.titulo, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                when {
                    h.cumprido -> Etiqueta("Cumprido", CorSucesso.copy(alpha = 0.15f), CorSucesso)
                    h.emAndamento != null -> Etiqueta("Em andamento", cores.primary.copy(alpha = 0.15f), cores.primary)
                }
            }
            Text(
                listOfNotNull(
                    formatarMinutos(h.duracaoMinutos),
                    h.horarioPreferencial?.take(5),
                    "sequência de ${h.streakAtual} ${if (h.streakAtual == 1) "dia" else "dias"}",
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
            )
            if (podeAgir) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onIniciar, enabled = !desabilitado) { Text("Iniciar") }
                    OutlinedButton(onClick = onConcluir, enabled = !desabilitado) { Text("Concluir") }
                }
            }
        }
    }
}
