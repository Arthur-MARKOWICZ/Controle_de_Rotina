package com.controlerotina.mobile.estado

import com.controlerotina.mobile.api.ApiErro
import com.controlerotina.mobile.api.BlocoPlano
import com.controlerotina.mobile.api.CapacidadeSemanal
import com.controlerotina.mobile.api.CriarCapacidadeSemanalItem
import com.controlerotina.mobile.api.DiaSemana
import com.controlerotina.mobile.api.Execucao
import com.controlerotina.mobile.api.Plano
import com.controlerotina.mobile.api.RotinaApi
import com.controlerotina.mobile.api.StatusExecucao
import com.controlerotina.mobile.api.StatusPlano
import com.controlerotina.mobile.dominio.AcaoBloco
import com.controlerotina.mobile.dominio.acoesDoBloco
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.coroutines.cancellation.CancellationException

data class BlocoNaTela(
    val bloco: BlocoPlano,
    val execucao: Execucao?,
    val acoes: Set<AcaoBloco>,
) {
    val status: StatusExecucao get() = execucao?.status ?: StatusExecucao.NAO_INICIADO
}

/** Horas do preset "Dias úteis 4h" (o mesmo da web e do padrão de contas novas). */
const val HORAS_PRESET_DIAS_UTEIS = 4
const val HORAS_MAX_DIA = 16
const val MSG_CAPACIDADE_MUDOU = "A capacidade mudou, tente de novo."

private val DIAS_UTEIS = listOf(DiaSemana.SEGUNDA, DiaSemana.TERCA, DiaSemana.QUARTA, DiaSemana.QUINTA, DiaSemana.SEXTA)

/** Seg–sex no preset, fim de semana em 0. */
val SEMANA_DIAS_UTEIS: Map<DiaSemana, Int> =
    DiaSemana.entries.associateWith { if (it in DIAS_UTEIS) HORAS_PRESET_DIAS_UTEIS else 0 }

/** O que gravar para chegar em [horasPorDia]: `POST` em lote para dias novos com horas, `PATCH` para os que mudaram. */
data class AlteracoesCapacidade(
    val criar: List<CriarCapacidadeSemanalItem>,
    val atualizar: List<Pair<String, Int>>,
)

fun alteracoesCapacidade(semana: List<CapacidadeSemanal>, horasPorDia: Map<DiaSemana, Int>): AlteracoesCapacidade {
    val criar = mutableListOf<CriarCapacidadeSemanalItem>()
    val atualizar = mutableListOf<Pair<String, Int>>()
    for (dia in DiaSemana.entries) {
        val horas = horasPorDia[dia] ?: continue
        val existente = semana.firstOrNull { it.ativo && it.diaSemana == dia }
        when {
            existente == null -> if (horas > 0) criar += CriarCapacidadeSemanalItem(dia, horas)
            existente.horas != horas -> atualizar += existente.id to horas
        }
    }
    return AlteracoesCapacidade(criar, atualizar)
}

sealed interface EstadoHoje {
    data object Carregando : EstadoHoje

    /**
     * Dia sem plano. Com [pedindoCapacidade] a tela abre o sheet "Quanto tempo você tem por dia?"
     * no lugar de gerar (usuário sem nenhuma hora configurada).
     */
    data class SemPlano(
        val gerando: Boolean = false,
        val pedindoCapacidade: Boolean = false,
        val horasPorDia: Map<DiaSemana, Int> = emptyMap(),
        val salvandoCapacidade: Boolean = false,
        val erroCapacidade: String? = null,
    ) : EstadoHoje
    data class Conteudo(val plano: Plano, val blocos: List<BlocoNaTela>) : EstadoHoje
    data class Erro(val mensagem: String) : EstadoHoje
}

