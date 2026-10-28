import { describe, expect, it } from 'vitest'

import { isSenhaForte, regrasSenha } from './senhaForte'

describe('regrasSenha (T1)', () => {
  it('marca cada regra ao digitar caractere que a satisfaz', () => {
    expect(regrasSenha('').every((r) => !r.ok)).toBe(true)

    const comMinuscula = regrasSenha('a')
    expect(comMinuscula.find((r) => r.id === 'minuscula')?.ok).toBe(true)
    expect(comMinuscula.find((r) => r.id === 'maiuscula')?.ok).toBe(false)

    const comMaiuscula = regrasSenha('aA')
    expect(comMaiuscula.find((r) => r.id === 'maiuscula')?.ok).toBe(true)

    const comDigito = regrasSenha('aA1')
    expect(comDigito.find((r) => r.id === 'digito')?.ok).toBe(true)

    const comEspecial = regrasSenha('aA1@')
    expect(comEspecial.find((r) => r.id === 'especial')?.ok).toBe(true)

    const completa = regrasSenha('Senha@12')
    expect(completa.every((r) => r.ok)).toBe(true)
  })
})

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
