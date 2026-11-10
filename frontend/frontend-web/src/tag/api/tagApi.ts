import { lerErroApi } from '../../auth/api/lerErroApi'
import { apiFetch, readApiJson } from '../../auth/session/apiClient'

export const TAG_COR_PADRAO = '#6B7280'

export type TagResumo = {
  id: string
  nome: string
  cor: string
}

export type TagResponse = TagResumo & {
  createdAt?: string
  updatedAt?: string
}

export type CriarTagRequest = {
  nome: string
  cor?: string
}

export type AtualizarTagRequest = {
  nome?: string
  cor?: string
}

export async function listarTags(): Promise<TagResponse[]> {
  const response = await apiFetch('/tags')
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível carregar as tags.')
  }
  return readApiJson<TagResponse[]>(response)
}

export async function criarTag(body: CriarTagRequest): Promise<TagResponse> {
  const response = await apiFetch('/tags', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível criar a tag.')
  }
  return readApiJson<TagResponse>(response)
}

export async function atualizarTag(id: string, body: AtualizarTagRequest): Promise<TagResponse> {
  const response = await apiFetch(`/tags/${id}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível atualizar a tag.')
  }
  return readApiJson<TagResponse>(response)
}

export async function excluirTag(id: string): Promise<void> {
  const response = await apiFetch(`/tags/${id}`, { method: 'DELETE' })
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível excluir a tag.')
  }
}
