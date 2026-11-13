package com.controlerotina.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.controlerotina.mobile.AppDependencias
import com.controlerotina.mobile.dominio.formatarMinutos
import com.controlerotina.mobile.estado.TITULO_MAX
import kotlinx.coroutines.launch

/** Detalhe da tarefa: edição (spec `mobile-tarefas`, "Editar tarefa") e subtarefas. */
@Composable
fun TarefaDetalheTela(id: String, deps: AppDependencias, modifier: Modifier) {
    val vm = deps.editarTarefaVm
    val estado by vm.estado.collectAsState()
    val projetos by deps.opcoesProjeto.ativos.collectAsState()
    var confirmarSaida by remember { mutableStateOf(false) }
    val lancar: (suspend () -> Unit) -> Unit = { vm.escopo.launch { it() } }

    LaunchedEffect(id) {
        vm.abrir(id)
        deps.opcoesProjeto.carregar()
    }

    val voltar = { if (estado.alterado) confirmarSaida = true else deps.navegacao.voltar() }

    TelaDetalhe(
        titulo = if (estado.original?.parentId != null) "Subtarefa" else "Tarefa",
        onVoltar = { voltar() },
        modifier = modifier,
        acoes = {
            TextButton(
                onClick = { lancar { if (vm.salvar()) deps.navegacao.voltar() } },
                enabled = estado.original != null && !estado.salvando,
            ) { Text(if (estado.salvando) "Salvando…" else "Salvar") }
        },
    ) {
        val original = estado.original
        when {
            estado.carregando -> Carregando()
            original == null -> Aviso("Não foi possível abrir a tarefa", estado.erroCarga, "Tentar de novo") { lancar { vm.abrir(id) } }
            else -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val c = estado.campos
                original.parentTitulo?.let {
                    Text("Subtarefa de $it", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                }
                OutlinedTextField(
                    value = c.titulo,
                    onValueChange = { v -> vm.alterar { it.copy(titulo = v) } },
                    label = { Text("Título") },
                    isError = estado.errosCampo["titulo"] != null,
                    supportingText = { Text(estado.errosCampo["titulo"] ?: "${c.titulo.length}/$TITULO_MAX") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = c.descricao,
                    onValueChange = { v -> vm.alterar { it.copy(descricao = v) } },
                    label = { Text("Descrição") },
                    minLines = 2,
                    isError = estado.errosCampo["descricao"] != null,
                    supportingText = estado.errosCampo["descricao"]?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                SeletorPrioridade(c.prioridade, permiteNenhuma = false) { p -> vm.alterar { it.copy(prioridade = p) } }
                // A API não remove prazo pelo PATCH (null mantém o atual): só dá para trocar a data.
                CampoData(
                    rotulo = c.prazoRotulo,
                    vazio = "Sem prazo (toque para definir)",
                    prefixo = "Prazo",
                    onEscolher = { iso, rotulo -> vm.alterar { it.copy(prazo = iso, prazoRotulo = rotulo) } },
                    onLimpar = if (original.prazo == null) {
                        { vm.alterar { it.copy(prazo = null, prazoRotulo = null) } }
                    } else {
                        null
                    },
                )
                estado.errosCampo["prazo"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (estado.duracaoEditavel) {
                    OutlinedTextField(
                        value = c.duracao,
                        onValueChange = { v -> vm.alterar { it.copy(duracao = v.filter(Char::isDigit).take(4)) } },
                        label = { Text("Duração em minutos") },
                        singleLine = true,
                        isError = estado.errosCampo["duracaoMinutos"] != null,
                        supportingText = estado.errosCampo["duracaoMinutos"]?.let { { Text(it) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        "Duração: ${formatarMinutos(original.duracaoMinutos)} (soma das subtarefas)",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                SeletorProjeto(c.projetoId, projetos, nomeAtual = original.projetoNome) { p -> vm.alterar { it.copy(projetoId = p) } }
                estado.errosCampo["projetoId"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                estado.erroGeral?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                if (estado.aceitaSubtarefa) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    SecaoSubtarefas(deps)
                }
            }
        }
    }

    if (confirmarSaida) {
        Confirmar(
            titulo = "Descartar alterações?",
            texto = "O que você mudou nesta tarefa ainda não foi salvo.",
            confirmar = "Descartar",
            onConfirmar = {
                confirmarSaida = false
                deps.navegacao.voltar()
            },
            onCancelar = { confirmarSaida = false },
        )
    }
}

@Composable
private fun SecaoSubtarefas(deps: AppDependencias) {
    val vm = deps.editarTarefaVm
    val estado by vm.estado.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Subtarefas", style = MaterialTheme.typography.titleMedium)
        if (estado.subtarefas.isEmpty()) {
            Text("Quebre em passos menores; a duração passa a ser a soma deles.", style = MaterialTheme.typography.bodySmall)
        }
        estado.subtarefas.forEach { filho ->
            OutlinedButton(onClick = { abrirTarefa(deps, filho.id) }, modifier = Modifier.fillMaxWidth()) {
                Text(filho.titulo, modifier = Modifier.weight(1f), maxLines = 1)
                Text(formatarMinutos(filho.duracaoMinutos), style = MaterialTheme.typography.labelSmall)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            OutlinedTextField(
                value = estado.novaSubtarefa,
                onValueChange = vm::alterarNovaSubtarefa,
                label = { Text("Nova subtarefa") },
                singleLine = true,
                isError = estado.erroSubtarefa != null,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = estado.novaSubtarefaDuracao,
                onValueChange = vm::alterarNovaSubtarefaDuracao,
                label = { Text("min") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(0.4f),
            )
        }
        estado.erroSubtarefa?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = { vm.escopo.launch { vm.criarSubtarefa() } },
            enabled = !estado.criandoSubtarefa,
        ) { Text(if (estado.criandoSubtarefa) "Adicionando…" else "Adicionar subtarefa") }
    }
}
