import { dataHojeIso } from '../planner/data'
import { diffDiasCivis } from '../home/justificativa'
import { ordenarPorTagMatch } from '../tag/tagMatch'
import type { TarefaResponse } from './api/tarefaApi'
import type { StatusTarefa } from './validation/tarefaForm'

export type GrupoPrazoId =
  | 'atrasadas'
  | 'anteriores'
  | 'hoje'
  | 'esta_semana'
  | 'depois'
  | 'sem_prazo'

export type GrupoPrazo = {
  id: GrupoPrazoId
  label: string
  tarefas: TarefaResponse[]
}

const GRUPO_LABEL: Record<GrupoPrazoId, string> = {
  atrasadas: 'Atrasadas',
  anteriores: 'Anteriores',
  hoje: 'Hoje',
  esta_semana: 'Esta semana',
  depois: 'Depois',
  sem_prazo: 'Sem prazo',
}

const ORDEM_GRUPOS: GrupoPrazoId[] = [
  'atrasadas',
  'anteriores',
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
export function formatarPrazoLista(
  prazoIso: string | null | undefined,
  ref: Date = new Date(),
  status?: StatusTarefa,
): string {
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
  if (status === 'FINALIZADA') {
    if (dias === -1) {
      return 'há 1 dia'
    }
    return `há ${Math.abs(dias)} dias`
  }
  if (dias === -1) {
    return 'atrasada há 1 dia'
  }
  return `atrasada há ${Math.abs(dias)} dias`
}

export function classificarGrupoPrazo(
  prazoIso: string | null | undefined,
  ref: Date = new Date(),
  status?: StatusTarefa,
): GrupoPrazoId {
  if (!prazoIso) {
    return 'sem_prazo'
  }
  const hoje = dataHojeIso(ref)
  const prazoDia = dataCivilIso(new Date(prazoIso))
  const dias = diffDiasCivis(hoje, prazoDia)

  if (dias < 0) {
    // Finalizada com prazo passado não é atraso — só histórico.
    return status === 'FINALIZADA' ? 'anteriores' : 'atrasadas'
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
  filtroTagIds: string[] = [],
): GrupoPrazo[] {
  const buckets: Record<GrupoPrazoId, TarefaResponse[]> = {
    atrasadas: [],
    anteriores: [],
    hoje: [],
    esta_semana: [],
    depois: [],
    sem_prazo: [],
  }

  for (const tarefa of tarefas) {
    const grupo = classificarGrupoPrazo(tarefa.prazo, ref, tarefa.status)
    buckets[grupo].push(tarefa)
  }

  return ORDEM_GRUPOS.filter((id) => incluirVazios || buckets[id].length > 0).map((id) => ({
    id,
    label: GRUPO_LABEL[id],
    tarefas: ordenarPorTagMatch(buckets[id], filtroTagIds),
  }))
}
