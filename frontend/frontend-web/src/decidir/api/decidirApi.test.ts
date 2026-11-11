import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as apiClient from '../../auth/session/apiClient'
import {
  buildDecidirQuery,
  buscarDecidir,
  criarDecidir,
  excluirDecidir,
  listarDecidir,
  promoverDecidir,
} from './decidirApi'

vi.mock('../../auth/session/apiClient', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../auth/session/apiClient')>()
  return { ...actual, apiFetch: vi.fn() }
})

const item = {
  id: 'd-1',
  titulo: 'Comprar assinatura',
  descricao: null,
  decidirAte: null,
  projetoId: null,
  tarefaId: null,
  tags: [],
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

describe('decidirApi', () => {
  beforeEach(() => {
    vi.mocked(apiClient.apiFetch).mockReset()
  })

  it('monta query com promovidos e tagId', () => {
    expect(buildDecidirQuery({ promovidos: true, tagIds: ['a', 'b'], page: 1, size: 10 })).toBe(
      '/decidir?promovidos=true&tagId=a&tagId=b&page=1&size=10',
    )
  })

  it('lista Decidir via GET', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ content: [item], totalElements: 1, totalPages: 1, number: 0, size: 20 }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    const page = await listarDecidir({ tagIds: ['t1'] })
    expect(page.content).toHaveLength(1)
    expect(apiClient.apiFetch).toHaveBeenCalledWith('/decidir?tagId=t1&page=0&size=20')
  })

  it('busca item via GET /decidir/{id}', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify(item), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    await expect(buscarDecidir('d-1')).resolves.toEqual(item)
    expect(apiClient.apiFetch).toHaveBeenCalledWith('/decidir/d-1')
  })

  it('cria item via POST', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify(item), {
        status: 201,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    await criarDecidir({ titulo: 'Comprar assinatura' })
    expect(apiClient.apiFetch).toHaveBeenCalledWith(
      '/decidir',
      expect.objectContaining({ method: 'POST' }),
    )
  })

  it('promove e descarta', async () => {
    vi.mocked(apiClient.apiFetch)
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ ...item, tarefaId: 't-1' }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      )
      .mockResolvedValueOnce(new Response(null, { status: 204 }))

    await promoverDecidir('d-1', { duracaoMinutos: 45 })
    expect(apiClient.apiFetch).toHaveBeenCalledWith(
      '/decidir/d-1/promover',
      expect.objectContaining({ method: 'POST' }),
    )
    await excluirDecidir('d-1')
    expect(apiClient.apiFetch).toHaveBeenCalledWith('/decidir/d-1', { method: 'DELETE' })
  })
})
