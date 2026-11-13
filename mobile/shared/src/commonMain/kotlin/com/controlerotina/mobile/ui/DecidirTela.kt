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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.controlerotina.mobile.AppDependencias
import com.controlerotina.mobile.api.ItemDecidir
import com.controlerotina.mobile.dominio.decidirVencido
import com.controlerotina.mobile.dominio.formatarDataCurta
import com.controlerotina.mobile.estado.DecidirViewModel
import com.controlerotina.mobile.estado.Destino
import com.controlerotina.mobile.estado.FormDecidir
import com.controlerotina.mobile.estado.TITULO_MAX
import kotlinx.coroutines.launch
import kotlin.time.Clock

/** Aba Decidir (spec `mobile-decidir`). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecidirTela(vm: DecidirViewModel, deps: AppDependencias, modifier: Modifier) {
    val estado by vm.estado.collectAsState()
    val retornos by deps.retornosAoPrimeiroPlano.collectAsState()
    var novoAberto by remember { mutableStateOf(false) }
    val lancar: (suspend () -> Unit) -> Unit = { vm.escopo.launch { it() } }

    LaunchedEffect(retornos) { vm.recarregar() }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !estado.decididos, onClick = { lancar { vm.mostrarDecididos(false) } }, label = { Text("Abertos") })
                FilterChip(selected = estado.decididos, onClick = { lancar { vm.mostrarDecididos(true) } }, label = { Text("Decididos") })
            }
            PullToRefreshBox(
                isRefreshing = estado.atualizando,
                onRefresh = { lancar { vm.recarregar(puxando = true) } },
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    estado.carregando -> Carregando()
                    estado.erro != null && estado.itens.isEmpty() ->
                        Aviso("Não foi possível carregar", estado.erro, "Tentar de novo") { lancar { vm.recarregar() } }
                    estado.itens.isEmpty() && estado.decididos -> Aviso("Nada decidido ainda")
                    estado.itens.isEmpty() ->
                        Aviso("Nada para decidir", "Anote o que ainda não sabe se vai fazer. Depois vira tarefa ou é descartado.", "Novo item") {
                            novoAberto = true
                        }
                    else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                        items(estado.itens, key = { it.id }) { item ->
                            DecidirCard(item) {
                                deps.decidirDetalheVm.abrir(item)
                                deps.navegacao.abrir(Destino.DecidirDetalhe(item.id))
                            }
                        }
                    }
                }
            }
        }
        if (!estado.decididos) {
            FloatingActionButton(
                onClick = { novoAberto = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            ) { Icon(Icons.Filled.Add, contentDescription = "Novo item para decidir") }
        }
    }

    if (novoAberto) NovoDecidirSheet(vm, onFechar = { novoAberto = false })
}

@Composable
private fun DecidirCard(item: ItemDecidir, onAbrir: () -> Unit) {
    val cores = MaterialTheme.colorScheme
    val vencido = !item.promovido && decidirVencido(item.decidirAte, Clock.System.now())
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClickLabel = "Abrir item", onClick = onAbrir)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.titulo, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                formatarDataCurta(item.decidirAte)?.let { data ->
                    if (vencido) {
                        Etiqueta("Decidir até $data · vencido", cores.error.copy(alpha = 0.12f), cores.error)
                    } else {
                        Text("Decidir até $data", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (item.promovido) Etiqueta("Virou tarefa", cores.primary.copy(alpha = 0.12f), cores.primary)
            }
        }
    }
}

@Composable
private fun CamposDecidir(form: FormDecidir, habilitado: Boolean, onMudar: ((FormDecidir) -> FormDecidir) -> Unit) {
    OutlinedTextField(
        value = form.titulo,
        onValueChange = { v -> onMudar { it.copy(titulo = v) } },
        label = { Text("Título") },
        enabled = habilitado,
        isError = form.errosCampo["titulo"] != null,
        supportingText = { Text(form.errosCampo["titulo"] ?: "${form.titulo.length}/$TITULO_MAX") },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = form.descricao,
        onValueChange = { v -> onMudar { it.copy(descricao = v) } },
        label = { Text("Descrição (opcional)") },
        enabled = habilitado,
        minLines = 2,
        isError = form.errosCampo["descricao"] != null,
        supportingText = form.errosCampo["descricao"]?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth(),
    )
    if (habilitado) {
        CampoData(
            rotulo = form.decidirAteRotulo,
            vazio = "Decidir até (opcional)",
            prefixo = "Decidir até",
            onEscolher = { iso, rotulo -> onMudar { it.copy(decidirAte = iso, decidirAteRotulo = rotulo) } },
            onLimpar = { onMudar { it.copy(decidirAte = null, decidirAteRotulo = null) } },
        )
    } else {
        form.decidirAteRotulo?.let { Text("Decidir até $it", style = MaterialTheme.typography.bodyMedium) }
    }
    form.errosCampo["decidirAte"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    form.erroGeral?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NovoDecidirSheet(vm: DecidirViewModel, onFechar: () -> Unit) {
    val form by vm.form.collectAsState()
    ModalBottomSheet(onDismissRequest = onFechar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().imePadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Para decidir", style = MaterialTheme.typography.titleLarge)
            CamposDecidir(form, habilitado = true, onMudar = vm::alterarForm)
            Button(
                onClick = { vm.escopo.launch { if (vm.criar()) onFechar() } },
                enabled = !form.enviando,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (form.enviando) "Anotando…" else "Anotar") }
        }
    }
}

/** Detalhe de um item: editar, descartar e virar tarefa; decidido é só leitura. */
@Composable
fun DecidirDetalheTela(id: String, deps: AppDependencias, modifier: Modifier) {
    val vm = deps.decidirDetalheVm
    val estado by vm.estado.collectAsState()
    var confirmarSaida by remember { mutableStateOf(false) }
    var confirmarDescarte by remember { mutableStateOf(false) }
    var promovendo by remember { mutableStateOf(false) }
    val lancar: (suspend () -> Unit) -> Unit = { vm.escopo.launch { it() } }

    TelaDetalhe(
        titulo = if (estado.somenteLeitura) "Decidido" else "Para decidir",
        onVoltar = { if (estado.alterado) confirmarSaida = true else deps.navegacao.voltar() },
        modifier = modifier,
        acoes = {
            if (!estado.somenteLeitura) {
                TextButton(
                    onClick = { lancar { vm.salvar() } },
                    enabled = estado.alterado && !estado.form.enviando,
                ) { Text(if (estado.form.enviando) "Salvando…" else "Salvar") }
            }
        },
    ) {
        if (estado.item?.id != id) {
            Aviso("Item não encontrado", acao = "Voltar") { deps.navegacao.voltar() }
            return@TelaDetalhe
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CamposDecidir(estado.form, habilitado = !estado.somenteLeitura, onMudar = vm::alterarForm)
            estado.erroAcao?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (estado.somenteLeitura) {
                Text("Este item já virou tarefa e não pode mais ser editado.", style = MaterialTheme.typography.bodySmall)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { promovendo = true }, enabled = !estado.ocupado) { Text("Virar tarefa") }
                    OutlinedButton(onClick = { confirmarDescarte = true }, enabled = !estado.ocupado) { Text("Descartar") }
                }
            }
        }
    }

    if (promovendo) {
        val p = estado.promover
        AlertDialog(
            onDismissRequest = { promovendo = false },
            title = { Text("Virar tarefa") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Tudo opcional: o que ficar vazio segue o padrão da tarefa.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = p.duracao,
                        onValueChange = { v -> vm.alterarPromover { it.copy(duracao = v) } },
                        label = { Text("Duração em minutos") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    CampoData(
                        rotulo = p.prazoRotulo,
                        vazio = "Prazo",
                        prefixo = "Prazo",
                        onEscolher = { iso, rotulo -> vm.alterarPromover { it.copy(prazo = iso, prazoRotulo = rotulo) } },
                        onLimpar = { vm.alterarPromover { it.copy(prazo = null, prazoRotulo = null) } },
                    )
                    SeletorPrioridade(p.prioridade, permiteNenhuma = true) { pr -> vm.alterarPromover { it.copy(prioridade = pr) } }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    promovendo = false
                    lancar { if (vm.promover()) deps.navegacao.voltar() }
                }) { Text("Criar tarefa") }
            },
            dismissButton = { TextButton(onClick = { promovendo = false }) { Text("Cancelar") } },
        )
    }
    if (confirmarDescarte) {
        Confirmar(
            titulo = "Descartar item?",
            texto = "O item é apagado de vez. Não dá para desfazer.",
            confirmar = "Descartar",
            onConfirmar = {
                confirmarDescarte = false
                lancar { if (vm.descartar()) deps.navegacao.voltar() }
            },
            onCancelar = { confirmarDescarte = false },
        )
    }
    if (confirmarSaida) {
        Confirmar(
            titulo = "Descartar alterações?",
            texto = "O que você mudou neste item ainda não foi salvo.",
            confirmar = "Descartar",
            onConfirmar = {
                confirmarSaida = false
                deps.navegacao.voltar()
            },
            onCancelar = { confirmarSaida = false },
        )
    }
}
