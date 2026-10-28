import { dataHojeIso } from '../planner/data'
import { diffDiasCivis } from '../home/justificativa'
import type { TarefaResponse } from './api/tarefaApi'

export type GrupoPrazoId = 'atrasadas' | 'hoje' | 'esta_semana' | 'depois' | 'sem_prazo'

export type GrupoPrazo = {
  id: GrupoPrazoId
  label: string
  tarefas: TarefaResponse[]
}

const GRUPO_LABEL: Record<GrupoPrazoId, string> = {
  atrasadas: 'Atrasadas',
  hoje: 'Hoje',
  esta_semana: 'Esta semana',
  depois: 'Depois',
  sem_prazo: 'Sem prazo',
}

const ORDEM_GRUPOS: GrupoPrazoId[] = [
  'atrasadas',
  'hoje',
  'esta_semana',
  'depois',
  'sem_prazo',
]

function dataCivilIso(instant: Date): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Sao_Paulo',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(instant)
}

function weekdayIsoBrt(ymd: string): number {
  const meioDia = new Date(`${ymd}T12:00:00-03:00`)
  const wd = new Intl.DateTimeFormat('en-US', {
    timeZone: 'America/Sao_Paulo',
    weekday: 'short',
  }).format(meioDia)
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

/** Domingo da semana civil BRT que contém `hojeYmd` (segunda–domingo). */
export function fimDaSemanaBrt(hojeYmd: string): string {
  const wd = weekdayIsoBrt(hojeYmd)
  const add = 7 - wd
  const [y, m, d] = hojeYmd.split('-').map(Number)
  const utc = new Date(Date.UTC(y, m - 1, d + add))
  const yy = utc.getUTCFullYear()
  const mm = String(utc.getUTCMonth() + 1).padStart(2, '0')
  const dd = String(utc.getUTCDate()).padStart(2, '0')
  return `${yy}-${mm}-${dd}`
}

/** Texto relativo curto para a lista (title guarda a data absoluta). */
export function formatarPrazoLista(prazoIso: string | null | undefined, ref: Date = new Date()): string {
  if (!prazoIso) {
    return 'Sem prazo'
  }
  const hoje = dataHojeIso(ref)
  const prazoDia = dataCivilIso(new Date(prazoIso))
  const dias = diffDiasCivis(hoje, prazoDia)

  if (dias === 0) {
    return 'hoje'
  }
  if (dias === 1) {
    return 'amanhã'
  }
  if (dias > 1) {
    return `em ${dias} dias`
  }
  if (dias === -1) {
    return 'atrasada há 1 dia'
  }
  return `atrasada há ${Math.abs(dias)} dias`
}

export function classificarGrupoPrazo(
  prazoIso: string | null | undefined,
  ref: Date = new Date(),
): GrupoPrazoId {
  if (!prazoIso) {
    return 'sem_prazo'
  }
  const hoje = dataHojeIso(ref)
  const prazoDia = dataCivilIso(new Date(prazoIso))
  const dias = diffDiasCivis(hoje, prazoDia)

  if (dias < 0) {
    return 'atrasadas'
  }
  if (dias === 0) {
    return 'hoje'
  }
  const fimSemana = fimDaSemanaBrt(hoje)
  if (diffDiasCivis(prazoDia, fimSemana) >= 0) {
    return 'esta_semana'
  }
  return 'depois'
}

/** Agrupa client-side; omite grupos vazios, exceto quando `incluirVazios`. */
export function agruparPorPrazo(
  tarefas: TarefaResponse[],
  ref: Date = new Date(),
  incluirVazios = false,
): GrupoPrazo[] {
  const buckets: Record<GrupoPrazoId, TarefaResponse[]> = {
    atrasadas: [],
    hoje: [],
    esta_semana: [],
    depois: [],
    sem_prazo: [],
  }

  for (const tarefa of tarefas) {
    const grupo = classificarGrupoPrazo(tarefa.prazo, ref)
    buckets[grupo].push(tarefa)
  }

  return ORDEM_GRUPOS.filter((id) => incluirVazios || buckets[id].length > 0).map((id) => ({
    id,
    label: GRUPO_LABEL[id],
    tarefas: buckets[id],
  }))
}
