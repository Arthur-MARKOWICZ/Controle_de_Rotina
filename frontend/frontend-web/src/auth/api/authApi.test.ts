import { afterEach, describe, expect, it, vi } from 'vitest'

import { ApiError, cadastrarUsuario, login, logout } from './authApi'

describe('authApi', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('login 2xx devolve accessToken', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ accessToken: 'jwt-ok' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await expect(login({ email: 'ana@email.com', senha: 'Senha@123' })).resolves.toEqual({
      accessToken: 'jwt-ok',
    })
    expect(fetchMock).toHaveBeenCalledWith('/auth/login', expect.objectContaining({ method: 'POST' }))
  })

  it('cadastro 2xx devolve o usuário', async () => {
    const usuario = {
      id: 'u-1',
      nome: 'Ana',
      email: 'ana@email.com',
      xpTotal: 0,
      nivel: 1,
      xpNoNivel: 0,
      xpParaProximoNivel: 10,
    }
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify(usuario), {
          status: 201,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    )

    await expect(
      cadastrarUsuario({ nome: 'Ana', email: 'ana@email.com', senha: 'Senha@123' }),
    ).resolves.toEqual(usuario)
  })

  it('propaga 400 de validação no login', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ title: 'Bad Request', detail: 'E-mail inválido.' }), {
          status: 400,
        }),
      ),
    )
    await expect(login({ email: 'x', senha: 'Senha@123' })).rejects.toMatchObject({
      status: 400,
      message: 'E-mail inválido.',
    })
  })

  it('propaga 401 de credenciais inválidas', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ title: 'Não autorizado', detail: 'Credenciais inválidas.' }), {
          status: 401,
        }),
      ),
    )
    await expect(login({ email: 'ana@email.com', senha: 'errada' })).rejects.toMatchObject({
      status: 401,
      message: 'Credenciais inválidas.',
    })
  })

  it('propaga 404 no cadastro', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ title: 'Não encontrado' }), { status: 404 }),
      ),
    )
    await expect(
      cadastrarUsuario({ nome: 'Ana', email: 'ana@email.com', senha: 'Senha@123' }),
    ).rejects.toMatchObject({ status: 404 })
  })

  it('propaga 409 de e-mail duplicado no cadastro', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ title: 'Conflito', detail: 'E-mail já cadastrado.' }), {
          status: 409,
        }),
      ),
    )
    await expect(
      cadastrarUsuario({ nome: 'Ana', email: 'ana@email.com', senha: 'Senha@123' }),
    ).rejects.toMatchObject({ status: 409, message: 'E-mail já cadastrado.' })
  })

  it('propaga 500', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(new Response(JSON.stringify({ title: 'Erro interno' }), { status: 500 })),
    )
    await expect(login({ email: 'ana@email.com', senha: 'Senha@123' })).rejects.toBeInstanceOf(ApiError)
  })

  it('rejeita HTML da SPA e JSON inválido no 2xx', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response('<!doctype html><html><div id="root"></div></html>', {
          status: 200,
          headers: { 'Content-Type': 'text/html' },
        }),
      ),
    )
    await expect(login({ email: 'ana@email.com', senha: 'Senha@123' })).rejects.toThrow()

    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response('não-é-json', { status: 200, headers: { 'Content-Type': 'application/json' } }),
      ),
    )
    await expect(login({ email: 'ana@email.com', senha: 'Senha@123' })).rejects.toThrow()
  })

  it('logout 204 completa mesmo sem access token', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)
    await expect(logout(null)).resolves.toBeUndefined()
    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(new Headers(init.headers).get('Authorization')).toBeNull()
  })
})
