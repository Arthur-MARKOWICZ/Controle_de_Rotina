import { afterEach, describe, expect, it, vi } from 'vitest'

import { logout } from './authApi'

describe('authApi.logout', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('envia Authorization Bearer quando há access token', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await logout('access-jwt')

    expect(fetchMock).toHaveBeenCalledOnce()
    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    const headers = new Headers(init.headers)
    expect(headers.get('Authorization')).toBe('Bearer access-jwt')
    expect(init.credentials).toBe('include')
    expect(init.method).toBe('POST')
  })

  it('omite Authorization quando não há access token', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await logout(null)

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    const headers = new Headers(init.headers)
    expect(headers.get('Authorization')).toBeNull()
  })

  it('resolve em 400 sem lançar', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 400 })))
    await expect(logout('jwt')).resolves.toBeUndefined()
  })

  it('resolve em 401 sem lançar (estado de sessão fica a cargo do caller)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 401 })))
    await expect(logout('jwt-expirado')).resolves.toBeUndefined()
  })

  it('resolve em 404 e 409 sem lançar', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 404 })))
    await expect(logout('jwt')).resolves.toBeUndefined()
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 409 })))
    await expect(logout('jwt')).resolves.toBeUndefined()
  })

  it('resolve em 500 sem lançar', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 500 })))
    await expect(logout('jwt')).resolves.toBeUndefined()
  })

  it('resolve quando o corpo vem HTML da SPA', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response('<!doctype html>', { status: 200, headers: { 'Content-Type': 'text/html' } }),
      ),
    )
    await expect(logout('jwt')).resolves.toBeUndefined()
  })
})
