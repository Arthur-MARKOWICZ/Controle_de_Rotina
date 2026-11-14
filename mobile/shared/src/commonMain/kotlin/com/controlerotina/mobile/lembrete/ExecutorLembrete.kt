package com.controlerotina.mobile.lembrete

import com.controlerotina.mobile.api.ApiErro
import com.controlerotina.mobile.api.ExecucoesDoDia
import com.controlerotina.mobile.api.FiltroTarefas
import com.controlerotina.mobile.api.RotinaApi
import com.controlerotina.mobile.api.StatusPlano
import com.controlerotina.mobile.dominio.Notificacao
import com.controlerotina.mobile.dominio.STATUS_AVISO_PRAZO
import com.controlerotina.mobile.dominio.TipoLembrete
import com.controlerotina.mobile.dominio.diaDoDisparo
import com.controlerotina.mobile.dominio.intervaloDiaSeguinte
import com.controlerotina.mobile.dominio.montarAvisoPrazo
import com.controlerotina.mobile.dominio.montarResumo
import com.controlerotina.mobile.dominio.no
import com.controlerotina.mobile.dominio.proximoDisparo
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

/** Tamanho da página do aviso de prazo; a contagem vem de `totalElements`. */
const val TAMANHO_PAGINA_AVISO = 50

enum class MotivoPulo { DESLIGADO, FORA_DA_JANELA, JA_DISPAROU, SEM_PERMISSAO, SEM_SESSAO, FALHA_API, NADA_A_AVISAR }

sealed interface ResultadoLembrete {
    data object Exibida : ResultadoLembrete
    data class Pulada(val motivo: MotivoPulo) : ResultadoLembrete
}

/**
 * Uma execução de lembrete (design L4): decide se dispara, busca na API, monta e exibe.
 * Nunca lança erro de API nem exibe notificação de erro (spec "Falha silenciosa").
 * Reagenda o próximo dia, exceto quando o lembrete está desligado ou a sessão acabou
 * (nesse caso o logout já cancela tudo e o próximo login reagenda).
 */
class ExecutorLembrete(
    private val api: RotinaApi,
    private val preferencias: PreferenciasLembretes,
    private val notificador: Notificador,
    private val agendador: AgendadorLembretes,
    private val clock: Clock = Clock.System,
    private val fuso: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    suspend fun executar(tipo: TipoLembrete): ResultadoLembrete {
        if (!preferencias.ligado(tipo)) return ResultadoLembrete.Pulada(MotivoPulo.DESLIGADO)
        val agora = clock.now()
        val fusoAtual = fuso()
        val horario = preferencias.horario(tipo)
        val dia = diaDoDisparo(agora, horario, fusoAtual)

        val resultado = decidir(tipo, dia)
        if (resultado != ResultadoLembrete.Pulada(MotivoPulo.SEM_SESSAO)) {
            // A partir do alvo deste disparo, para uma execução adiantada não repetir hoje.
            val base = dia?.no(horario, fusoAtual)?.let { maxOf(it, agora) } ?: agora
            agendador.agendar(tipo, proximoDisparo(base, horario, fusoAtual))
        }
        return resultado
    }

    private suspend fun decidir(tipo: TipoLembrete, dia: LocalDate?): ResultadoLembrete {
        if (dia == null) return ResultadoLembrete.Pulada(MotivoPulo.FORA_DA_JANELA)
        if (preferencias.ultimoDia(tipo) == dia.toString()) return ResultadoLembrete.Pulada(MotivoPulo.JA_DISPAROU)
        if (!notificador.permitido()) return ResultadoLembrete.Pulada(MotivoPulo.SEM_PERMISSAO)

        val notificacao = try {
            conteudo(tipo, dia)
        } catch (e: CancellationException) {
            throw e
        } catch (_: ApiErro.SessaoExpirada) {
            return ResultadoLembrete.Pulada(MotivoPulo.SEM_SESSAO)
        } catch (_: Exception) {
            // Rede, HTTP, JSON inválido (ex.: HTML de proxy): nada confiável para mostrar.
            return ResultadoLembrete.Pulada(MotivoPulo.FALHA_API)
        } ?: return ResultadoLembrete.Pulada(MotivoPulo.NADA_A_AVISAR)

        notificador.exibir(notificacao)
        preferencias.marcarDisparo(tipo, dia)
        return ResultadoLembrete.Exibida
    }

    private suspend fun conteudo(tipo: TipoLembrete, dia: LocalDate): Notificacao? = when (tipo) {
        TipoLembrete.RESUMO -> {
            val data = dia.toString()
            val plano = api.plano(data)
            val execucoes = if (plano?.status == StatusPlano.APROVADO) api.execucoes(data) else ExecucoesDoDia()
            montarResumo(plano, execucoes)
        }
        TipoLembrete.PRAZO -> {
            val (de, ate) = intervaloDiaSeguinte(dia, fuso())
            val pagina = api.tarefas(
                0,
                TAMANHO_PAGINA_AVISO,
                FiltroTarefas(status = STATUS_AVISO_PRAZO, prazoDe = de.toString(), prazoAte = ate.toString()),
            )
            montarAvisoPrazo(pagina.content, pagina.totalElements)
        }
    }
}
