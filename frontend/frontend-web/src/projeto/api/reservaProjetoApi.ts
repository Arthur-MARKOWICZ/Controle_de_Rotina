import { lerErroApi } from '../../auth/api/lerErroApi'
import { apiFetch } from '../../auth/session/apiClient'
import type { DiaSemana } from '../../capacidade/api/capacidadeSemanalApi'

export type ReservaProjetoResponse = {
  id: string
  projetoId: string
  diaSemana: DiaSemana
  minutosReservados: number
  createdAt: string
  updatedAt: string
}

export type CriarReservaProjetoRequest = {
  diaSemana: DiaSemana
  minutosReservados: number
}

export type AtualizarReservaProjetoRequest = {
  diaSemana?: DiaSemana
  minutosReservados?: number
}

export type ListarReservasParams = {
  diaSemana?: DiaSemana
  projetoId?: string
}

export function buildReservasQuery(filtro: ListarReservasParams = {}): string {
  const params = new URLSearchParams()
  if (filtro.diaSemana) {
    params.set('diaSemana', filtro.diaSemana)
  }
  if (filtro.projetoId) {
    params.set('projetoId', filtro.projetoId)
  }
  const qs = params.toString()
  return qs ? `/projetos/reservas?${qs}` : '/projetos/reservas'
}

export async function listarReservas(
  filtro: ListarReservasParams = {},
): Promise<ReservaProjetoResponse[]> {
  const response = await apiFetch(buildReservasQuery(filtro))
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível carregar as reservas.')
  }
  return (await response.json()) as ReservaProjetoResponse[]
}

export async function criarReserva(
  projetoId: string,
  body: CriarReservaProjetoRequest,
): Promise<ReservaProjetoResponse> {
  const response = await apiFetch(`/projetos/${projetoId}/reservas`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível criar a reserva.')
  }
  return (await response.json()) as ReservaProjetoResponse
}

export async function atualizarReserva(
  projetoId: string,
  reservaId: string,
  body: AtualizarReservaProjetoRequest,
): Promise<ReservaProjetoResponse> {
  const response = await apiFetch(`/projetos/${projetoId}/reservas/${reservaId}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!response.ok) {
    throw await lerErroApi(response, 'Não foi possível atualizar a reserva.')
  }
  return (await response.json()) as ReservaProjetoResponse
}
