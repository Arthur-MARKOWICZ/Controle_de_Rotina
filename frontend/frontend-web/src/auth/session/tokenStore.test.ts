import { describe, expect, it, beforeEach, vi } from 'vitest'

import {
  clearAccessToken,
  getAccessToken,
  setAccessToken,
  subscribeAccessToken,
} from './tokenStore'

describe('tokenStore', () => {
  beforeEach(() => {
    clearAccessToken()
  })

  it('guarda e devolve o access token', () => {
    setAccessToken('jwt-abc')
    expect(getAccessToken()).toBe('jwt-abc')
  })

  it('limpa o access token no logout', () => {
    setAccessToken('jwt-abc')
    clearAccessToken()
    expect(getAccessToken()).toBeNull()
  })

  it('notifica assinantes quando o token muda ou é limpo', () => {
    const listener = vi.fn()
    const unsubscribe = subscribeAccessToken(listener)

    setAccessToken('jwt-abc')
    clearAccessToken()
    unsubscribe()
    setAccessToken('jwt-xyz')

    expect(listener).toHaveBeenCalledTimes(2)
    expect(listener).toHaveBeenNthCalledWith(1, 'jwt-abc')
    expect(listener).toHaveBeenNthCalledWith(2, null)
  })
})
