import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../../auth/api/authApi'
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

  it('propaga 400', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ title: 'Bad Request', detail: 'minutos inválidos' }), {
        status: 400,
        headers: { 'Content-Type': 'application/problem+json' },
      }),
    )
    await expect(
      criarReserva('proj-tcc', { diaSemana: 'SEGUNDA', minutosReservados: -1 }),
    ).rejects.toMatchObject({ status: 400, message: 'minutos inválidos' })
  })

  it('propaga 401 quando o refresh falhou', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ title: 'Não autorizado', detail: 'Sessão expirada.' }), {
        status: 401,
      }),
    )
    await expect(listarReservas()).rejects.toMatchObject({ status: 401 })
  })

  it('propaga 404', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ title: 'Não encontrado', detail: 'Reserva inexistente.' }), {
        status: 404,
      }),
    )
    await expect(
      atualizarReserva('proj-tcc', 'r-sumiu', { minutosReservados: 30 }),
    ).rejects.toMatchObject({ status: 404, message: 'Reserva inexistente.' })
  })

  it('propaga 409 de conflito', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ title: 'Conflito', detail: 'Já existe reserva neste dia.' }), {
        status: 409,
      }),
    )
    await expect(
      criarReserva('proj-tcc', { diaSemana: 'SEGUNDA', minutosReservados: 60 }),
    ).rejects.toMatchObject({ status: 409, message: 'Já existe reserva neste dia.' })
  })

  it('propaga 500', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ title: 'Erro interno' }), { status: 500 }),
    )
    await expect(listarReservas()).rejects.toBeInstanceOf(ApiError)
  })

  it('rejeita HTML do proxy e JSON inválido', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response('<!doctype html><html><div id="root"></div></html>', {
        status: 404,
        headers: { 'Content-Type': 'text/html' },
      }),
    )
    await expect(listarReservas()).rejects.toBeInstanceOf(ApiError)

    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response('não-é-json', { status: 500, headers: { 'Content-Type': 'application/json' } }),
    )
    await expect(listarReservas()).rejects.toBeInstanceOf(ApiError)
  })
})
