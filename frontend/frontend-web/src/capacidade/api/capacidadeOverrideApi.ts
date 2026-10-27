import { ApiError } from '../../auth/api/authApi'
import { apiFetch, readApiJson } from '../../auth/session/apiClient'

export type CapacidadeOverrideResponse = {
  id: string
  data: string
  horas: number
  ativo: boolean
  createdAt: string
  updatedAt: string
}

export type ListarCapacidadesOverrideParams = {
  incluirDeletadas?: boolean
  somenteDeletadas?: boolean
  dataDe?: string
  dataAte?: string
}

export type CriarCapacidadeOverrideItem = {
  data: string
  horas: number
}

export type AtualizarCapacidadeOverrideRequest = {
  data?: string
  horas?: number
}

async function lerErro(response: Response, fallback: string): Promise<ApiError> {
  try {
    const body = await readApiJson<{ detail?: string; title?: string }>(response)
    return new ApiError(response.status, body.detail || body.title || fallback)
  } catch (error) {
    if (error instanceof ApiError) {
      return error
    }
    return new ApiError(response.status, fallback)
  }
}

export function buildCapacidadesOverrideQuery(
  filtro: ListarCapacidadesOverrideParams = {},
): string {
  const params = new URLSearchParams()

  if (filtro.incluirDeletadas != null) {
    params.set('incluirDeletadas', String(filtro.incluirDeletadas))
  }
  if (filtro.somenteDeletadas != null) {
    params.set('somenteDeletadas', String(filtro.somenteDeletadas))
  }
  if (filtro.dataDe) {
    params.set('dataDe', filtro.dataDe)
  }
  if (filtro.dataAte) {
    params.set('dataAte', filtro.dataAte)
  }

  const query = params.toString()
  return query ? `/capacidades/overrides?${query}` : '/capacidades/overrides'
}

export async function listarCapacidadesOverride(
  filtro: ListarCapacidadesOverrideParams = {},
): Promise<CapacidadeOverrideResponse[]> {
  const response = await apiFetch(buildCapacidadesOverrideQuery(filtro))
  if (!response.ok) {
    throw await lerErro(response, 'Não foi possível carregar os overrides.')
  }
  return readApiJson<CapacidadeOverrideResponse[]>(response)
}

export async function criarCapacidadeOverride(
  body: CriarCapacidadeOverrideItem | CriarCapacidadeOverrideItem[],
): Promise<CapacidadeOverrideResponse[]> {
  const response = await apiFetch('/capacidades/overrides', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!response.ok) {
    throw await lerErro(response, 'Não foi possível criar o override.')
  }
  return readApiJson<CapacidadeOverrideResponse[]>(response)
}

export async function atualizarCapacidadeOverride(
  id: string,
  body: AtualizarCapacidadeOverrideRequest,
): Promise<CapacidadeOverrideResponse> {
  const response = await apiFetch(`/capacidades/overrides/${id}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!response.ok) {
    throw await lerErro(response, 'Não foi possível atualizar o override.')
  }
  return readApiJson<CapacidadeOverrideResponse>(response)
}

export async function excluirCapacidadeOverride(id: string): Promise<void> {
  const response = await apiFetch(`/capacidades/overrides/${id}`, { method: 'DELETE' })
  if (!response.ok) {
    throw await lerErro(response, 'Não foi possível excluir o override.')
  }
}

export async function restaurarCapacidadeOverride(
  id: string,
): Promise<CapacidadeOverrideResponse> {
  const response = await apiFetch(`/capacidades/overrides/${id}/restore`, {
    method: 'POST',
  })
  if (!response.ok) {
    throw await lerErro(response, 'Não foi possível restaurar o override.')
  }
  return readApiJson<CapacidadeOverrideResponse>(response)
}
