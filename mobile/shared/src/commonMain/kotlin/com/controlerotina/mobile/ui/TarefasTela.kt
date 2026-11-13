package com.controlerotina.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.controlerotina.mobile.AppDependencias
import com.controlerotina.mobile.api.Prioridade
import com.controlerotina.mobile.api.StatusTarefa
import com.controlerotina.mobile.api.Tarefa
import com.controlerotina.mobile.dominio.PRESETS_DURACAO
import com.controlerotina.mobile.dominio.duracaoValida
import com.controlerotina.mobile.dominio.formatarDataCurta
import com.controlerotina.mobile.dominio.formatarMinutos
import com.controlerotina.mobile.dominio.presetHabilitado
import com.controlerotina.mobile.estado.AcaoSessaoTarefa
import com.controlerotina.mobile.estado.Destino
import com.controlerotina.mobile.estado.TITULO_MAX
import com.controlerotina.mobile.estado.TarefasViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TarefasTela(vm: TarefasViewModel, deps: AppDependencias, modifier: Modifier) {
    val estado by vm.estado.collectAsState()
    val versao by deps.sessaoAtiva.versao.collectAsState()
    val retornos by deps.retornosAoPrimeiroPlano.collectAsState()
    var novaAberta by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<Pair<Tarefa, AcaoSessaoTarefa>?>(null) }
    val lancar: (suspend () -> Unit) -> Unit = { acao -> vm.escopo.launch { acao() } }
    val lista = rememberLazyListState()

    LaunchedEffect(versao, retornos) { vm.recarregar() }

    // Paginação: perto do fim da lista, busca a próxima página.
    val pertoDoFim by remember {
        derivedStateOf {
            val ultimo = lista.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            ultimo >= lista.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(pertoDoFim, estado.tarefas.size) {
        if (pertoDoFim && !estado.fim) vm.carregarMais()
    }

    Box(modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = estado.atualizando,
            onRefresh = { lancar { vm.recarregar(puxando = true) } },
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                estado.carregandoInicial -> Carregando()
                estado.erro != null && estado.tarefas.isEmpty() ->
                    Aviso("Não foi possível carregar as tarefas", estado.erro, "Tentar de novo") { lancar { vm.recarregar() } }
                estado.tarefas.isEmpty() ->
                    Aviso("Nenhuma tarefa aberta", "Anote o que precisa fazer e o planner encaixa no seu dia.", "Nova tarefa") {
                        novaAberta = true
                    }
                else -> LazyColumn(state = lista, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                    item { Subtitulo("Abertas, por prazo") }
                    estado.tarefas.forEach { t ->
                        val aberto = t.id in estado.expandidos
                        item(key = t.id) {
                            TarefaCard(
                                t,
                                podeSessao = vm.podeSessao(t),
                                ocupado = estado.ocupado,
                                onAcao = { acao -> picker = t to acao },
                                onAbrir = { abrirTarefa(deps, t.id) },
                                expandido = if (t.temFilhos) aberto else null,
                                onExpandir = {
                                    if (aberto) vm.recolher(t.id) else lancar { vm.expandir(t.id) }
                                },
                            )
                        }
                        if (aberto) {
                            items(estado.filhos[t.id].orEmpty(), key = { "${t.id}/${it.id}" }) { filho ->
                                TarefaCard(
                                    filho,
                                    podeSessao = vm.podeSessao(filho),
                                    ocupado = estado.ocupado,
                                    onAcao = { acao -> picker = filho to acao },
                                    onAbrir = { abrirTarefa(deps, filho.id) },
                                    recuo = true,
                                )
                            }
                        }
                    }
                    if (estado.carregandoMais) {
                        item { Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) } }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { novaAberta = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Filled.Add, contentDescription = "Nova tarefa") }
    }

    if (novaAberta) NovaTarefaSheet(vm, deps, onFechar = { novaAberta = false })

    picker?.let { (tarefa, acao) ->
        PickerDuracao(
            tarefa = tarefa,
            acao = acao,
            onFechar = { picker = null },
            onConfirmar = { minutos ->
                picker = null
                lancar { vm.executarSessao(tarefa, acao, minutos) }
            },
        )
    }
}

internal fun abrirTarefa(deps: AppDependencias, id: String) {
    deps.navegacao.abrir(Destino.TarefaDetalhe(id))
}

/**
 * Card de tarefa. Ações sempre na margem esquerda (spec `mobile-tarefas`).
 * [expandido] não nulo mostra o controle de subtarefas; [recuo] marca uma subtarefa aninhada.
 */
