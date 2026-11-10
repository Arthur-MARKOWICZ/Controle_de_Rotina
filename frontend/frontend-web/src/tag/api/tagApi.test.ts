import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as apiClient from '../../auth/session/apiClient'
import { TAG_COR_PADRAO, atualizarTag, criarTag, excluirTag, listarTags } from './tagApi'

vi.mock('../../auth/session/apiClient', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../auth/session/apiClient')>()
  return { ...actual, apiFetch: vi.fn() }
})

const tag = {
  id: 'tag-1',
  nome: 'Foco',
  cor: TAG_COR_PADRAO,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

describe('tagApi', () => {
  beforeEach(() => {
    vi.mocked(apiClient.apiFetch).mockReset()
  })

  it('lista tags via GET /tags', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify([tag]), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    await expect(listarTags()).resolves.toEqual([tag])
    expect(apiClient.apiFetch).toHaveBeenCalledWith('/tags')
  })

  it('cria tag via POST /tags', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify(tag), {
        status: 201,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    const criada = await criarTag({ nome: 'Foco' })
    expect(criada).toEqual(tag)
    expect(apiClient.apiFetch).toHaveBeenCalledWith(
      '/tags',
      expect.objectContaining({ method: 'POST' }),
    )
  })

  it('atualiza tag via PATCH /tags/{id}', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ ...tag, nome: 'Deep' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    await atualizarTag('tag-1', { nome: 'Deep' })
    expect(apiClient.apiFetch).toHaveBeenCalledWith(
      '/tags/tag-1',
      expect.objectContaining({ method: 'PATCH' }),
    )
  })

  it('exclui tag via DELETE /tags/{id}', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(new Response(null, { status: 204 }))
    await excluirTag('tag-1')
    expect(apiClient.apiFetch).toHaveBeenCalledWith('/tags/tag-1', { method: 'DELETE' })
  })
})
