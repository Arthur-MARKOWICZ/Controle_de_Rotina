import { lerErroApi } from '../../auth/api/lerErroApi'
import { apiFetch } from '../../auth/session/apiClient'
import type { CriarEsperaRequest, EsperaResumo } from '../../pendencia/api/espera'
import type { TagResumo } from '../../tag/api/tagApi'

export type StatusProjeto = 'ATIVO' | 'ARQUIVADO'

export type ProjetoResponse = {
  id: string
  nome: string
  descricao: string | null
  status: StatusProjeto
  tags?: TagResumo[]
  tagMatchScore?: number | null
  createdAt: string
  updatedAt: string
  /** Esperas do próprio projeto. Ausente em payloads antigos. */
  esperas?: EsperaResumo[]
  /** Projeto bloqueado por espera irresolvida. */
  bloqueada?: boolean
}

export type PageProjeto = {
  content: ProjetoResponse[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export type ListarProjetosParams = {
  status?: StatusProjeto
  page?: number
  size?: number
  tagIds?: string[]
}

export type CriarProjetoRequest = {
  nome: string
  descricao?: string | null
  tagIds?: string[]
}

export type AtualizarProjetoRequest = {
  nome?: string
  descricao?: string | null
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

export function buildProjetosQuery(filtro: ListarProjetosParams = {}): string {
  const params = new URLSearchParams()
  if (filtro.status) {
    params.set('status', filtro.status)
  }
  appendAll(params, 'tagId', filtro.tagIds)
  params.set('page', String(filtro.page ?? 0))
  params.set('size', String(filtro.size ?? 20))
  return `/projetos?${params.toString()}`
}

export async function listarProjetos(
  filtro: ListarProjetosParams = {},
): Promise<PageProjeto> {
  const response = await apiFetch(buildProjetosQuery(filtro))
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível carregar os projetos.')
  }
  return (await response.json()) as PageProjeto
}

export async function criarProjeto(body: CriarProjetoRequest): Promise<ProjetoResponse> {
  const response = await apiFetch('/projetos', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível criar o projeto.')
  }
  return (await response.json()) as ProjetoResponse
}

export async function buscarProjeto(id: string): Promise<ProjetoResponse> {
  const response = await apiFetch(`/projetos/${id}`)
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível carregar o projeto.')
  }
  return (await response.json()) as ProjetoResponse
}

export async function atualizarProjeto(
  id: string,
  body: AtualizarProjetoRequest,
): Promise<ProjetoResponse> {
  const response = await apiFetch(`/projetos/${id}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível atualizar o projeto.')
  }
  return (await response.json()) as ProjetoResponse
}

export async function arquivarProjeto(id: string): Promise<ProjetoResponse> {
  const response = await apiFetch(`/projetos/${id}/arquivar`, { method: 'POST' })
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível arquivar o projeto.')
  }
  return (await response.json()) as ProjetoResponse
}

export async function reativarProjeto(id: string): Promise<ProjetoResponse> {
  const response = await apiFetch(`/projetos/${id}/reativar`, { method: 'POST' })
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível reativar o projeto.')
  }
  return (await response.json()) as ProjetoResponse
}

export async function listarEsperasProjeto(id: string): Promise<EsperaResumo[]> {
  const response = await apiFetch(`/projetos/${id}/esperas`)
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível carregar as esperas do projeto.')
  }
  return (await response.json()) as EsperaResumo[]
}

export async function criarEsperaProjeto(
  id: string,
  body: CriarEsperaRequest,
): Promise<EsperaResumo> {
  const response = await apiFetch(`/projetos/${id}/esperas`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível criar a espera.')
  }
  return (await response.json()) as EsperaResumo
}

export async function excluirEsperaProjeto(id: string, esperaId: string): Promise<void> {
  const response = await apiFetch(`/projetos/${id}/esperas/${esperaId}`, { method: 'DELETE' })
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível remover a espera.')
  }
}
