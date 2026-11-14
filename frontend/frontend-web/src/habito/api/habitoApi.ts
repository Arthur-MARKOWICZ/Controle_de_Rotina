import { lerErroApi } from '../../auth/api/lerErroApi'
import { apiFetch, readApiJson } from '../../auth/session/apiClient'
import type { TagResumo } from '../../tag/api/tagApi'
import type { DiaSemana } from '../../capacidade/api/capacidadeSemanalApi'
import type { ExecucaoResponse, StatusExecucao } from '../../execucao/api/execucaoApi'

export type StatusHabito = 'ATIVO' | 'PAUSADO'

export type HabitoResponse = {
  id: string
  titulo: string
  descricao: string | null
  duracaoMinutos: number
  diasSemana: DiaSemana[]
  horarioPreferencial: string | null
  status: StatusHabito
  streakAtual: number
  melhorStreak: number
  createdAt: string
  updatedAt: string
  deletedAt: string | null
  deletada: boolean
  tags?: TagResumo[]
  tagMatchScore?: number | null
  /** Posição manual na lista. Ausente em payloads antigos. */
  ordem?: number
}

export type PageHabito = {
  content: HabitoResponse[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export type ListarHabitosParams = {
  status?: StatusHabito
  incluirDeletadas?: boolean
  somenteDeletadas?: boolean
  page?: number
  size?: number
  tagIds?: string[]
}

export type CriarHabitoRequest = {
  titulo: string
  descricao?: string | null
  duracaoMinutos: number
  diasSemana: DiaSemana[]
  horarioPreferencial?: string | null
  tagIds?: string[]
}

export type AtualizarHabitoRequest = {
  titulo?: string
  descricao?: string | null
  duracaoMinutos?: number
  diasSemana?: DiaSemana[]
  horarioPreferencial?: string | null
  tagIds?: string[]
}

function appendAll(params: URLSearchParams, key: string, values?: string[]) {
  if (!values?.length) {
    return
  }
  for (const value of values) {
    params.append(key, value)
  }
}

export function buildHabitosQuery(filtro: ListarHabitosParams = {}): string {
  const params = new URLSearchParams()
  if (filtro.status) {
    params.set('status', filtro.status)
  }
  if (filtro.incluirDeletadas != null) {
    params.set('incluirDeletadas', String(filtro.incluirDeletadas))
  }
  if (filtro.somenteDeletadas != null) {
    params.set('somenteDeletadas', String(filtro.somenteDeletadas))
  }
  appendAll(params, 'tagId', filtro.tagIds)
  params.set('page', String(filtro.page ?? 0))
  params.set('size', String(filtro.size ?? 20))
  return `/habitos?${params.toString()}`
}

export async function listarHabitos(filtro: ListarHabitosParams = {}): Promise<PageHabito> {
  const response = await apiFetch(buildHabitosQuery(filtro))
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível carregar os hábitos.')
  }
  return (await response.json()) as PageHabito
}

export async function criarHabito(body: CriarHabitoRequest): Promise<HabitoResponse> {
  const response = await apiFetch('/habitos', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível criar o hábito.')
  }
  return (await response.json()) as HabitoResponse
}

export async function buscarHabito(id: string): Promise<HabitoResponse> {
  const response = await apiFetch(`/habitos/${id}`)
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível carregar o hábito.')
  }
  return (await response.json()) as HabitoResponse
}

