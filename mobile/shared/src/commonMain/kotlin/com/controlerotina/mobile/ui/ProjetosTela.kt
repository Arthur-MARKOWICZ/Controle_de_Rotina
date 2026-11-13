package com.controlerotina.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.controlerotina.mobile.AppDependencias
import com.controlerotina.mobile.api.Projeto
import com.controlerotina.mobile.api.StatusProjeto
import com.controlerotina.mobile.api.Tarefa
import com.controlerotina.mobile.estado.AcaoSessaoTarefa
import com.controlerotina.mobile.estado.Destino
import com.controlerotina.mobile.estado.FormProjeto
import com.controlerotina.mobile.estado.NOME_PROJETO_MAX
import com.controlerotina.mobile.estado.ProjetosViewModel
import kotlinx.coroutines.launch

/** Aba Projetos (spec `mobile-projetos`). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjetosTela(vm: ProjetosViewModel, deps: AppDependencias, modifier: Modifier) {
    val estado by vm.estado.collectAsState()
    val retornos by deps.retornosAoPrimeiroPlano.collectAsState()
    var novoAberto by remember { mutableStateOf(false) }
    val lancar: (suspend () -> Unit) -> Unit = { vm.escopo.launch { it() } }

    LaunchedEffect(retornos) { vm.recarregar() }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = estado.filtro == StatusProjeto.ATIVO,
                    onClick = { lancar { vm.filtrar(StatusProjeto.ATIVO) } },
                    label = { Text("Ativos") },
                )
                FilterChip(
                    selected = estado.filtro == StatusProjeto.ARQUIVADO,
                    onClick = { lancar { vm.filtrar(StatusProjeto.ARQUIVADO) } },
                    label = { Text("Arquivados") },
                )
            }
            PullToRefreshBox(
                isRefreshing = estado.atualizando,
                onRefresh = { lancar { vm.recarregar(puxando = true) } },
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    estado.carregando -> Carregando()
                    estado.erro != null && estado.projetos.isEmpty() ->
                        Aviso("Não foi possível carregar os projetos", estado.erro, "Tentar de novo") { lancar { vm.recarregar() } }
                    estado.projetos.isEmpty() && estado.filtro == StatusProjeto.ARQUIVADO ->
                        Aviso("Nenhum projeto arquivado")
                    estado.projetos.isEmpty() ->
                        Aviso("Nenhum projeto", "Agrupe tarefas de um mesmo objetivo num projeto.", "Novo projeto") { novoAberto = true }
                    else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                        items(estado.projetos, key = { it.id }) { p ->
                            ProjetoCard(p) { deps.navegacao.abrir(Destino.ProjetoDetalhe(p.id)) }
                        }
                    }
                }
            }
        }
        if (estado.filtro == StatusProjeto.ATIVO) {
            FloatingActionButton(
                onClick = { novoAberto = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            ) { Icon(Icons.Filled.Add, contentDescription = "Novo projeto") }
        }
    }

    if (novoAberto) NovoProjetoSheet(vm, onFechar = { novoAberto = false })
}

@Composable
private fun ProjetoCard(p: Projeto, onAbrir: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClickLabel = "Abrir projeto", onClick = onAbrir),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(p.nome, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            p.descricao?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (p.bloqueada) {
                Text("Bloqueado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
private fun CamposProjeto(form: FormProjeto, onMudar: ((FormProjeto) -> FormProjeto) -> Unit) {
    OutlinedTextField(
        value = form.nome,
        onValueChange = { v -> onMudar { it.copy(nome = v) } },
        label = { Text("Nome") },
        singleLine = true,
        isError = form.errosCampo["nome"] != null,
        supportingText = { Text(form.errosCampo["nome"] ?: "${form.nome.length}/$NOME_PROJETO_MAX") },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = form.descricao,
        onValueChange = { v -> onMudar { it.copy(descricao = v) } },
        label = { Text("Descrição (opcional)") },
        minLines = 2,
        isError = form.errosCampo["descricao"] != null,
        supportingText = form.errosCampo["descricao"]?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth(),
    )
    form.erroGeral?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NovoProjetoSheet(vm: ProjetosViewModel, onFechar: () -> Unit) {
    val form by vm.form.collectAsState()
    ModalBottomSheet(onDismissRequest = onFechar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().imePadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Novo projeto", style = MaterialTheme.typography.titleLarge)
            CamposProjeto(form, vm::alterarForm)
            Button(
                onClick = { vm.escopo.launch { if (vm.criar()) onFechar() } },
                enabled = !form.enviando,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (form.enviando) "Criando…" else "Criar projeto") }
        }
    }
}

/** Detalhe do projeto: editar, arquivar/reativar e tarefas dele. */
@Composable
fun ProjetoDetalheTela(id: String, deps: AppDependencias, modifier: Modifier) {
    val vm = deps.projetoDetalheVm
    val estado by vm.estado.collectAsState()
    val tarefasEstado by deps.tarefasVm.estado.collectAsState()
    val versao by deps.sessaoAtiva.versao.collectAsState()
    var confirmarSaida by remember { mutableStateOf(false) }
    var confirmarArquivar by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<Pair<Tarefa, AcaoSessaoTarefa>?>(null) }
    val lancar: (suspend () -> Unit) -> Unit = { vm.escopo.launch { it() } }

    LaunchedEffect(id) { vm.abrir(id) }
    LaunchedEffect(versao) { vm.recarregarTarefas() }

    TelaDetalhe(
        titulo = "Projeto",
        onVoltar = { if (estado.alterado) confirmarSaida = true else deps.navegacao.voltar() },
        modifier = modifier,
        acoes = {
            TextButton(
                onClick = { lancar { vm.salvar() } },
                enabled = estado.alterado && !estado.form.enviando,
            ) { Text(if (estado.form.enviando) "Salvando…" else "Salvar") }
        },
    ) {
        val projeto = estado.projeto
        when {
            estado.carregando -> Carregando()
            projeto == null -> Aviso("Não foi possível abrir o projeto", estado.erroCarga, "Tentar de novo") { lancar { vm.abrir(id) } }
            else -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (projeto.status == StatusProjeto.ARQUIVADO) {
                        Text("Arquivado", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                    }
                    CamposProjeto(estado.form, vm::alterarForm)
                    if (projeto.status == StatusProjeto.ATIVO) {
                        OutlinedButton(onClick = { confirmarArquivar = true }, enabled = !estado.ocupado) { Text("Arquivar") }
                    } else {
                        Button(onClick = { lancar { vm.reativar() } }, enabled = !estado.ocupado) { Text("Reativar") }
                    }
                    HorizontalDivider()
                    Text("Tarefas abertas", style = MaterialTheme.typography.titleMedium)
                    if (estado.tarefas.isEmpty()) Text("Nenhuma tarefa aberta neste projeto.", style = MaterialTheme.typography.bodySmall)
                }
                estado.tarefas.forEach { t ->
                    TarefaCard(
                        t,
                        podeSessao = deps.tarefasVm.podeSessao(t),
                        ocupado = tarefasEstado.ocupado,
                        onAcao = { acao -> picker = t to acao },
                        onAbrir = { abrirTarefa(deps, t.id) },
                    )
                }
            }
        }
    }

    picker?.let { (tarefa, acao) ->
        PickerDuracao(
            tarefa = tarefa,
            acao = acao,
            onFechar = { picker = null },
            onConfirmar = { minutos ->
                picker = null
                lancar {
                    deps.tarefasVm.executarSessao(tarefa, acao, minutos)
                    vm.recarregarTarefas()
                }
            },
        )
    }
    if (confirmarArquivar) {
        Confirmar(
            titulo = "Arquivar projeto?",
            texto = "Ele sai da lista de ativos. Dá para reativar depois em Arquivados.",
            confirmar = "Arquivar",
            onConfirmar = {
                confirmarArquivar = false
                lancar { vm.arquivar() }
            },
            onCancelar = { confirmarArquivar = false },
        )
    }
    if (confirmarSaida) {
        Confirmar(
            titulo = "Descartar alterações?",
            texto = "O que você mudou neste projeto ainda não foi salvo.",
            confirmar = "Descartar",
            onConfirmar = {
                confirmarSaida = false
                deps.navegacao.voltar()
            },
            onCancelar = { confirmarSaida = false },
        )
    }
}