/** Tela Hoje: plano do dia, gerar/aprovar e execução dos blocos (spec mobile-hoje). */
class HojeViewModel(
    private val api: RotinaApi,
    private val sessaoAtiva: SessaoAtivaStore,
    private val hoje: () -> String,
    val escopo: CoroutineScope,
) {
    private val _estado = MutableStateFlow<EstadoHoje>(EstadoHoje.Carregando)
    val estado: StateFlow<EstadoHoje> = _estado.asStateFlow()

    private val _atualizando = MutableStateFlow(false)
    val atualizando: StateFlow<Boolean> = _atualizando.asStateFlow()

    /** Id do bloco (ou "plano") com ação em andamento, para desabilitar o botão. */
    private val _ocupado = MutableStateFlow<String?>(null)
    val ocupado: StateFlow<String?> = _ocupado.asStateFlow()

    /** Template semanal lido pela guarda; base para decidir entre `POST` e `PATCH`. */
    private var semana: List<CapacidadeSemanal> = emptyList()

    /** Volta ao estado inicial, sem nada do usuário anterior na memória. */
    fun limpar() {
        _estado.value = EstadoHoje.Carregando
        _atualizando.value = false
        _ocupado.value = null
        semana = emptyList()
    }

    suspend fun carregar(puxando: Boolean = false) {
        if (puxando || _estado.value is EstadoHoje.Conteudo) _atualizando.value = true
        else _estado.value = EstadoHoje.Carregando
        try {
            val data = hoje()
            val plano = api.plano(data)
            _estado.value = if (plano == null) EstadoHoje.SemPlano() else montar(plano, data)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiErro) {
            if (_estado.value is EstadoHoje.Conteudo) sessaoAtiva.avisar(mensagemDeErro(e))
            else _estado.value = EstadoHoje.Erro(mensagemDeErro(e))
        } finally {
            _atualizando.value = false
        }
    }

    private suspend fun montar(plano: Plano, data: String): EstadoHoje.Conteudo {
        val execucoes = if (plano.status == StatusPlano.APROVADO) {
            api.execucoes(data).itens.associate { it.bloco.id to it.execucao }
        } else {
            emptyMap()
        }
        val blocos = plano.blocos.sortedBy { it.ordem }.map { bloco ->
            val exec = execucoes[bloco.id]
            BlocoNaTela(bloco, exec, acoesDoBloco(plano.status, exec?.status))
        }
        return EstadoHoje.Conteudo(plano, blocos)
    }

    /**
     * Gera o plano do dia. Sem capacidade configurada (dia em 0h e nenhum dia do template com horas),
     * não chama a API: pede a capacidade primeiro. Fim de semana com template não bloqueia.
     */
    suspend fun gerar() {
        val atual = _estado.value as? EstadoHoje.SemPlano ?: return
        if (atual.gerando || atual.salvandoCapacidade) return
        _estado.value = EstadoHoje.SemPlano(gerando = true)
        val data = hoje()
        if (faltaCapacidade(data)) {
            _estado.value = EstadoHoje.SemPlano(
                pedindoCapacidade = true,
                horasPorDia = DiaSemana.entries.associateWith { dia ->
                    semana.firstOrNull { it.ativo && it.diaSemana == dia }?.horas ?: 0
                },
            )
            return
        }
        gerarAgora(data)
    }

    private suspend fun gerarAgora(data: String) {
        _estado.value = EstadoHoje.SemPlano(gerando = true)
        try {
            _estado.value = montar(api.gerarPlano(data), data)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiErro) {
            _estado.value = EstadoHoje.SemPlano()
            sessaoAtiva.avisar(mensagemDeErro(e))
        }
    }

    /** Se uma das leituras falhar, segue gerando (comportamento anterior) em vez de travar. */
    private suspend fun faltaCapacidade(data: String): Boolean = try {
        val doDia = api.capacidadeResolvida(data, data).firstOrNull { it.data == data }?.horas ?: 0
        if (doDia > 0) {
            false
        } else {
            semana = api.capacidadesSemanais()
            semana.none { it.ativo && it.horas > 0 }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: ApiErro) {
        false
    }

    /** "Usar dias úteis 4h": seg–sex 4h, fim de semana em 0, e gera. */
    suspend fun usarDiasUteis() = salvarSemana(SEMANA_DIAS_UTEIS)

    /** Grava o stepper do sheet e, no sucesso, gera o plano (um toque até o plano). */
    suspend fun salvarSemana(horasPorDia: Map<DiaSemana, Int>) {
        val atual = _estado.value as? EstadoHoje.SemPlano ?: return
        if (!atual.pedindoCapacidade || atual.salvandoCapacidade) return
        val horas = horasPorDia.mapValues { (_, h) -> h.coerceIn(0, HORAS_MAX_DIA) }
        _estado.value = atual.copy(horasPorDia = horas, salvandoCapacidade = true, erroCapacidade = null)
        try {
            val (criar, atualizar) = alteracoesCapacidade(semana, horas)
            if (criar.isNotEmpty()) api.criarCapacidadesSemanais(criar)
            atualizar.forEach { (id, h) -> api.atualizarCapacidadeSemanal(id, h) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiErro) {
            val conflito = e is ApiErro.Http && e.status == 409
            if (conflito) {
                semana = try {
                    api.capacidadesSemanais()
                } catch (_: ApiErro) {
                    semana
                }
            }
            _estado.value = atual.copy(
                horasPorDia = horas,
                erroCapacidade = if (conflito) MSG_CAPACIDADE_MUDOU else mensagemDeErro(e),
            )
            return
        }
        gerarAgora(hoje())
    }

    /** Fechar o sheet sem escolher: nada é gravado, nada é gerado. */
    fun fecharCapacidade() {
        val atual = _estado.value as? EstadoHoje.SemPlano ?: return
        if (atual.salvandoCapacidade) return
        _estado.value = EstadoHoje.SemPlano()
    }

    suspend fun aprovar() {
        val atual = _estado.value as? EstadoHoje.Conteudo ?: return
        if (atual.plano.status != StatusPlano.RASCUNHO) return
        // Nada para aprovar: o back devolveria 409 Plano vazio.
        if (atual.plano.blocos.isEmpty()) return
        acao("plano") {
            val aprovado = api.aprovarPlano(atual.plano.id)
            _estado.value = montar(aprovado, hoje())
            sessaoAtiva.avisar("Plano aprovado")
        }
    }

    suspend fun iniciar(item: BlocoNaTela) {
        if (AcaoBloco.INICIAR !in item.acoes) return
        acao(item.bloco.id) {
            val execucao = api.iniciarBloco(item.bloco.id)
            sessaoAtiva.registrarInicio(item.bloco.titulo, item.bloco.duracaoMinutos, execucao)
            carregar()
        }
    }

    suspend fun concluir(item: BlocoNaTela) {
        val id = item.execucao?.id ?: return
        if (AcaoBloco.CONCLUIR !in item.acoes) return
        acao(item.bloco.id) {
            val resultado = api.concluirExecucao(id)
            sessaoAtiva.avisar(mensagemConclusao(resultado.xp))
            if (sessaoAtiva.sessao.value?.execucaoId == id) sessaoAtiva.limpar()
            sessaoAtiva.notificarMudanca()
            carregar()
        }
    }

    suspend fun abortar(item: BlocoNaTela) {
        val id = item.execucao?.id ?: return
        if (AcaoBloco.ABORTAR !in item.acoes) return
        acao(item.bloco.id) {
            api.abortarExecucao(id)
            if (sessaoAtiva.sessao.value?.execucaoId == id) sessaoAtiva.limpar()
            sessaoAtiva.notificarMudanca()
            carregar()
        }
    }

    private suspend fun acao(chave: String, bloco: suspend () -> Unit) {
        if (_ocupado.value != null) return
        _ocupado.value = chave
        try {
            bloco()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiErro) {
            sessaoAtiva.avisar(mensagemDeErro(e))
            carregar()
        } finally {
            _ocupado.update { if (it == chave) null else it }
        }
    }
}
