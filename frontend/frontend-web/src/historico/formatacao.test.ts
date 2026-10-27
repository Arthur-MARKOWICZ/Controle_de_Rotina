import { describe, expect, it } from 'vitest'

import { formatarMinutosTrabalhados } from './formatacao'

describe('formatarMinutosTrabalhados', () => {
  it('formata horas e minutos', () => {
    expect(formatarMinutosTrabalhados(220)).toBe('3h40')
    expect(formatarMinutosTrabalhados(60)).toBe('1h')
    expect(formatarMinutosTrabalhados(45)).toBe('45min')
    expect(formatarMinutosTrabalhados(0)).toBe('0min')
  })
})
