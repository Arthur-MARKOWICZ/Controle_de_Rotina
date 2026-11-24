package com.controlerotina.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.Canvas
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.controlerotina.mobile.AppDependencias
import com.controlerotina.mobile.api.DiaSemana
import com.controlerotina.mobile.api.OrigemBloco
import com.controlerotina.mobile.api.StatusExecucao
import com.controlerotina.mobile.api.StatusPlano
import com.controlerotina.mobile.dominio.AcaoBloco
import com.controlerotina.mobile.dominio.formatarMinutos
import com.controlerotina.mobile.estado.BlocoNaTela
import com.controlerotina.mobile.estado.EstadoHoje
import com.controlerotina.mobile.estado.HORAS_MAX_DIA
import com.controlerotina.mobile.estado.HojeViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojeTela(vm: HojeViewModel, deps: AppDependencias, modifier: Modifier) {
    val estado by vm.estado.collectAsState()
    val atualizando by vm.atualizando.collectAsState()
    val ocupado by vm.ocupado.collectAsState()
    val versao by deps.sessaoAtiva.versao.collectAsState()
    val retornos by deps.retornosAoPrimeiroPlano.collectAsState()
    var abortar by remember { mutableStateOf<BlocoNaTela?>(null) }
    val lancar: (suspend () -> Unit) -> Unit = { acao -> vm.escopo.launch { acao() } }

    LaunchedEffect(versao, retornos) { vm.carregar() }

    PullToRefreshBox(
        isRefreshing = atualizando,
        onRefresh = { lancar { vm.carregar(puxando = true) } },
        modifier = modifier.fillMaxSize(),
    ) {
        when (val e = estado) {
            EstadoHoje.Carregando -> Carregando()
            is EstadoHoje.Erro -> Aviso("Não foi possível carregar o dia", e.mensagem, "Tentar de novo") {
                lancar { vm.carregar() }
            }
            is EstadoHoje.SemPlano -> {
                Aviso(
                    "Ainda não há plano para hoje",
                    "O planner monta o dia com suas tarefas, hábitos e capacidade.",
                    if (e.gerando) "Gerando…" else "Gerar plano",
                    acaoHabilitada = !e.gerando,
                ) { lancar { vm.gerar() } }
                if (e.pedindoCapacidade) {
                    CapacidadeSheet(
                        e,
                        onFechar = vm::fecharCapacidade,
                        onUsarDiasUteis = { lancar { vm.usarDiasUteis() } },
                        onSalvar = { horas -> lancar { vm.salvarSemana(horas) } },
                    )
                }
            }
            is EstadoHoje.Conteudo -> PlanoConteudo(e, ocupado, lancar, vm, onAbortar = { abortar = it })
        }
    }

    abortar?.let { item ->
        AlertDialog(
            onDismissRequest = { abortar = null },
            title = { Text("Abortar bloco?") },
            text = { Text("\"${item.bloco.titulo}\" volta a ficar disponível para iniciar.") },
            confirmButton = {
                TextButton(onClick = {
                    abortar = null
                    lancar { vm.abortar(item) }
                }) { Text("Abortar") }
            },
            dismissButton = { TextButton(onClick = { abortar = null }) { Text("Continuar") } },
        )
    }
}

