const ACCESS_TOKEN_KEY = 'controle_rotina_access_token'

type TokenListener = (token: string | null) => void

const listeners = new Set<TokenListener>()

function notify(token: string | null): void {
  for (const listener of listeners) {
    listener(token)
  }
}

/** Observa mudanças do access token (ex.: limpeza após refresh falhar). */
export function subscribeAccessToken(listener: TokenListener): () => void {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

export function getAccessToken(): string | null {
  return sessionStorage.getItem(ACCESS_TOKEN_KEY)
}

export function setAccessToken(token: string): void {
  sessionStorage.setItem(ACCESS_TOKEN_KEY, token)
  notify(token)
}

export function clearAccessToken(): void {
  sessionStorage.removeItem(ACCESS_TOKEN_KEY)
  notify(null)
}
