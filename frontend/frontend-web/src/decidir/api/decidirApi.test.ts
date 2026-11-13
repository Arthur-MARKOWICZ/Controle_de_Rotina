import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../../auth/api/authApi'
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

  it('propaga 400', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ title: 'Bad Request', detail: 'título é obrigatório.' }), {
        status: 400,
      }),
    )
    await expect(criarDecidir({ titulo: '' })).rejects.toMatchObject({
      status: 400,
      message: 'título é obrigatório.',
    })
  })

  it('propaga 401 quando o refresh falhou', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ title: 'Não autorizado', detail: 'Sessão expirada.' }), {
        status: 401,
      }),
    )
    await expect(listarDecidir()).rejects.toMatchObject({ status: 401 })
  })

  it('propaga 404', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ title: 'Não encontrado', detail: 'Item inexistente.' }), {
        status: 404,
      }),
    )
    await expect(buscarDecidir('d-sumiu')).rejects.toMatchObject({ status: 404 })
  })

  it('propaga 409', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ title: 'Conflito', detail: 'Já promovido.' }), { status: 409 }),
    )
    await expect(promoverDecidir('d-1')).rejects.toMatchObject({
      status: 409,
      message: 'Já promovido.',
    })
  })

  it('propaga 500', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ title: 'Erro interno' }), { status: 500 }),
    )
    await expect(listarDecidir()).rejects.toBeInstanceOf(ApiError)
  })

  it('rejeita HTML da SPA e JSON inválido', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response('<!doctype html><html><div id="root"></div></html>', {
        status: 200,
        headers: { 'Content-Type': 'text/html' },
      }),
    )
    await expect(buscarDecidir('d-1')).rejects.toBeInstanceOf(ApiError)

    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response('não-é-json', { status: 200, headers: { 'Content-Type': 'application/json' } }),
    )
    await expect(buscarDecidir('d-1')).rejects.toMatchObject({
      message: 'Resposta inválida da API (não é JSON).',
    })
  })
})
