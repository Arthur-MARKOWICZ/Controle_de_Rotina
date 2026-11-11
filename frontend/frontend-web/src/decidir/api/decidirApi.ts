import { lerErroApi } from '../../auth/api/lerErroApi'
import { apiFetch, readApiJson } from '../../auth/session/apiClient'
import type { TagResumo } from '../../tag/api/tagApi'
import type { Prioridade } from '../../tarefa/validation/tarefaForm'

export type DecidirResponse = {
  id: string
  titulo: string
  descricao: string | null
  decidirAte: string | null
  projetoId: string | null
  tarefaId: string | null
  tags?: TagResumo[]
  tagMatchScore?: number | null
  createdAt: string
  updatedAt: string
}

export type PageDecidir = {
  content: DecidirResponse[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export type ListarDecidirParams = {
  promovidos?: boolean
  page?: number
  size?: number
  tagIds?: string[]
}

export type CriarDecidirRequest = {
  titulo: string
  descricao?: string | null
  decidirAte?: string | null
  projetoId?: string | null
  tagIds?: string[]
}

export type AtualizarDecidirRequest = {
  titulo?: string
  descricao?: string | null
  decidirAte?: string | null
  projetoId?: string | null
  tagIds?: string[]
}

export type PromoverDecidirRequest = {
  titulo?: string
  descricao?: string | null
  prioridade?: Prioridade
  prazo?: string | null
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

export function buildDecidirQuery(filtro: ListarDecidirParams = {}): string {
  const params = new URLSearchParams()
  if (filtro.promovidos) {
    params.set('promovidos', 'true')
  }
  appendAll(params, 'tagId', filtro.tagIds)
  params.set('page', String(filtro.page ?? 0))
  params.set('size', String(filtro.size ?? 20))
  return `/decidir?${params.toString()}`
}

export async function listarDecidir(filtro: ListarDecidirParams = {}): Promise<PageDecidir> {
  const response = await apiFetch(buildDecidirQuery(filtro))
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível carregar a fila Decidir.')
  }
  return (await response.json()) as PageDecidir
}

export async function buscarDecidir(id: string): Promise<DecidirResponse> {
  const response = await apiFetch(`/decidir/${id}`)
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível carregar o item.')
  }
  return readApiJson<DecidirResponse>(response)
}

export async function criarDecidir(body: CriarDecidirRequest): Promise<DecidirResponse> {
  const response = await apiFetch('/decidir', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível criar o item.')
  }
  return readApiJson<DecidirResponse>(response)
}

export async function atualizarDecidir(
  id: string,
  body: AtualizarDecidirRequest,
): Promise<DecidirResponse> {
  const response = await apiFetch(`/decidir/${id}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível atualizar o item.')
  }
  return readApiJson<DecidirResponse>(response)
}

export async function excluirDecidir(id: string): Promise<void> {
  const response = await apiFetch(`/decidir/${id}`, { method: 'DELETE' })
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível descartar o item.')
  }
}

export async function promoverDecidir(
  id: string,
  body: PromoverDecidirRequest = {},
): Promise<DecidirResponse> {
  const response = await apiFetch(`/decidir/${id}/promover`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível promover o item.')
  }
  return readApiJson<DecidirResponse>(response)
}
