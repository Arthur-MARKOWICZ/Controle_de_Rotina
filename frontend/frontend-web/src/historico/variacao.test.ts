import { describe, expect, it } from 'vitest'

import { formatarVariacaoPercentual, variacaoPercentual } from './variacao'

describe('variacaoPercentual (T1)', () => {
  it('retorna null quando a base é 0', () => {
    expect(variacaoPercentual(120, 0)).toBeNull()
    expect(variacaoPercentual(0, 0)).toBeNull()
  })

  it('calcula variação positiva arredondada', () => {
    expect(variacaoPercentual(112, 100)).toBe(12)
  })

  it('calcula variação negativa arredondada', () => {
    expect(variacaoPercentual(80, 100)).toBe(-20)
  })
})

describe('formatarVariacaoPercentual', () => {
  it('usa traço quando não há base', () => {
    expect(formatarVariacaoPercentual(null)).toBe('—')
  })

  it('formata positivo e negativo com sinal', () => {
    expect(formatarVariacaoPercentual(12)).toBe('+12%')
    expect(formatarVariacaoPercentual(-20)).toBe('-20%')
  })

  it('formata zero sem sinal positivo', () => {
    expect(formatarVariacaoPercentual(0)).toBe('0%')
  })
})
