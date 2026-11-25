import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiFetch, TITULO_CONTA_EXCLUIDA } from './apiClient'
import {
  clearAccessToken,
  getAccessToken,
  limparAvisoContaExcluida,
  setAccessToken,
  temAvisoContaExcluida,
} from './tokenStore'

function problem(status: number, title: string, detail: string): Response {
  return new Response(JSON.stringify({ status, title, detail }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })
}

const refreshOk = () =>
  new Response(JSON.stringify({ accessToken: 'access-novo' }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })

describe('apiFetch com 401 "Conta excluída"', () => {
  beforeEach(() => {
    clearAccessToken()
    limparAvisoContaExcluida()
    vi.restoreAllMocks()
  })

  it('não tenta refresh, limpa a sessão e marca o aviso de conta excluída', async () => {
    setAccessToken('access-1')
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(problem(401, TITULO_CONTA_EXCLUIDA, 'Esta conta foi excluída.'))
    vi.stubGlobal('fetch', fetchMock)

    const response = await apiFetch('/tarefas')

    expect(response.status).toBe(401)
    expect(fetchMock).toHaveBeenCalledOnce()
    expect(getAccessToken()).toBeNull()
    expect(temAvisoContaExcluida()).toBe(true)
  })

  it('mantém o corpo legível para quem chamou', async () => {
    setAccessToken('access-1')
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValueOnce(problem(401, TITULO_CONTA_EXCLUIDA, 'Esta conta foi excluída.')),
    )

    const response = await apiFetch('/tarefas')

    expect(await response.json()).toMatchObject({ title: TITULO_CONTA_EXCLUIDA })
  })

  it('duas chamadas concorrentes com conta excluída nunca chamam /auth/refresh', async () => {
    setAccessToken('access-1')
    const fetchMock = vi
      .fn()
      .mockImplementation(() =>
        Promise.resolve(problem(401, TITULO_CONTA_EXCLUIDA, 'Esta conta foi excluída.')),
      )
    vi.stubGlobal('fetch', fetchMock)

    await Promise.all([apiFetch('/tarefas'), apiFetch('/habitos')])

    expect(fetchMock.mock.calls.map((c) => c[0])).not.toContain('/auth/refresh')
    expect(getAccessToken()).toBeNull()
  })

  it('401 de senha errada (Credenciais inválidas) não renova nem derruba a sessão', async () => {
    setAccessToken('access-1')
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(problem(401, 'Não autorizado', 'Credenciais inválidas.'))
    vi.stubGlobal('fetch', fetchMock)

    const response = await apiFetch('/me', { method: 'DELETE' })

    expect(response.status).toBe(401)
    expect(fetchMock).toHaveBeenCalledOnce()
    expect(getAccessToken()).toBe('access-1')
    expect(temAvisoContaExcluida()).toBe(false)
  })

  it('401 comum (Não autenticado) continua renovando a sessão', async () => {
    setAccessToken('access-expirado')
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(problem(401, 'Não autorizado', 'Não autenticado.'))
      .mockResolvedValueOnce(refreshOk())
      .mockResolvedValueOnce(new Response('{}', { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    const response = await apiFetch('/tarefas')

    expect(response.status).toBe(200)
    expect(fetchMock.mock.calls[1][0]).toBe('/auth/refresh')
    expect(temAvisoContaExcluida()).toBe(false)
  })

  it('401 com HTML do proxy é tratado como 401 comum (refresh)', async () => {
    setAccessToken('access-expirado')
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(new Response('<!doctype html><html></html>', { status: 401 }))
      .mockResolvedValueOnce(refreshOk())
      .mockResolvedValueOnce(new Response('{}', { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    const response = await apiFetch('/tarefas')

    expect(response.status).toBe(200)
    expect(fetchMock.mock.calls[1][0]).toBe('/auth/refresh')
  })

  it('401 com JSON que não é objeto é tratado como 401 comum', async () => {
    setAccessToken('access-expirado')
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(new Response('"texto"', { status: 401 }))
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
    vi.stubGlobal('fetch', fetchMock)

    const response = await apiFetch('/tarefas')

    expect(response.status).toBe(401)
    expect(fetchMock.mock.calls[1][0]).toBe('/auth/refresh')
    expect(temAvisoContaExcluida()).toBe(false)
  })
})
