import { ApiError } from '../../auth/api/authApi'
import { apiFetch } from '../../auth/session/apiClient'

export type OrigemCapacidade = 'SEMANAL' | 'OVERRIDE' | 'VAZIO'

export type CapacidadeResolvidaResponse = {
  data: string
  horas: number | null
  origem: OrigemCapacidade
}

export type ConsultarCapacidadeResolvidaParams = {
  dataDe: string
  dataAte: string
}

async function lerErro(response: Response, fallback: string): Promise<ApiError> {
  try {
    const body = (await response.json()) as { detail?: string; title?: string }
    return new ApiError(response.status, body.detail || body.title || fallback)
  } catch {
    return new ApiError(response.status, fallback)
  }
}

export function buildCapacidadeResolvidaQuery(
  filtro: ConsultarCapacidadeResolvidaParams,
): string {
  const params = new URLSearchParams()
  params.set('dataDe', filtro.dataDe)
  params.set('dataAte', filtro.dataAte)
  return `/capacidades/resolvida?${params.toString()}`
}

export async function consultarCapacidadeResolvida(
  filtro: ConsultarCapacidadeResolvidaParams,
): Promise<CapacidadeResolvidaResponse[]> {
  const response = await apiFetch(buildCapacidadeResolvidaQuery(filtro))
  if (!response.ok) {
    throw await lerErro(response, 'Não foi possível carregar a capacidade resolvida.')
  }
  return (await response.json()) as CapacidadeResolvidaResponse[]
}
