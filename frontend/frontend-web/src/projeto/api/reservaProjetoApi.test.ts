import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as apiClient from '../../auth/session/apiClient'
import {
  atualizarReserva,
  criarReserva,
  listarReservas,
  type ReservaProjetoResponse,
} from './reservaProjetoApi'

vi.mock('../../auth/session/apiClient', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../auth/session/apiClient')>()
  return {
    ...actual,
    apiFetch: vi.fn(),
  }
})

const reserva: ReservaProjetoResponse = {
  id: 'r-1',
  projetoId: 'proj-tcc',
  diaSemana: 'SEGUNDA',
  minutosReservados: 60,
  createdAt: '2026-01-01T10:00:00Z',
  updatedAt: '2026-01-01T10:00:00Z',
}

describe('reservaProjetoApi', () => {
  beforeEach(() => {
    vi.mocked(apiClient.apiFetch).mockReset()
  })

  it('lista reservas do usuário via GET /projetos/reservas', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify([reserva]), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const lista = await listarReservas({ diaSemana: 'SEGUNDA' })

    expect(apiClient.apiFetch).toHaveBeenCalledWith('/projetos/reservas?diaSemana=SEGUNDA')
    expect(lista).toHaveLength(1)
  })

  it('cria e atualiza reserva', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify(reserva), {
        status: 201,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    await criarReserva('proj-tcc', { diaSemana: 'SEGUNDA', minutosReservados: 60 })
    expect(apiClient.apiFetch).toHaveBeenLastCalledWith('/projetos/proj-tcc/reservas', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ diaSemana: 'SEGUNDA', minutosReservados: 60 }),
    })

    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ ...reserva, minutosReservados: 90 }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    await atualizarReserva('proj-tcc', 'r-1', { minutosReservados: 90 })
    expect(apiClient.apiFetch).toHaveBeenLastCalledWith('/projetos/proj-tcc/reservas/r-1', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ minutosReservados: 90 }),
    })
  })
})
