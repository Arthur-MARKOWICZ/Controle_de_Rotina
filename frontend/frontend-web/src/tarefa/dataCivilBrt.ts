import { dataHojeIso } from '../planner/data'
import { instantParaDatetimeLocal } from './datetime'

/** Data civil (ano, mês 1–12, dia). */
export type DataCivil = { y: number; m: number; d: number }

const pad = (n: number) => String(n).padStart(2, '0')

/** Data civil de `ref` em America/Sao_Paulo. */
export function partesCivisBrt(ref: Date): DataCivil {
  const [y, m, d] = dataHojeIso(ref).split('-').map(Number)
  return { y, m, d }
}

export function adicionarDiasCivil(y: number, m: number, d: number, dias: number): DataCivil {
  const utc = new Date(Date.UTC(y, m - 1, d + dias))
  return { y: utc.getUTCFullYear(), m: utc.getUTCMonth() + 1, d: utc.getUTCDate() }
}

/** Weekday ISO 1=segunda … 7=domingo da data civil. */
export function weekdayIsoBrt(y: number, m: number, d: number): number {
  const dia = new Date(Date.UTC(y, m - 1, d)).getUTCDay()
  return dia === 0 ? 7 : dia
}

/** Próxima data com o weekday ISO pedido, estritamente depois da data informada. */
export function proximoDiaSemana(y: number, m: number, d: number, wdIso: number): DataCivil {
  if (!Number.isInteger(wdIso) || wdIso < 1 || wdIso > 7) {
    throw new RangeError(`Dia da semana inválido: ${wdIso}`)
  }
  const atual = weekdayIsoBrt(y, m, d)
  const add = (wdIso - atual + 7) % 7 || 7
  return adicionarDiasCivil(y, m, d, add)
}

/** Se (y, m, d) é uma data civil existente. */
export function dataCivilExiste(y: number, m: number, d: number): boolean {
  const utc = new Date(Date.UTC(y, m - 1, d))
  return utc.getUTCFullYear() === y && utc.getUTCMonth() === m - 1 && utc.getUTCDate() === d
}

/** Instante ISO de 23:59 BRT do dia civil. */
export function fimDoDiaBrtIso(y: number, m: number, d: number): string {
  return new Date(`${y}-${pad(m)}-${pad(d)}T23:59:00-03:00`).toISOString()
}

/** Fim do dia civil em BRT (23:59) → datetime-local do browser. */
export function fimDoDiaBrtParaDatetimeLocal(y: number, m: number, d: number): string {
  return instantParaDatetimeLocal(fimDoDiaBrtIso(y, m, d))
}