export async function atualizarHabito(
  id: string,
  body: AtualizarHabitoRequest,
): Promise<HabitoResponse> {
  const response = await apiFetch(`/habitos/${id}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível atualizar o hábito.')
  }
  return (await response.json()) as HabitoResponse
}

export async function pausarHabito(id: string): Promise<HabitoResponse> {
  const response = await apiFetch(`/habitos/${id}/pausar`, { method: 'POST' })
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível pausar o hábito.')
  }
  return (await response.json()) as HabitoResponse
}

export async function reativarHabito(id: string): Promise<HabitoResponse> {
  const response = await apiFetch(`/habitos/${id}/reativar`, { method: 'POST' })
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível reativar o hábito.')
  }
  return (await response.json()) as HabitoResponse
}

export async function excluirHabito(id: string): Promise<void> {
  const response = await apiFetch(`/habitos/${id}`, { method: 'DELETE' })
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível excluir o hábito.')
  }
}

export async function restaurarHabito(id: string): Promise<HabitoResponse> {
  const response = await apiFetch(`/habitos/${id}/restore`, { method: 'POST' })
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível restaurar o hábito.')
  }
  return (await response.json()) as HabitoResponse
}

export async function reordenarHabitos(ids: string[]): Promise<void> {
  const response = await apiFetch('/habitos/ordem', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ ids }),
  })
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível reordenar os hábitos.')
  }
}

export type SlotExecucaoHabito = {
  blocoId: string
  execucaoId: string | null
  status: StatusExecucao
  iniciadoEm: string | null
}

export type HabitoDoDia = {
  id: string
  titulo: string
  duracaoMinutos: number
  horarioPreferencial: string | null
  streakAtual: number
  cumprido: boolean
  noPlanoAprovado: boolean
  execucaoAvulsa: SlotExecucaoHabito | null
  execucaoNoPlano: SlotExecucaoHabito | null
}

export async function listarHabitosDoDia(data?: string): Promise<HabitoDoDia[]> {
  const params = new URLSearchParams()
  if (data) {
    params.set('data', data)
  }
  const query = params.toString()
  const response = await apiFetch(query ? `/habitos/do-dia?${query}` : '/habitos/do-dia')
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível carregar os hábitos do dia.')
  }
  return readApiJson<HabitoDoDia[]>(response)
}

export async function iniciarHabitoAvulso(
  habitoId: string,
  data?: string,
): Promise<ExecucaoResponse> {
  const params = new URLSearchParams()
  if (data) {
    params.set('data', data)
  }
  const query = params.toString()
  const response = await apiFetch(
    query ? `/habitos/${habitoId}/iniciar?${query}` : `/habitos/${habitoId}/iniciar`,
    { method: 'POST' },
  )
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível iniciar o hábito.')
  }
  return readApiJson<ExecucaoResponse>(response)
}

/**
 * Conclui o hábito sem timer. `minutos` é o tempo que o usuário diz ter dedicado;
 * sem ele o backend usa a duração do hábito.
 */
export async function concluirHabitoDireto(
  habitoId: string,
  data?: string,
  minutos?: number,
): Promise<ExecucaoResponse> {
  const params = new URLSearchParams()
  if (data) {
    params.set('data', data)
  }
  const query = params.toString()
  const init: RequestInit =
    minutos === undefined
      ? { method: 'POST' }
      : {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ minutos }),
        }
  const response = await apiFetch(
    query
      ? `/habitos/${habitoId}/concluir-direto?${query}`
      : `/habitos/${habitoId}/concluir-direto`,
    init,
  )
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível concluir o hábito.')
  }
  return readApiJson<ExecucaoResponse>(response)
}

export type OrigemTempo = 'TIMER' | 'INFORMADO' | 'MISTO'

export type HabitoHistoricoDia = {
  data: string
  minutosDedicados: number
  metaMinutos: number
  suficiente: boolean
  origem: OrigemTempo
}

/** Cumprimentos do hábito por dia; sem datas, o backend usa os últimos 84 dias (BRT). */
export async function buscarHistoricoHabito(
  habitoId: string,
  dataDe?: string,
  dataAte?: string,
): Promise<HabitoHistoricoDia[]> {
  const params = new URLSearchParams()
  if (dataDe) {
    params.set('dataDe', dataDe)
  }
  if (dataAte) {
    params.set('dataAte', dataAte)
  }
  const query = params.toString()
  const response = await apiFetch(
    query ? `/habitos/${habitoId}/historico?${query}` : `/habitos/${habitoId}/historico`,
  )
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível carregar o histórico.')
  }
  return readApiJson<HabitoHistoricoDia[]>(response)
}
