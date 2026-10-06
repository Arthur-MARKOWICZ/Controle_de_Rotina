import { describe, expect, it, beforeEach } from 'vitest'

import { clearAccessToken, getAccessToken, setAccessToken } from './tokenStore'

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
})
