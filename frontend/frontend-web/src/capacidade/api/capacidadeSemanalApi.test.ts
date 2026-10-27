import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../../auth/api/authApi'
import * as apiClient from '../../auth/session/apiClient'
import {
  atualizarCapacidadeSemanal,
  criarCapacidadeSemanal,
  excluirCapacidadeSemanal,
  listarCapacidadesSemanais,
  restaurarCapacidadeSemanal,
  type CapacidadeSemanalResponse,
} from './capacidadeSemanalApi'

vi.mock('../../auth/session/apiClient', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../auth/session/apiClient')>()
  return {
    ...actual,
    apiFetch: vi.fn(),
  }
})

const capacidade: CapacidadeSemanalResponse = {
  id: 'c-1',
  diaSemana: 'SEGUNDA',
  horas: 4,
  ativo: true,
  createdAt: '2026-01-01T10:00:00Z',
  updatedAt: '2026-01-01T10:00:00Z',
}

describe('capacidadeSemanalApi', () => {
  beforeEach(() => {
    vi.mocked(apiClient.apiFetch).mockReset()
  })

  it('lista capacidades semanais via GET /capacidades/semanal', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify([capacidade]), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const lista = await listarCapacidadesSemanais()

    expect(apiClient.apiFetch).toHaveBeenCalledWith('/capacidades/semanal')
    expect(lista).toHaveLength(1)
    expect(lista[0].diaSemana).toBe('SEGUNDA')
  })

  it('lista com filtros de deletadas', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify([]), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    await listarCapacidadesSemanais({ incluirDeletadas: true, somenteDeletadas: false })

    const [url] = vi.mocked(apiClient.apiFetch).mock.calls[0]
    expect(url).toContain('/capacidades/semanal?')
    expect(url).toContain('incluirDeletadas=true')
    expect(url).toContain('somenteDeletadas=false')
  })

  it('cria capacidade unitária com POST /capacidades/semanal', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify([capacidade]), {
        status: 201,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const body = { diaSemana: 'SEGUNDA' as const, horas: 4 }
    const criadas = await criarCapacidadeSemanal(body)

    expect(apiClient.apiFetch).toHaveBeenCalledWith('/capacidades/semanal', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
    expect(criadas).toHaveLength(1)
    expect(criadas[0].id).toBe('c-1')
  })

  it('cria lote com POST array em /capacidades/semanal', async () => {
    const lote = [
      { ...capacidade, id: 'c-1', diaSemana: 'SEGUNDA' as const, horas: 4 },
      { ...capacidade, id: 'c-2', diaSemana: 'TERCA' as const, horas: 2 },
    ]
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify(lote), {
        status: 201,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const body = [
      { diaSemana: 'SEGUNDA' as const, horas: 4 },
      { diaSemana: 'TERCA' as const, horas: 2 },
    ]
    const criadas = await criarCapacidadeSemanal(body)

    expect(apiClient.apiFetch).toHaveBeenCalledWith('/capacidades/semanal', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
    expect(criadas).toHaveLength(2)
  })

  it('atualiza capacidade com PATCH', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ ...capacidade, horas: 6 }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const atualizada = await atualizarCapacidadeSemanal('c-1', { horas: 6 })

    expect(apiClient.apiFetch).toHaveBeenCalledWith('/capacidades/semanal/c-1', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ horas: 6 }),
    })
    expect(atualizada.horas).toBe(6)
  })

  it('exclui capacidade com DELETE /capacidades/semanal/{id}', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(new Response(null, { status: 204 }))

    await excluirCapacidadeSemanal('c-1')

    expect(apiClient.apiFetch).toHaveBeenCalledWith('/capacidades/semanal/c-1', {
      method: 'DELETE',
    })
  })

  it('restaura capacidade com POST /capacidades/semanal/{id}/restore', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify(capacidade), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const restaurada = await restaurarCapacidadeSemanal('c-1')

    expect(apiClient.apiFetch).toHaveBeenCalledWith('/capacidades/semanal/c-1/restore', {
      method: 'POST',
    })
    expect(restaurada.ativo).toBe(true)
  })

  it('propaga ApiError com detalhe da API', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          title: 'Conflito',
          detail: 'Já existe capacidade ativa para SEGUNDA.',
        }),
        { status: 409, headers: { 'Content-Type': 'application/json' } },
      ),
    )

    try {
      await criarCapacidadeSemanal({ diaSemana: 'SEGUNDA', horas: 4 })
      expect.fail('deveria ter lançado ApiError')
    } catch (error) {
      expect(error).toBeInstanceOf(ApiError)
      expect(error).toMatchObject({
        status: 409,
        message: 'Já existe capacidade ativa para SEGUNDA.',
      })
    }
  })
})