@Composable
private fun PlanoConteudo(
    e: EstadoHoje.Conteudo,
    ocupado: String?,
    lancar: (suspend () -> Unit) -> Unit,
    vm: HojeViewModel,
    onAbortar: (BlocoNaTela) -> Unit,
) {
    val plano = e.plano
    val totalMin = plano.blocos.sumOf { it.duracaoMinutos }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Subtitulo(
                "${plano.blocos.size} blocos · ${formatarMinutos(totalMin)}" +
                    if (plano.status == StatusPlano.RASCUNHO) " · rascunho" else "",
            )
        }
        if (!plano.mensagem.isNullOrBlank()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        // "Não coube" é aviso (ocre), não erro nem laranja de XP.
                        containerColor = if (plano.temConflito) {
                            Rotina.cores.avisoFundo
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                ) { Text(plano.mensagem, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium) }
            }
        }
        if (plano.status == StatusPlano.RASCUNHO) {
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val vazio = plano.blocos.isEmpty()
                        Text(
                            if (vazio) {
                                "Nenhum bloco coube. Ajuste a capacidade ou adicione tarefas."
                            } else {
                                "Revise o rascunho e aprove para começar."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "Ajustes finos (adicionar ou remover blocos) estão na versão web.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                        Button(
                            onClick = { lancar { vm.aprovar() } },
                            enabled = ocupado == null && !vazio,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                when {
                                    vazio -> "Nada para aprovar"
                                    ocupado == "plano" -> "Aprovando…"
                                    else -> "Aprovar plano"
                                },
                            )
                        }
                    }
                }
            }
        }
        itemsIndexed(e.blocos, key = { _, it -> it.bloco.id }) { indice, item ->
            BlocoCard(item, desabilitado = ocupado != null, carregando = ocupado == item.bloco.id,
                primeiro = indice == 0,
                ultimo = indice == e.blocos.lastIndex,
                onIniciar = { lancar { vm.iniciar(item) } },
                onConcluir = { lancar { vm.concluir(item) } },
                onAbortar = { onAbortar(item) },
            )
        }
        if (plano.itensNaoAlocados.isNotEmpty()) {
            item { CabecalhoTela("Não coube hoje") }
            items(plano.itensNaoAlocados, key = { "na-" + it.id }) { na ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(na.titulo, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatarMinutos(na.duracaoMinutos), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun BlocoCard(
    item: BlocoNaTela,
    desabilitado: Boolean,
    carregando: Boolean,
    primeiro: Boolean,
    ultimo: Boolean,
    onIniciar: () -> Unit,
    onConcluir: () -> Unit,
    onAbortar: () -> Unit,
) {
    val cores = MaterialTheme.colorScheme
    val concluido = item.status == StatusExecucao.CONCLUIDO
    val linha = corDaLinha(item.bloco.origem, item.bloco.tarefa?.projetoNome, Rotina.cores)
    // Plano como linha de metrô: a estação à esquerda, o trecho na cor do projeto liga os blocos.
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(start = 12.dp, end = 16.dp)) {
        Estacao(linha, item.status, primeiro, ultimo, Modifier.width(28.dp).fillMaxHeight())
        Card(
            Modifier.weight(1f).padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (item.status == StatusExecucao.EM_ANDAMENTO) cores.primaryContainer else cores.surface,
            ),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.bloco.titulo,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (concluido) cores.onSurface.copy(alpha = 0.5f) else cores.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            listOfNotNull(
                                formatarMinutos(item.bloco.duracaoMinutos),
                                if (item.bloco.origem == OrigemBloco.HABITO) "hábito" else null,
                                item.bloco.habito?.horarioPreferencial?.take(5),
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    when (item.status) {
                        StatusExecucao.CONCLUIDO -> Etiqueta("Concluído", Rotina.cores.sucessoFundo, Rotina.cores.sucesso)
                        StatusExecucao.EM_ANDAMENTO -> Etiqueta("Em andamento", cores.primary.copy(alpha = 0.15f), cores.primary)
                        StatusExecucao.ABORTADO -> Etiqueta("Abortado", cores.surfaceVariant, cores.onSurface)
                        StatusExecucao.NAO_INICIADO -> Unit
                    }
                }
                if (item.acoes.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.End)) {
                        if (AcaoBloco.ABORTAR in item.acoes) TextButton(onClick = onAbortar, enabled = !desabilitado) { Text("Abortar") }
                        if (AcaoBloco.CONCLUIR in item.acoes) Button(onClick = onConcluir, enabled = !desabilitado) { Text("Concluir") }
                        if (AcaoBloco.INICIAR in item.acoes) {
                            FilledTonalButton(onClick = onIniciar, enabled = !desabilitado) { Text(if (carregando) "Iniciando…" else "Iniciar") }
                        }
                    }
                }
            }
        }
    }
}

