import { describe, expect, it } from 'vitest'

import { isEmailValido } from './email'

describe('isEmailValido', () => {
  it('aceita email válido', () => {
    expect(isEmailValido('user@email.com')).toBe(true)
  })

  it('rejeita email sem @', () => {
    expect(isEmailValido('useremail.com')).toBe(false)
  })

  it('rejeita email vazio ou em branco', () => {
    expect(isEmailValido('')).toBe(false)
    expect(isEmailValido('  ')).toBe(false)
  })

  it('aceita email com espaços nas bordas', () => {
    expect(isEmailValido('  user@email.com  ')).toBe(true)
  })

  it('rejeita sem domínio', () => {
    expect(isEmailValido('user@')).toBe(false)
  })

  it('rejeita dois @', () => {
    expect(isEmailValido('user@@email.com')).toBe(false)
  })

  it('rejeita sem TLD', () => {
    expect(isEmailValido('user@email')).toBe(false)
  })
})
