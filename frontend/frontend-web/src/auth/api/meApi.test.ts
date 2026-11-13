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

  it('propaga 400 de validação', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ title: 'Dados inválidos', detail: 'Nome muito longo.' }), {
        status: 400,
      }),
    )
    await expect(atualizarMe({ senhaAtual: 'Senha@123', nome: 'A'.repeat(300) })).rejects.toMatchObject({
      status: 400,
      message: 'Nome muito longo.',
    })
  })

  it('propaga 404', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ title: 'Não encontrado', detail: 'Usuário não encontrado.' }), {
        status: 404,
      }),
    )
    await expect(buscarMe()).rejects.toMatchObject({ status: 404 })
  })

  it('propaga 409', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ title: 'Conflito', detail: 'E-mail já em uso.' }), {
        status: 409,
      }),
    )
    await expect(
      atualizarMe({ senhaAtual: 'Senha@123', email: 'outro@email.com' }),
    ).rejects.toMatchObject({ status: 409, message: 'E-mail já em uso.' })
  })

  it('propaga 500', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ title: 'Erro interno' }), { status: 500 }),
    )
    await expect(buscarMe()).rejects.toMatchObject({ status: 500, message: 'Erro interno' })
  })

  it('rejeita HTML da SPA no GET /me', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response('<!doctype html><html><div id="root"></div></html>', {
        status: 200,
        headers: { 'Content-Type': 'text/html' },
      }),
    )
    await expect(buscarMe()).rejects.toThrow()
  })

  it('rejeita JSON inválido', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response('não-é-json', { status: 200, headers: { 'Content-Type': 'application/json' } }),
    )
    await expect(buscarMe()).rejects.toThrow()
  })
})
