import { dataHojeIso } from '../planner/data'
import type { Prioridade } from '../tarefa/validation/tarefaForm'
import { labelPrioridade } from './labels'

/** Data civil YYYY-MM-DD de um instante em America/Sao_Paulo (não depende do mock de “hoje”). */
function dataCivilIso(instant: Date): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Sao_Paulo',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(instant)
}

/** Diferença em dias civis (YYYY-MM-DD) entre duas datas ISO locais. */
export function diffDiasCivis(deIso: string, ateIso: string): number {
  const [y1, m1, d1] = deIso.split('-').map(Number)
  const [y2, m2, d2] = ateIso.split('-').map(Number)
  const a = Date.UTC(y1, m1 - 1, d1)
  const b = Date.UTC(y2, m2 - 1, d2)
  return Math.round((b - a) / 86_400_000)
}

/**
 * Texto relativo do prazo a partir de hoje (America/Sao_Paulo).
 * Aceita `null`: a API serializa ausência como JSON `null` (`string | null`),
 * não como `undefined` — tipar só `string` ou `string | undefined` quebra o `tsc`.
 */
export function formatarPrazoRelativo(
  prazoIso: string | null,
  ref: Date = new Date(),
): string {
  if (prazoIso == null) {
    return 'Sem prazo'
  }
  const hoje = dataHojeIso(ref)
  const prazoDia = dataCivilIso(new Date(prazoIso))
  const dias = diffDiasCivis(hoje, prazoDia)

  if (dias === 0) {
    return 'Prazo hoje'
  }
  if (dias === 1) {
    return 'Prazo amanhã'
  }
  if (dias > 1) {
    return `Prazo em ${dias} dias`
  }
  if (dias === -1) {
    return 'Atrasada há 1 dia'
  }
  return `Atrasada há ${Math.abs(dias)} dias`
}

/** Justificativa mínima do rascunho: prazo relativo + prioridade. */
export function formatarJustificativaBloco(
  prazoIso: string,
  prioridade: Prioridade,
  ref: Date = new Date(),
): string {
  return `${formatarPrazoRelativo(prazoIso, ref)} · prioridade ${labelPrioridade(prioridade).toLowerCase()}`
}

const PRIORIDADE_PESO: Record<Prioridade, number> = {
  BAIXA: 1,
  MEDIA: 2,
  ALTA: 3,
}

/** Menor prioridade; em empate, maior `ordem` (menos prioritária na lista). */
export function escolherBlocoMenorPrioridade<
  T extends { id: string; ordem: number; tarefa: { prioridade: Prioridade } },
>(blocos: T[]): T | null {
  if (blocos.length === 0) {
    return null
  }
  return blocos.reduce((menor, atual) => {
    const pesoMenor = PRIORIDADE_PESO[menor.tarefa.prioridade]
    const pesoAtual = PRIORIDADE_PESO[atual.tarefa.prioridade]
    if (pesoAtual < pesoMenor) {
      return atual
    }
    if (pesoAtual === pesoMenor && atual.ordem > menor.ordem) {
      return atual
    }
    return menor
  })
}
