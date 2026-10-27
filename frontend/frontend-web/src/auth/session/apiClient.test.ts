import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiFetch } from './apiClient'
import { clearAccessToken, getAccessToken, setAccessToken } from './tokenStore'

describe('apiFetch', () => {
  beforeEach(() => {
    clearAccessToken()
    vi.restoreAllMocks()
  })

  it('envia Authorization Bearer e evita cache HTTP', async () => {
    setAccessToken('access-1')
    const fetchMock = vi.fn().mockResolvedValue(new Response('{}', { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    await apiFetch('/qualquer')

    expect(fetchMock).toHaveBeenCalledOnce()
    const init = fetchMock.mock.calls[0][1]
    const headers = init?.headers as Headers
    expect(headers.get('Authorization')).toBe('Bearer access-1')
    expect(headers.get('Accept')).toBe('application/json')
    expect(init?.cache).toBe('no-store')
  })

  it('em 401 tenta refresh e refaz a requisição com o novo token', async () => {
    setAccessToken('access-expirado')
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ accessToken: 'access-novo' }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      )
      .mockResolvedValueOnce(new Response('{"ok":true}', { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    const response = await apiFetch('/protegido')

    expect(response.status).toBe(200)
    expect(getAccessToken()).toBe('access-novo')
    expect(fetchMock).toHaveBeenCalledTimes(3)
    expect(fetchMock.mock.calls[1][0]).toBe('/auth/refresh')
    const retryHeaders = fetchMock.mock.calls[2][1]?.headers as Headers
    expect(retryHeaders.get('Authorization')).toBe('Bearer access-novo')
  })

  it('se o refresh falhar, limpa o access token', async () => {
    setAccessToken('access-expirado')
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
    vi.stubGlobal('fetch', fetchMock)

    const response = await apiFetch('/protegido')

    expect(response.status).toBe(401)
    expect(getAccessToken()).toBeNull()
  })
})
