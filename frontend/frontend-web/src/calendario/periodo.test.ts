import { describe, expect, it } from 'vitest'

import {
  celulasGradeMes,
  intervaloMes,
  intervaloSemana,
  labelPeriodo,
  navegarPeriodo,
  parseLente,
  parseVisao,
} from './periodo'

describe('periodo calendário', () => {
  it('intervaloSemana é segunda → domingo', () => {
    expect(intervaloSemana('2026-10-28')).toEqual({
      dataDe: '2026-10-26',
      dataAte: '2026-11-01',
    })
  })

  it('intervaloMes cobre o mês civil', () => {
    expect(intervaloMes('2026-10-15')).toEqual({
      dataDe: '2026-10-01',
      dataAte: '2026-10-31',
    })
  })

  it('navegarPeriodo avança semana e mês', () => {
    expect(navegarPeriodo('semana', '2026-10-26', 1)).toBe('2026-11-02')
    expect(navegarPeriodo('mes', '2026-10-01', 1)).toBe('2026-11-01')
    expect(navegarPeriodo('mes', '2026-10-01', -1)).toBe('2026-09-01')
  })

  it('celulasGradeMes começa na segunda e preenche padding', () => {
    const celulas = celulasGradeMes('2026-10-15')
    expect(celulas.length % 7).toBe(0)
    // 1/out/2026 é quinta → 3 células de padding
    expect(celulas.slice(0, 3).every((c) => c.foraDoMes && c.data === null)).toBe(true)
    expect(celulas[3]).toEqual({ data: '2026-10-01', foraDoMes: false })
    expect(celulas.find((c) => c.data === '2026-10-31')).toEqual({
      data: '2026-10-31',
      foraDoMes: false,
    })
  })

  it('parseVisao e parseLente com defaults', () => {
    expect(parseVisao(null)).toBe('semana')
    expect(parseVisao('mes')).toBe('mes')
    expect(parseLente(null)).toBe('plano')
    expect(parseLente('prazos')).toBe('prazos')
  })

  it('labelPeriodo formata semana e mês', () => {
    expect(labelPeriodo('semana', '2026-10-26')).toMatch(/26/)
    expect(labelPeriodo('mes', '2026-10-01')).toMatch(/outubro/i)
  })
})