private val NOMES_DIA = mapOf(
    DiaSemana.SEGUNDA to ("Seg" to "segunda"),
    DiaSemana.TERCA to ("Ter" to "terça"),
    DiaSemana.QUARTA to ("Qua" to "quarta"),
    DiaSemana.QUINTA to ("Qui" to "quinta"),
    DiaSemana.SEXTA to ("Sex" to "sexta"),
    DiaSemana.SABADO to ("Sáb" to "sábado"),
    DiaSemana.DOMINGO to ("Dom" to "domingo"),
)

/** "Quanto tempo você tem por dia?": preset em um toque ou stepper por dia, e gera o plano. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CapacidadeSheet(
    e: EstadoHoje.SemPlano,
    onFechar: () -> Unit,
    onUsarDiasUteis: () -> Unit,
    onSalvar: (Map<DiaSemana, Int>) -> Unit,
) {
    var horas by remember(e.horasPorDia) { mutableStateOf(DiaSemana.entries.associateWith { e.horasPorDia[it] ?: 0 }) }
    val salvando = e.salvandoCapacidade
    ModalBottomSheet(
        onDismissRequest = { if (!salvando) onFechar() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Quanto tempo você tem por dia?", style = MaterialTheme.typography.titleLarge)
            Text(
                "Sem horas definidas o plano sai vazio. Escolha um ponto de partida; dá para mudar depois.",
                style = MaterialTheme.typography.bodyMedium,
            )
            e.erroCapacidade?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Button(onClick = onUsarDiasUteis, enabled = !salvando, modifier = Modifier.fillMaxWidth()) {
                Text(if (salvando) "Salvando…" else "Usar dias úteis 4h")
            }
            Text("Ou ajuste cada dia:", style = MaterialTheme.typography.labelLarge)
            DiaSemana.entries.forEach { dia ->
                val (curto, extenso) = NOMES_DIA.getValue(dia)
                val valor = horas.getValue(dia)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(curto, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    IconButton(
                        onClick = { horas = horas + (dia to (valor - 1).coerceAtLeast(0)) },
                        enabled = !salvando && valor > 0,
                        modifier = Modifier.semantics { contentDescription = "Diminuir horas de $extenso" },
                    ) { Text("−", Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.titleLarge) }
                    Text(
                        "${valor}h",
                        Modifier.width(48.dp).semantics { contentDescription = "$valor horas na $extenso" },
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    IconButton(
                        onClick = { horas = horas + (dia to (valor + 1).coerceAtMost(HORAS_MAX_DIA)) },
                        enabled = !salvando && valor < HORAS_MAX_DIA,
                        modifier = Modifier.semantics { contentDescription = "Aumentar horas de $extenso" },
                    ) { Text("+", Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.titleLarge) }
                }
            }
            OutlinedButton(
                onClick = { onSalvar(horas) },
                enabled = !salvando && horas.values.any { it > 0 },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Salvar e gerar") }
            TextButton(onClick = onFechar, enabled = !salvando, modifier = Modifier.align(Alignment.End).padding(bottom = 16.dp)) {
                Text("Agora não")
            }
        }
    }
}

/** Estação da linha do plano: vazia = pendente, aro grosso = em andamento, cheia = concluída. */
@Composable
private fun Estacao(cor: Color, status: StatusExecucao, primeiro: Boolean, ultimo: Boolean, modifier: Modifier) {
    val fundo = MaterialTheme.colorScheme.surface
    Canvas(modifier) {
        val x = size.width / 2
        val y = 30.dp.toPx()
        val traco = 4.dp.toPx()
        if (!primeiro) drawLine(cor, Offset(x, 0f), Offset(x, y), traco)
        if (!ultimo) drawLine(cor, Offset(x, y), Offset(x, size.height), traco)
        val raio = 9.dp.toPx()
        when (status) {
            StatusExecucao.CONCLUIDO -> drawCircle(cor, raio, Offset(x, y))
            StatusExecucao.EM_ANDAMENTO -> {
                drawCircle(fundo, raio, Offset(x, y))
                drawCircle(cor, raio - 2.5.dp.toPx(), Offset(x, y), style = Stroke(5.dp.toPx()))
            }
            else -> {
                drawCircle(fundo, raio, Offset(x, y))
                drawCircle(cor, raio - 1.5.dp.toPx(), Offset(x, y), style = Stroke(3.dp.toPx()))
            }
        }
    }
}
