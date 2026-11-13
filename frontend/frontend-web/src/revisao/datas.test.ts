import { describe, expect, it } from 'vitest'

import { dataHojeIso } from '../planner/data'
import { proximaSegunda } from './datas'

describe('proximaSegunda', () => {
  it('retorna a segunda seguinte num dia de semana comum', () => {
    expect(proximaSegunda('2026-11-13')).toBe('2026-11-16')
  })

  it('no domingo 23:30 BRT retorna amanhã', () => {
    const domingoNoiteBrt = new Date('2026-11-16T02:30:00Z')
    expect(proximaSegunda(dataHojeIso(domingoNoiteBrt))).toBe('2026-11-16')
  })

  it('na segunda 00:05 BRT retorna a segunda da semana que vem', () => {
    const segundaMadrugadaBrt = new Date('2026-11-16T03:05:00Z')
    expect(proximaSegunda(dataHojeIso(segundaMadrugadaBrt))).toBe('2026-11-23')
  })

  it('atravessa a virada de mês', () => {
    expect(proximaSegunda('2026-11-28')).toBe('2026-11-30')
    expect(proximaSegunda('2026-11-30')).toBe('2026-12-07')
  })

  it('atravessa a virada de ano', () => {
    expect(proximaSegunda('2026-12-31')).toBe('2027-01-04')
  })

  it('lança RangeError para formato inválido', () => {
    expect(() => proximaSegunda('13/11/2026')).toThrow(RangeError)
  })

  it('lança RangeError para string vazia', () => {
    expect(() => proximaSegunda('')).toThrow(RangeError)
  })
})
