import { lerErroApi } from '../../auth/api/lerErroApi'
import { apiFetch, readApiJson } from '../../auth/session/apiClient'
import type { StatusPlano } from '../../planner/api/planoApi'
import type { OrigemCapacidade } from '../../capacidade/api/capacidadeResolvidaApi'
import type { Prioridade, StatusTarefa } from '../../tarefa/validation/tarefaForm'

export type CalendarioBloco = {
  id: string
  tarefaId: string
  titulo: string
  duracaoMinutos: number
  ordem: number
  prioridade: Prioridade
}

export type CalendarioPlano = {
  id: string
  versao: number
  temConflito: boolean
  blocos: CalendarioBloco[]
}

export type CalendarioTarefaPrazo = {
  id: string
  titulo: string
  prazo: string
  status: StatusTarefa
  prioridade: Prioridade
  duracaoMinutos: number
}

export type CalendarioDia = {
  data: string
  capacidadeMinutos: number
  origemCapacidade: OrigemCapacidade
  minutosAlocados: number
  sobrecarga: boolean
  statusPlano: StatusPlano | null
  plano: CalendarioPlano | null
  tarefasComPrazo: CalendarioTarefaPrazo[]
}

export type CalendarioResponse = {
  periodo: { inicio: string; fim: string }
  dias: CalendarioDia[]
}

export function buildCalendarioUrl(dataDe: string, dataAte: string): string {
  const params = new URLSearchParams({ dataDe, dataAte })
  return `/calendario?${params.toString()}`
}

export async function consultarCalendario(
  dataDe: string,
  dataAte: string,
): Promise<CalendarioResponse> {
  const response = await apiFetch(buildCalendarioUrl(dataDe, dataAte))
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível carregar o calendário.')
  }
  return readApiJson(response)
}
