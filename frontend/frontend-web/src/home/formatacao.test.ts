import { describe, expect, it } from 'vitest'

import { formatarDataIsoPorExtenso, formatarDataPorExtenso, formatarDiaCurto } from './formatacao'

describe('home/formatacao', () => {
  it('rótulo curto de dia legível (ter 04/11)', () => {
    expect(formatarDiaCurto('2025-11-04')).toBe('ter 04/11')
  })

  it('data ISO por extenso mantém o dia civil', () => {
    expect(formatarDataIsoPorExtenso('2026-10-27')).toMatch(/^Terça.*27 de outubro/i)
  })

  it('virada de ano mantém o dia civil', () => {
    expect(formatarDiaCurto('2025-12-31')).toBe('qua 31/12')
    expect(formatarDataIsoPorExtenso('2026-01-01')).toMatch(/1 de janeiro/i)
  })

  it('domingo e sábado usam weekday curto', () => {
    expect(formatarDiaCurto('2026-11-01')).toMatch(/^dom 01\/11$/i)
    expect(formatarDiaCurto('2026-10-31')).toMatch(/^sáb 31\/10$/i)
  })

  it('formatarDataPorExtenso capitaliza o weekday', () => {
    expect(formatarDataPorExtenso(new Date('2026-10-27T15:00:00Z'))).toMatch(/^[A-ZÁÉÍÓÚ]/)
  })

  it('mesmo ISO em fusos diferentes do runner produz o mesmo rótulo curto', () => {
    expect(formatarDiaCurto('2026-10-08')).toBe('qui 08/10')
  })

  it('ano bissexto formata 29 de fevereiro', () => {
    expect(formatarDiaCurto('2024-02-29')).toBe('qui 29/02')
    expect(formatarDataIsoPorExtenso('2024-02-29')).toMatch(/29 de fevereiro/i)
  })
})
