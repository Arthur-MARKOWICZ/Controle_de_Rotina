import { describe, expect, it } from 'vitest'

import { isSenhaForte } from './senhaForte'

describe('isSenhaForte', () => {
  it('aceita senha com 8–72 chars, minúscula, maiúscula, dígito e especial', () => {
    expect(isSenhaForte('Senha@123')).toBe(true)
  })

  it('rejeita senha sem minúscula', () => {
    expect(isSenhaForte('SENHA@123')).toBe(false)
  })

  it('rejeita senha sem maiúscula', () => {
    expect(isSenhaForte('senha@123')).toBe(false)
  })

  it('rejeita senha sem dígito', () => {
    expect(isSenhaForte('Senha@abc')).toBe(false)
  })

  it('rejeita senha sem caractere especial', () => {
    expect(isSenhaForte('Senha1234')).toBe(false)
  })

  it('rejeita senha com menos de 8 caracteres', () => {
    expect(isSenhaForte('Se@1a')).toBe(false)
  })

  it('rejeita senha com mais de 72 caracteres', () => {
    expect(isSenhaForte(`Aa@1${'x'.repeat(70)}`)).toBe(false)
  })

  it('rejeita vazio ou em branco', () => {
    expect(isSenhaForte('')).toBe(false)
    expect(isSenhaForte('   ')).toBe(false)
  })
})
