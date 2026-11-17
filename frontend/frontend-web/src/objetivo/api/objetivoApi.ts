import type { components } from '../../api/schema'
import { lerErroApi } from '../../auth/api/lerErroApi'
import { apiFetch, readApiJson } from '../../auth/session/apiClient'

type Schemas = components['schemas']

export type ObjetivoResponse = Schemas['ObjetivoResponse']

export type StatusObjetivo = ObjetivoResponse['status']

export type CriarObjetivoRequest = Schemas['CriarObjetivoRequest']

export type AtualizarObjetivoRequest = Schemas['AtualizarObjetivoRequest']

export type PageObjetivo = Schemas['PagedModelObjetivoResponse']

export type ListarObjetivosParams = {
  status?: StatusObjetivo
  page?: number
  size?: number
}

export function buildObjetivosQuery(filtro: ListarObjetivosParams = {}): string {
  const params = new URLSearchParams()
  if (filtro.status) {
    params.set('status', filtro.status)
  }
  params.set('page', String(filtro.page ?? 0))
  params.set('size', String(filtro.size ?? 20))
  return `/objetivos?${params.toString()}`
}

export async function listarObjetivos(filtro: ListarObjetivosParams = {}): Promise<PageObjetivo> {
  const response = await apiFetch(buildObjetivosQuery(filtro))
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível carregar os objetivos.')
  }
  return readApiJson<PageObjetivo>(response)
}

export async function criarObjetivo(body: CriarObjetivoRequest): Promise<ObjetivoResponse> {
  const response = await apiFetch('/objetivos', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível criar o objetivo.')
  }
  return readApiJson<ObjetivoResponse>(response)
}

export async function buscarObjetivo(id: string): Promise<ObjetivoResponse> {
  const response = await apiFetch(`/objetivos/${id}`)
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível carregar o objetivo.')
  }
  return readApiJson<ObjetivoResponse>(response)
}

export async function atualizarObjetivo(
  id: string,
  body: AtualizarObjetivoRequest,
): Promise<ObjetivoResponse> {
  const response = await apiFetch(`/objetivos/${id}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível atualizar o objetivo.')
  }
  return readApiJson<ObjetivoResponse>(response)
}

async function transicionar(id: string, acao: string, fallback: string): Promise<ObjetivoResponse> {
  const response = await apiFetch(`/objetivos/${id}/${acao}`, { method: 'POST' })
  if (!response.ok) {
    throw await lerErroApi(response, fallback)
  }
  return readApiJson<ObjetivoResponse>(response)
}

export function concluirObjetivo(id: string): Promise<ObjetivoResponse> {
  return transicionar(id, 'concluir', 'Não foi possível concluir o objetivo.')
}

export function arquivarObjetivo(id: string): Promise<ObjetivoResponse> {
  return transicionar(id, 'arquivar', 'Não foi possível arquivar o objetivo.')
}

export function reabrirObjetivo(id: string): Promise<ObjetivoResponse> {
  return transicionar(id, 'reabrir', 'Não foi possível reabrir o objetivo.')
}
