import { describe, expect, it } from 'vitest'

import { truncarDescricao } from './truncarDescricao'

describe('truncarDescricao', () => {
  it('mantém texto curto e remove espaços das pontas', () => {
    expect(truncarDescricao('  Correr 3x por semana  ')).toBe('Correr 3x por semana')
  })

  it('texto com exatamente o limite não é cortado', () => {
    expect(truncarDescricao('a'.repeat(120))).toBe('a'.repeat(120))
  })

  it('texto acima do limite termina em reticências com o tamanho máximo', () => {
    const curto = truncarDescricao('a'.repeat(121))
    expect(curto).toBe(`${'a'.repeat(119)}…`)
    expect(curto).toHaveLength(120)
  })

  it('respeita limite customizado', () => {
    expect(truncarDescricao('abcdef', 4)).toBe('abc…')
  })

  it('null e undefined viram null', () => {
    expect(truncarDescricao(null)).toBeNull()
    expect(truncarDescricao(undefined)).toBeNull()
  })

  it('string vazia vira null', () => {
    expect(truncarDescricao('')).toBeNull()
  })

  it('só espaços vira null', () => {
    expect(truncarDescricao('   \n  ')).toBeNull()
  })
})
