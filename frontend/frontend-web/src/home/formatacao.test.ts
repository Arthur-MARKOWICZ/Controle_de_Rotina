import { describe, expect, it } from 'vitest'

import { formatarDataIsoPorExtenso, formatarDiaCurto } from './formatacao'

describe('home/formatacao', () => {
  it('rótulo curto de dia legível (ter 04/11)', () => {
    expect(formatarDiaCurto('2025-11-04')).toBe('ter 04/11')
  })

  it('data ISO por extenso mantém o dia civil', () => {
    expect(formatarDataIsoPorExtenso('2026-10-27')).toMatch(/^Terça.*27 de outubro/i)
  })
})
