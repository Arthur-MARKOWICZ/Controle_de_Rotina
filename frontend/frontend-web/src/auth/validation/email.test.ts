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
})
