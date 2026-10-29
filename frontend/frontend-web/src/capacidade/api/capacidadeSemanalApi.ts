import { lerErroApi } from '../../auth/api/lerErroApi'
import { apiFetch, readApiJson } from '../../auth/session/apiClient'

export type DiaSemana =
  | 'SEGUNDA'
  | 'TERCA'
  | 'QUARTA'
  | 'QUINTA'
  | 'SEXTA'
  | 'SABADO'
  | 'DOMINGO'

export type CapacidadeSemanalResponse = {
  id: string
  diaSemana: DiaSemana
  horas: number
  ativo: boolean
  createdAt: string
  updatedAt: string
}

export type ListarCapacidadesSemanaisParams = {
  incluirDeletadas?: boolean
  somenteDeletadas?: boolean
}

export type CriarCapacidadeSemanalItem = {
  diaSemana: DiaSemana
  horas: number
}

export type AtualizarCapacidadeSemanalRequest = {
  diaSemana?: DiaSemana
  horas?: number
}

export function buildCapacidadesSemanaisQuery(
  filtro: ListarCapacidadesSemanaisParams = {},
): string {
  const params = new URLSearchParams()

  if (filtro.incluirDeletadas != null) {
    params.set('incluirDeletadas', String(filtro.incluirDeletadas))
  }
  if (filtro.somenteDeletadas != null) {
    params.set('somenteDeletadas', String(filtro.somenteDeletadas))
  }

  const query = params.toString()
  return query ? `/capacidades/semanal?${query}` : '/capacidades/semanal'
}

export async function listarCapacidadesSemanais(
  filtro: ListarCapacidadesSemanaisParams = {},
): Promise<CapacidadeSemanalResponse[]> {
  const response = await apiFetch(buildCapacidadesSemanaisQuery(filtro))
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível carregar as capacidades.')
  }
  return readApiJson<CapacidadeSemanalResponse[]>(response)
}

export async function criarCapacidadeSemanal(
  body: CriarCapacidadeSemanalItem | CriarCapacidadeSemanalItem[],
): Promise<CapacidadeSemanalResponse[]> {
  const response = await apiFetch('/capacidades/semanal', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível criar a capacidade.')
  }
  return readApiJson<CapacidadeSemanalResponse[]>(response)
}

export async function atualizarCapacidadeSemanal(
  id: string,
  body: AtualizarCapacidadeSemanalRequest,
): Promise<CapacidadeSemanalResponse> {
  const response = await apiFetch(`/capacidades/semanal/${id}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível atualizar a capacidade.')
  }
  return readApiJson<CapacidadeSemanalResponse>(response)
}

export async function excluirCapacidadeSemanal(id: string): Promise<void> {
  const response = await apiFetch(`/capacidades/semanal/${id}`, { method: 'DELETE' })
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível excluir a capacidade.')
  }
}

export async function restaurarCapacidadeSemanal(
  id: string,
): Promise<CapacidadeSemanalResponse> {
  const response = await apiFetch(`/capacidades/semanal/${id}/restore`, {
    method: 'POST',
  })
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível restaurar a capacidade.')
  }
  return readApiJson<CapacidadeSemanalResponse>(response)
}