@Composable
internal fun TarefaCard(
    t: Tarefa,
    podeSessao: Boolean,
    ocupado: String?,
    onAcao: (AcaoSessaoTarefa) -> Unit,
    onAbrir: () -> Unit,
    expandido: Boolean? = null,
    onExpandir: () -> Unit = {},
    recuo: Boolean = false,
) {
    val cores = MaterialTheme.colorScheme
    Card(
        Modifier.fillMaxWidth()
            .padding(start = if (recuo) 40.dp else 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
            .clickable(onClickLabel = "Editar tarefa", onClick = onAbrir),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    t.titulo,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (expandido != null) {
                    IconButton(onClick = onExpandir) {
                        Icon(
                            if (expandido) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (expandido) "Recolher subtarefas" else "Mostrar subtarefas",
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                t.prioridade?.let {
                    val cor = when (it) {
                        Prioridade.ALTA -> cores.error
                        Prioridade.MEDIA -> cores.secondary
                        Prioridade.BAIXA -> cores.onSurface
                    }
                    Etiqueta(rotuloPrioridade(it), cor.copy(alpha = 0.12f), cor)
                }
                if (t.status == StatusTarefa.ATRASADA) Etiqueta("Atrasada", cores.error.copy(alpha = 0.12f), cores.error)
                if (t.bloqueada) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.semantics { contentDescription = "Bloqueada por espera" },
                    ) {
                        Icon(Icons.Filled.Lock, null, Modifier.size(14.dp), tint = cores.secondary)
                        Text(" Bloqueada", style = MaterialTheme.typography.labelSmall, color = cores.secondary)
                    }
                }
            }
            Text(
                listOfNotNull(
                    formatarDataCurta(t.prazo)?.let { "Prazo $it" } ?: "Sem prazo",
                    "restam ${formatarMinutos(t.restanteMinutos)}",
                    t.projetoNome,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
            )
            if (podeSessao) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val livre = ocupado == null
                    Button(onClick = { onAcao(AcaoSessaoTarefa.INICIAR) }, enabled = livre) {
                        Text(if (ocupado == t.id) "…" else "Iniciar")
                    }
                    OutlinedButton(onClick = { onAcao(AcaoSessaoTarefa.CONCLUIR) }, enabled = livre) { Text("Concluir") }
                }
            }
        }
    }
}

/** Presets 15/25/30/45/60, Restante e valor livre; tudo entre 1 e o restante (spec mobile-tarefas). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PickerDuracao(
    tarefa: Tarefa,
    acao: AcaoSessaoTarefa,
    onFechar: () -> Unit,
    onConfirmar: (Int) -> Unit,
) {
    val restante = tarefa.restanteMinutos
    var escolhido by remember { mutableStateOf<Int?>(PRESETS_DURACAO.lastOrNull { presetHabilitado(it, restante) } ?: restante) }
    var livre by remember { mutableStateOf("") }
    val valor = livre.toIntOrNull() ?: escolhido
    val valido = duracaoValida(valor, restante)

    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text(if (acao == AcaoSessaoTarefa.INICIAR) "Quanto tempo agora?" else "Quanto tempo você dedicou?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tarefa.titulo, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PRESETS_DURACAO.forEach { p ->
                        FilterChip(
                            selected = livre.isEmpty() && escolhido == p,
                            onClick = { escolhido = p; livre = "" },
                            enabled = presetHabilitado(p, restante),
                            label = { Text("${p}min") },
                        )
                    }
                    FilterChip(
                        selected = livre.isEmpty() && escolhido == restante,
                        onClick = { escolhido = restante; livre = "" },
                        label = { Text("Restante (${formatarMinutos(restante)})") },
                    )
                }
                OutlinedTextField(
                    value = livre,
                    onValueChange = { livre = it.filter(Char::isDigit).take(4) },
                    label = { Text("Outro valor (min)") },
                    singleLine = true,
                    isError = livre.isNotEmpty() && valido == null,
                    supportingText = { Text("Entre 1 e $restante minutos") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { valido?.let(onConfirmar) }, enabled = valido != null) {
                Text(if (acao == AcaoSessaoTarefa.INICIAR) "Iniciar" else "Concluir")
            }
        },
        dismissButton = { TextButton(onClick = onFechar) { Text("Cancelar") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NovaTarefaSheet(vm: TarefasViewModel, deps: AppDependencias, onFechar: () -> Unit) {
    val form by vm.form.collectAsState()
    val projetos by deps.opcoesProjeto.ativos.collectAsState()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(Unit) { deps.opcoesProjeto.carregar() }

    ModalBottomSheet(onDismissRequest = onFechar, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().imePadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Nova tarefa", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = form.titulo,
                onValueChange = vm::alterarTitulo,
                label = { Text("Título") },
                isError = form.errosCampo["titulo"] != null,
                supportingText = { Text(form.errosCampo["titulo"] ?: "${form.titulo.length}/$TITULO_MAX") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.duracao,
                onValueChange = vm::alterarDuracao,
                label = { Text("Duração em minutos (opcional)") },
                singleLine = true,
                isError = form.errosCampo["duracaoMinutos"] != null,
                supportingText = form.errosCampo["duracaoMinutos"]?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            CampoData(
                rotulo = form.prazoRotulo,
                vazio = "Prazo (opcional)",
                prefixo = "Prazo",
                onEscolher = { iso, rotulo -> vm.alterarPrazo(iso, rotulo) },
                onLimpar = { vm.alterarPrazo(null, null) },
            )
            form.errosCampo["prazo"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            SeletorProjeto(form.projetoId, projetos, nomeAtual = null, onEscolher = vm::alterarProjeto)
            form.errosCampo["projetoId"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            form.erroGeral?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = { vm.escopo.launch { if (vm.criar()) onFechar() } },
                enabled = !form.enviando,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (form.enviando) "Criando…" else "Criar tarefa") }
        }
    }
}
