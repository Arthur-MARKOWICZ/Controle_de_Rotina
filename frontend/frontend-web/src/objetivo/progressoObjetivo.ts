import { formatarCapacidadeMinutos } from '../planner/data'
import type { ObjetivoResponse } from './api/objetivoApi'

export type ProgressoObjetivo = NonNullable<ObjetivoResponse['progresso']>

export type ProgressoNormalizado = {
  /** Percentual inteiro limitado a 0–100. */
  pct: number
  /** Ex.: "2h de 5h concluídas". Os minutos ficam brutos, sem limite. */
  texto: string
}

/**
 * Prepara o progresso da API para exibição. Devolve `null` quando não há o que medir
 * (progresso nulo ou total de minutos ≤ 0): nesse caso a tela não mostra barra nem 0%.
 * O percentual é o da API (já com piso no backend); aqui só se limita a 0–100.
 */
export function normalizarProgresso(
  progresso: ObjetivoResponse['progresso'] | undefined,
): ProgressoNormalizado | null {
  if (!progresso || !(progresso.minutosTotais > 0)) {
    return null
  }
  const bruto = Number.isFinite(progresso.percentual) ? progresso.percentual : 0
  const pct = Math.min(100, Math.max(0, Math.round(bruto)))
  const concluidos = formatarCapacidadeMinutos(progresso.minutosConcluidos)
  const totais = formatarCapacidadeMinutos(progresso.minutosTotais)
  return { pct, texto: `${concluidos} de ${totais} concluídas` }
}
