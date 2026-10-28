import { dataHojeIso } from '../planner/data'
import { instantParaDatetimeLocal } from './datetime'

export type PrazoAtalhoId = 'hoje' | 'amanha' | 'em_3_dias' | 'proxima_segunda'

export type PrazoAtalho = {
  id: PrazoAtalhoId
  label: string
  /** Valor para input datetime-local. */
  prazoLocal: string
}

const BRT = 'America/Sao_Paulo'

function partesCivisBrt(ref: Date): { y: number; m: number; d: number } {
  const iso = dataHojeIso(ref)
  const [y, m, d] = iso.split('-').map(Number)
  return { y, m, d }
}

function adicionarDiasCivil(y: number, m: number, d: number, dias: number): {
  y: number
  m: number
  d: number
} {
  const utc = new Date(Date.UTC(y, m - 1, d + dias))
  return { y: utc.getUTCFullYear(), m: utc.getUTCMonth() + 1, d: utc.getUTCDate() }
}

/** Weekday ISO 1=segunda … 7=domingo da data civil em BRT. */
function weekdayIsoBrt(y: number, m: number, d: number): number {
  const meioDiaBrt = new Date(
    `${String(y).padStart(4, '0')}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}T12:00:00-03:00`,
  )
  const wd = new Intl.DateTimeFormat('en-US', {
    timeZone: BRT,
    weekday: 'short',
  }).format(meioDiaBrt)
  const map: Record<string, number> = {
    Mon: 1,
    Tue: 2,
    Wed: 3,
    Thu: 4,
    Fri: 5,
    Sat: 6,
    Sun: 7,
  }
  return map[wd] ?? 1
}

function proximaSegunda(y: number, m: number, d: number): { y: number; m: number; d: number } {
  const wd = weekdayIsoBrt(y, m, d)
  const add = wd === 1 ? 7 : (1 - wd + 7) % 7
  return adicionarDiasCivil(y, m, d, add)
}

/** Fim do dia civil em BRT (23:59) → datetime-local do browser. */
export function fimDoDiaBrtParaDatetimeLocal(y: number, m: number, d: number): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  const instant = new Date(`${y}-${pad(m)}-${pad(d)}T23:59:00-03:00`)
  return instantParaDatetimeLocal(instant.toISOString())
}

export function atalhosPrazo(ref: Date = new Date()): PrazoAtalho[] {
  const hoje = partesCivisBrt(ref)
  const amanha = adicionarDiasCivil(hoje.y, hoje.m, hoje.d, 1)
  const em3 = adicionarDiasCivil(hoje.y, hoje.m, hoje.d, 3)
  const segunda = proximaSegunda(hoje.y, hoje.m, hoje.d)

  return [
    { id: 'hoje', label: 'Hoje', prazoLocal: fimDoDiaBrtParaDatetimeLocal(hoje.y, hoje.m, hoje.d) },
    {
      id: 'amanha',
      label: 'Amanhã',
      prazoLocal: fimDoDiaBrtParaDatetimeLocal(amanha.y, amanha.m, amanha.d),
    },
    {
      id: 'em_3_dias',
      label: 'Em 3 dias',
      prazoLocal: fimDoDiaBrtParaDatetimeLocal(em3.y, em3.m, em3.d),
    },
    {
      id: 'proxima_segunda',
      label: 'Próxima segunda',
      prazoLocal: fimDoDiaBrtParaDatetimeLocal(segunda.y, segunda.m, segunda.d),
    },
  ]
}
