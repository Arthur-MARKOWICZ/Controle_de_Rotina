package com.controlerotina.mobile.lembrete

import com.controlerotina.mobile.dominio.DestinoLembrete
import com.controlerotina.mobile.estado.Aba
import kotlin.time.Duration
import kotlin.time.Instant

// Decisões da casca Android dos lembretes, puras para testar na JVM (design L5, L6).
// O androidApp só lê o estado do sistema e chama estas funções.

/** Android 13 (TIRAMISU): a partir daqui notificação exige permissão em tempo de execução. */
const val API_PERMISSAO_NOTIFICACAO = 33

/** Pode mostrar notificação: o usuário não bloqueou o app e, no 13+, concedeu a permissão. */
fun notificacaoPermitida(sdk: Int, permissaoConcedida: Boolean, notificacoesAtivas: Boolean): Boolean =
    notificacoesAtivas && (sdk < API_PERMISSAO_NOTIFICACAO || permissaoConcedida)

/** Ligar um lembrete abre o diálogo do sistema? Só no 13+ e só se a permissão ainda não existe. */
fun precisaDialogoDePermissao(sdk: Int, permissaoConcedida: Boolean): Boolean =
    sdk >= API_PERMISSAO_NOTIFICACAO && !permissaoConcedida

/** Atraso até [em] para o agendador; nunca negativo (horário já passou = executa já). */
fun atrasoAte(em: Instant, agora: Instant): Duration =
    (em - agora).coerceAtLeast(Duration.ZERO)

/** Extra da notificação → destino; `null` se ausente ou desconhecido (intent de outra versão). */
fun destinoDoExtra(extra: String?): DestinoLembrete? =
    DestinoLembrete.entries.firstOrNull { it.name == extra }

/** Aba aberta pelo toque na notificação (spec "Abrir o app pela notificação"). */
fun abaDoDestino(destino: DestinoLembrete): Aba = when (destino) {
    DestinoLembrete.PLANO -> Aba.PLANO
    DestinoLembrete.TAREFAS -> Aba.TAREFAS
}
