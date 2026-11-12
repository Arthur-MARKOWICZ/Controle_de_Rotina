import { lerErroApi } from '../../auth/api/lerErroApi'
import { apiFetch, readApiJson } from '../../auth/session/apiClient'
import type { ExecucaoResponse } from '../../execucao/api/execucaoApi'
import type { CriarEsperaRequest, EsperaResumo } from '../../pendencia/api/espera'
import type { TagResumo } from '../../tag/api/tagApi'
import type { Prioridade, StatusTarefa } from '../validation/tarefaForm'

export type TarefaResponse = {
  id: string
  titulo: string
  descricao: string
  prioridade: Prioridade
  prazo: string | null
  status: StatusTarefa
  duracaoMinutos: number
  /** Minutos ainda não cobertos por blocos CONCLUIDO. Ausente em payloads antigos. */
  restanteMinutos?: number
  projetoId?: string | null
  projetoNome?: string | null
  tags?: TagResumo[]
  tagMatchScore?: number | null
  createdAt: string
  updatedAt: string
  deletedAt: string | null
  deletada: boolean
  parentId?: string | null
  parentTitulo?: string | null
  temFilhos?: boolean
  /** Esperas da própria tarefa. Ausente em payloads antigos. */
  esperas?: EsperaResumo[]
  /** Bloqueada por espera própria, de pai ou do projeto de origem. */
  bloqueada?: boolean
}

export type SessaoTarefaRequest = {
  duracaoMinutos?: number | null
  data?: string | null
  blocoId?: string | null
}

export type PageTarefa = {
  content: TarefaResponse[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export type SortCampo = 'prazo' | 'duracao' | 'prioridade'
export type SortDirection = 'asc' | 'desc'

export type ListarTarefasParams = {
  status?: StatusTarefa[]
  prioridade?: Prioridade[]
  prazoDe?: string
  prazoAte?: string
  duracaoDe?: number
  duracaoAte?: number
  criadoDe?: string
  criadoAte?: string
  atualizadoDe?: string
  atualizadoAte?: string
  projetoId?: string
  tagIds?: string[]
  incluirDeletadas?: boolean
  somenteDeletadas?: boolean
  parentId?: string
  somenteRaizes?: boolean
  page?: number
  size?: number
  sort?: SortCampo
  direction?: SortDirection
}

/** Create mínimo: só `titulo` (+ `projetoId` opcional). Defaults no servidor. */
export type CriarTarefaRequest = {
  titulo: string
  descricao?: string | null
  prioridade?: Prioridade | null
  prazo?: string | null
  duracaoMinutos?: number | null
  projetoId?: string | null
  tagIds?: string[]
  parentId?: string | null
}

export type AtualizarTarefaRequest = {
  titulo?: string
  descricao?: string
  prioridade?: Prioridade
  prazo?: string | null
  status?: StatusTarefa
  duracaoMinutos?: number
  projetoId?: string | null
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

export function buildTarefasQuery(filtro: ListarTarefasParams = {}): string {
  const params = new URLSearchParams()

  appendAll(params, 'status', filtro.status)
  appendAll(params, 'prioridade', filtro.prioridade)

  if (filtro.prazoDe) params.set('prazoDe', filtro.prazoDe)
  if (filtro.prazoAte) params.set('prazoAte', filtro.prazoAte)
  if (filtro.duracaoDe != null) params.set('duracaoDe', String(filtro.duracaoDe))
  if (filtro.duracaoAte != null) params.set('duracaoAte', String(filtro.duracaoAte))
  if (filtro.criadoDe) params.set('criadoDe', filtro.criadoDe)
  if (filtro.criadoAte) params.set('criadoAte', filtro.criadoAte)
  if (filtro.atualizadoDe) params.set('atualizadoDe', filtro.atualizadoDe)
  if (filtro.atualizadoAte) params.set('atualizadoAte', filtro.atualizadoAte)
  if (filtro.projetoId) params.set('projetoId', filtro.projetoId)
  appendAll(params, 'tagId', filtro.tagIds)
  if (filtro.incluirDeletadas != null) {
    params.set('incluirDeletadas', String(filtro.incluirDeletadas))
  }
  if (filtro.somenteDeletadas != null) {
    params.set('somenteDeletadas', String(filtro.somenteDeletadas))
  }
  if (filtro.parentId) params.set('parentId', filtro.parentId)
  if (filtro.somenteRaizes != null) {
    params.set('somenteRaizes', String(filtro.somenteRaizes))
  }

  params.set('page', String(filtro.page ?? 0))
  params.set('size', String(filtro.size ?? 20))

  const sort = filtro.sort ?? 'prazo'
  const direction = filtro.direction ?? 'asc'
  params.set('sort', `${sort},${direction}`)

  return `/tarefas?${params.toString()}`
}

export async function listarTarefas(
  filtro: ListarTarefasParams = {},
  signal?: AbortSignal,
): Promise<PageTarefa> {
  const response = await apiFetch(buildTarefasQuery(filtro), signal ? { signal } : undefined)
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível carregar as tarefas.')
  }
  return (await response.json()) as PageTarefa
}

export async function criarTarefa(body: CriarTarefaRequest): Promise<TarefaResponse> {
  const response = await apiFetch('/tarefas', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível criar a tarefa.')
  }
  return (await response.json()) as TarefaResponse
}

export async function buscarTarefa(id: string): Promise<TarefaResponse> {
  const response = await apiFetch(`/tarefas/${id}`)
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível carregar a tarefa.')
  }
  return (await response.json()) as TarefaResponse
}

export async function atualizarTarefa(
  id: string,
  body: AtualizarTarefaRequest,
): Promise<TarefaResponse> {
  const response = await apiFetch(`/tarefas/${id}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível atualizar a tarefa.')
  }
  return (await response.json()) as TarefaResponse
}

export async function excluirTarefa(id: string): Promise<void> {
  const response = await apiFetch(`/tarefas/${id}`, { method: 'DELETE' })
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível excluir a tarefa.')
  }
}

export async function restaurarTarefa(id: string): Promise<TarefaResponse> {
  const response = await apiFetch(`/tarefas/${id}/restore`, { method: 'POST' })
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível restaurar a tarefa.')
  }
  return (await response.json()) as TarefaResponse
}

export async function iniciarTarefaSessao(
  id: string,
  body: SessaoTarefaRequest = {},
): Promise<ExecucaoResponse> {
  const response = await apiFetch(`/tarefas/${id}/iniciar`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível iniciar a sessão da tarefa.')
  }
  return readApiJson<ExecucaoResponse>(response)
}

export async function concluirTarefaDireto(
  id: string,
  body: SessaoTarefaRequest = {},
): Promise<ExecucaoResponse> {
  const response = await apiFetch(`/tarefas/${id}/concluir-direto`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível concluir a sessão da tarefa.')
  }
  return readApiJson<ExecucaoResponse>(response)
}

export async function listarEsperasTarefa(id: string): Promise<EsperaResumo[]> {
  const response = await apiFetch(`/tarefas/${id}/esperas`)
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível carregar as esperas da tarefa.')
  }
  return (await response.json()) as EsperaResumo[]
}

export async function criarEsperaTarefa(
  id: string,
  body: CriarEsperaRequest,
): Promise<EsperaResumo> {
  const response = await apiFetch(`/tarefas/${id}/esperas`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível criar a espera.')
  }
  return (await response.json()) as EsperaResumo
}

export async function excluirEsperaTarefa(id: string, esperaId: string): Promise<void> {
  const response = await apiFetch(`/tarefas/${id}/esperas/${esperaId}`, { method: 'DELETE' })
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível remover a espera.')
  }
}
