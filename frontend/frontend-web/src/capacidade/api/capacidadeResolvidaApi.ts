import { lerErroApi } from '../../auth/api/lerErroApi'
import { apiFetch, readApiJson } from '../../auth/session/apiClient'

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
    throw await lerErroApi(response, 'Não foi possível carregar a capacidade resolvida.')
  }
  return readApiJson<CapacidadeResolvidaResponse[]>(response)
}
