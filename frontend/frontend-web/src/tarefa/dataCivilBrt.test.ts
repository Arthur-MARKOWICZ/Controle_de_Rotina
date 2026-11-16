import { describe, expect, it } from 'vitest'

import {
  adicionarDiasCivil,
  dataCivilExiste,
  fimDoDiaBrtIso,
  partesCivisBrt,
  proximoDiaSemana,
  weekdayIsoBrt,
} from './dataCivilBrt'

describe('dataCivilBrt', () => {
  it('deveResolverSextaDaMesmaSemanaQuandoHojeEhQuarta', () => {
    // 2026-11-18 é quarta
    expect(proximoDiaSemana(2026, 11, 18, 5)).toEqual({ y: 2026, m: 11, d: 20 })
  })

  it('devePularSeteDiasQuandoODiaPedidoEhHoje', () => {
    // 2026-11-20 é sexta
    expect(proximoDiaSemana(2026, 11, 20, 5)).toEqual({ y: 2026, m: 11, d: 27 })
  })

  it('deveUsarODiaCivilBrtEmDomingo2359', () => {
    // dom 22/11 23:59 BRT = seg 23/11 02:59 UTC
    const hoje = partesCivisBrt(new Date('2026-11-23T02:59:00.000Z'))
    expect(hoje).toEqual({ y: 2026, m: 11, d: 22 })
    expect(proximoDiaSemana(hoje.y, hoje.m, hoje.d, 1)).toEqual({ y: 2026, m: 11, d: 23 })
  })

  it('devePularParaSegundaSeguinteEmSegunda0000Brt', () => {
    // seg 23/11 00:00 BRT = 03:00 UTC
    const hoje = partesCivisBrt(new Date('2026-11-23T03:00:00.000Z'))
    expect(hoje).toEqual({ y: 2026, m: 11, d: 23 })
    expect(proximoDiaSemana(hoje.y, hoje.m, hoje.d, 1)).toEqual({ y: 2026, m: 11, d: 30 })
  })

  it('deveAtravessarViradaDeMes', () => {
    // 2026-11-30 é segunda → próxima quarta 02/12
    expect(proximoDiaSemana(2026, 11, 30, 3)).toEqual({ y: 2026, m: 12, d: 2 })
  })

  it('deveAtravessarViradaDeAno', () => {
    // 2026-12-31 é quinta → próxima quinta 07/01/2027
    expect(weekdayIsoBrt(2026, 12, 31)).toBe(4)
    expect(proximoDiaSemana(2026, 12, 31, 4)).toEqual({ y: 2027, m: 1, d: 7 })
    expect(adicionarDiasCivil(2026, 12, 31, 1)).toEqual({ y: 2027, m: 1, d: 1 })
  })

  it('deveLancarRangeErrorParaDiaDaSemanaForaDe1a7', () => {
    expect(() => proximoDiaSemana(2026, 11, 18, 0)).toThrow(RangeError)
    expect(() => proximoDiaSemana(2026, 11, 18, 8)).toThrow(RangeError)
    expect(() => proximoDiaSemana(2026, 11, 18, 1.5)).toThrow(RangeError)
  })

  it('deveRejeitarDataCivilInexistente', () => {
    expect(dataCivilExiste(2026, 2, 31)).toBe(false)
    expect(dataCivilExiste(2026, 13, 1)).toBe(false)
    expect(dataCivilExiste(2028, 2, 29)).toBe(true)
  })

  it('deveGerarFimDoDiaEmBrtComoInstanteUtc', () => {
    expect(fimDoDiaBrtIso(2026, 11, 20)).toBe('2026-11-21T02:59:00.000Z')
  })
})
