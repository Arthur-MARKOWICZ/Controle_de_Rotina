import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../../auth/api/authApi'
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

  it('propaga 400', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ title: 'Bad Request', detail: 'Nome vazio.' }), { status: 400 }),
    )
    await expect(criarTag({ nome: '' })).rejects.toMatchObject({
      status: 400,
      message: 'Nome vazio.',
    })
  })

  it('propaga 401 quando o refresh falhou', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ title: 'Não autorizado', detail: 'Sessão expirada.' }), {
        status: 401,
      }),
    )
    await expect(listarTags()).rejects.toMatchObject({ status: 401 })
  })

  it('propaga 404', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ title: 'Não encontrado', detail: 'Tag inexistente.' }), {
        status: 404,
      }),
    )
    await expect(atualizarTag('sumiu', { nome: 'X' })).rejects.toMatchObject({ status: 404 })
  })

  it('propaga 409 de nome duplicado', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ title: 'Tag já existe', detail: 'Nome duplicado.', nome: 'Foco' }), {
        status: 409,
      }),
    )
    await expect(criarTag({ nome: 'Foco' })).rejects.toMatchObject({
      status: 409,
      message: 'Nome duplicado.',
    })
  })

  it('propaga 500', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ title: 'Erro interno' }), { status: 500 }),
    )
    await expect(listarTags()).rejects.toBeInstanceOf(ApiError)
  })

  it('rejeita HTML da SPA e JSON inválido', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response('<!doctype html><html><div id="root"></div></html>', {
        status: 200,
        headers: { 'Content-Type': 'text/html' },
      }),
    )
    await expect(listarTags()).rejects.toBeInstanceOf(ApiError)

    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response('não-é-json', { status: 200, headers: { 'Content-Type': 'application/json' } }),
    )
    await expect(listarTags()).rejects.toMatchObject({
      message: 'Resposta inválida da API (não é JSON).',
    })
  })
})
