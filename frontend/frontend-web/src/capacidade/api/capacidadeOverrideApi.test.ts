import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../../auth/api/authApi'
import * as apiClient from '../../auth/session/apiClient'
import {
  atualizarCapacidadeOverride,
  criarCapacidadeOverride,
  excluirCapacidadeOverride,
  listarCapacidadesOverride,
  restaurarCapacidadeOverride,
  type CapacidadeOverrideResponse,
} from './capacidadeOverrideApi'

vi.mock('../../auth/session/apiClient', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../auth/session/apiClient')>()
  return {
    ...actual,
    apiFetch: vi.fn(),
  }
})

const override: CapacidadeOverrideResponse = {
  id: 'o-1',
  data: '2026-10-08',
  horas: 1,
  ativo: true,
  createdAt: '2026-01-01T10:00:00Z',
  updatedAt: '2026-01-01T10:00:00Z',
}

describe('capacidadeOverrideApi', () => {
  beforeEach(() => {
    vi.mocked(apiClient.apiFetch).mockReset()
  })

  it('lista overrides via GET /capacidades/overrides', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify([override]), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const lista = await listarCapacidadesOverride()

    expect(apiClient.apiFetch).toHaveBeenCalledWith('/capacidades/overrides')
    expect(lista).toHaveLength(1)
    expect(lista[0].data).toBe('2026-10-08')
  })

  it('lista com filtros de deletadas e intervalo de datas', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify([]), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    await listarCapacidadesOverride({
      incluirDeletadas: true,
      somenteDeletadas: false,
      dataDe: '2026-10-01',
      dataAte: '2026-10-31',
    })

    const [url] = vi.mocked(apiClient.apiFetch).mock.calls[0]
    expect(url).toContain('/capacidades/overrides?')
    expect(url).toContain('incluirDeletadas=true')
    expect(url).toContain('somenteDeletadas=false')
    expect(url).toContain('dataDe=2026-10-01')
    expect(url).toContain('dataAte=2026-10-31')
  })

  it('cria override unitário com POST /capacidades/overrides', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify([override]), {
        status: 201,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const body = { data: '2026-10-08', horas: 1 }
    const criados = await criarCapacidadeOverride(body)

    expect(apiClient.apiFetch).toHaveBeenCalledWith('/capacidades/overrides', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
    expect(criados).toHaveLength(1)
    expect(criados[0].id).toBe('o-1')
  })

  it('cria lote com POST array em /capacidades/overrides', async () => {
    const lote = [
      override,
      { ...override, id: 'o-2', data: '2026-10-09', horas: 2 },
    ]
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify(lote), {
        status: 201,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const body = [
      { data: '2026-10-08', horas: 1 },
      { data: '2026-10-09', horas: 2 },
    ]
    const criados = await criarCapacidadeOverride(body)

    expect(apiClient.apiFetch).toHaveBeenCalledWith('/capacidades/overrides', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
    expect(criados).toHaveLength(2)
  })

  it('atualiza override com PATCH', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ ...override, horas: 3 }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const atualizado = await atualizarCapacidadeOverride('o-1', { horas: 3 })

    expect(apiClient.apiFetch).toHaveBeenCalledWith('/capacidades/overrides/o-1', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ horas: 3 }),
    })
    expect(atualizado.horas).toBe(3)
  })

  it('exclui override com DELETE /capacidades/overrides/{id}', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(new Response(null, { status: 204 }))

    await excluirCapacidadeOverride('o-1')

    expect(apiClient.apiFetch).toHaveBeenCalledWith('/capacidades/overrides/o-1', {
      method: 'DELETE',
    })
  })

  it('restaura override com POST /capacidades/overrides/{id}/restore', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify(override), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const restaurado = await restaurarCapacidadeOverride('o-1')

    expect(apiClient.apiFetch).toHaveBeenCalledWith('/capacidades/overrides/o-1/restore', {
      method: 'POST',
    })
    expect(restaurado.ativo).toBe(true)
  })

  it('propaga ApiError com detalhe da API', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          title: 'Conflito',
          detail: 'Já existe override ativo para 2026-10-08.',
        }),
        { status: 409, headers: { 'Content-Type': 'application/json' } },
      ),
    )

    try {
      await criarCapacidadeOverride({ data: '2026-10-08', horas: 1 })
      expect.fail('deveria ter lançado ApiError')
    } catch (error) {
      expect(error).toBeInstanceOf(ApiError)
      expect(error).toMatchObject({
        status: 409,
        message: 'Já existe override ativo para 2026-10-08.',
      })
    }
  })
})
