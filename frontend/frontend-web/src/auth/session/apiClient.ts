import { clearAccessToken, getAccessToken, setAccessToken } from './tokenStore'

type AccessTokenResponse = { accessToken: string }

let refreshPromise: Promise<boolean> | null = null

async function tentarRefresh(): Promise<boolean> {
  if (!refreshPromise) {
    refreshPromise = (async () => {
      try {
        const response = await fetch('/auth/refresh', {
          method: 'POST',
          credentials: 'include',
        })
        if (!response.ok) {
          clearAccessToken()
          return false
        }
        const body = (await response.json()) as AccessTokenResponse
        if (!body.accessToken) {
          clearAccessToken()
          return false
        }
        setAccessToken(body.accessToken)
        return true
      } catch {
        clearAccessToken()
        return false
      } finally {
        refreshPromise = null
      }
    })()
  }
  return refreshPromise
}

function buildHeaders(init?: RequestInit): Headers {
  const headers = new Headers(init?.headers)
  const token = getAccessToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  return headers
}

export async function apiFetch(input: string, init?: RequestInit): Promise<Response> {
  const response = await fetch(input, {
    ...init,
    credentials: 'include',
    headers: buildHeaders(init),
  })

  if (response.status !== 401) {
    return response
  }

  const refreshed = await tentarRefresh()
  if (!refreshed) {
    return response
  }

  return fetch(input, {
    ...init,
    credentials: 'include',
    headers: buildHeaders(init),
  })
}
