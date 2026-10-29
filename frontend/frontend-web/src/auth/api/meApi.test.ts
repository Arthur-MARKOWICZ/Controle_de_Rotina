import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from './authApi'
import { atualizarMe, buscarMe } from './meApi'
import * as apiClient from '../session/apiClient'

vi.mock('../session/apiClient', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../session/apiClient')>()
  return {
    ...actual,
    apiFetch: vi.fn(),
  }
})

describe('meApi', () => {
  beforeEach(() => {
    vi.mocked(apiClient.apiFetch).mockReset()
  })

  it('busca nome, email e progresso XP em GET /me', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          id: '1',
          nome: 'Ana',
          email: 'ana@email.com',
          xpTotal: 0,
          nivel: 1,
          xpNoNivel: 0,
          xpParaProximoNivel: 10,
        }),
        {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        },
      ),
    )

    const me = await buscarMe()

    expect(apiClient.apiFetch).toHaveBeenCalledWith('/me')
    expect(me).toEqual({
      id: '1',
      nome: 'Ana',
      email: 'ana@email.com',
      xpTotal: 0,
      nivel: 1,
      xpNoNivel: 0,
      xpParaProximoNivel: 10,
    })
  })

  it('atualiza perfil com PUT /me', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          id: '1',
          nome: 'Ana Nova',
          email: 'ana@email.com',
          xpTotal: 0,
          nivel: 1,
          xpNoNivel: 0,
          xpParaProximoNivel: 10,
        }),
        {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        },
      ),
    )

    const body = { senhaAtual: 'Senha@123', nome: 'Ana Nova' }
    const me = await atualizarMe(body)

    expect(apiClient.apiFetch).toHaveBeenCalledWith('/me', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
    expect(me.nome).toBe('Ana Nova')
  })

  it('propaga ApiError com detail do ProblemDetail quando a API falha', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          title: 'Não autorizado',
          detail: 'Senha atual incorreta.',
          status: 401,
        }),
        { status: 401, headers: { 'Content-Type': 'application/problem+json' } },
      ),
    )

    await expect(atualizarMe({ senhaAtual: 'errada' })).rejects.toMatchObject({
      status: 401,
      message: 'Senha atual incorreta.',
    })
    await expect(atualizarMe({ senhaAtual: 'errada' })).rejects.toBeInstanceOf(ApiError)
  })
})
