import { lerErroApi } from '../../auth/api/lerErroApi'
import { apiFetch } from '../../auth/session/apiClient'
import type { Prioridade, StatusTarefa } from '../validation/tarefaForm'

export type TarefaResponse = {
  id: string
  titulo: string
  descricao: string
  prioridade: Prioridade
  prazo: string | null
  status: StatusTarefa
  duracaoMinutos: number
  createdAt: string
  updatedAt: string
  deletedAt: string | null
  deletada: boolean
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
  incluirDeletadas?: boolean
  somenteDeletadas?: boolean
  page?: number
  size?: number
  sort?: SortCampo
  direction?: SortDirection
}

export type CriarTarefaRequest = {
  titulo: string
  descricao: string
  prioridade: Prioridade
  prazo: string | null
  duracaoMinutos: number
}

export type AtualizarTarefaRequest = {
  titulo?: string
  descricao?: string
  prioridade?: Prioridade
  prazo?: string | null
  status?: StatusTarefa
  duracaoMinutos?: number
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
  if (filtro.incluirDeletadas != null) {
    params.set('incluirDeletadas', String(filtro.incluirDeletadas))
  }
  if (filtro.somenteDeletadas != null) {
    params.set('somenteDeletadas', String(filtro.somenteDeletadas))
  }

  params.set('page', String(filtro.page ?? 0))
  params.set('size', String(filtro.size ?? 20))

  const sort = filtro.sort ?? 'prazo'
  const direction = filtro.direction ?? 'asc'
  params.set('sort', `${sort},${direction}`)

  return `/tarefas?${params.toString()}`
}

export async function listarTarefas(filtro: ListarTarefasParams = {}): Promise<PageTarefa> {
  const response = await apiFetch(buildTarefasQuery(filtro))
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
