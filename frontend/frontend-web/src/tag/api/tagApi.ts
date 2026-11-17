import type { components } from '../../api/schema'
import { lerErroApi } from '../../auth/api/lerErroApi'
import { apiFetch, readApiJson } from '../../auth/session/apiClient'

export const TAG_COR_PADRAO = '#6B7280'

type Schemas = components['schemas']

export type TagResumo = Schemas['TagResumoResponse']

export type TagResponse = Schemas['TagResponse']

export type CriarTagRequest = Schemas['CriarTagRequest']

export type AtualizarTagRequest = Schemas['AtualizarTagRequest']

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
