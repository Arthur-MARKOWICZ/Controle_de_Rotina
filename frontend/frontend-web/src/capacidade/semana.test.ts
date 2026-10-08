import { describe, expect, it } from 'vitest'

import {
  adicionarDiasIso,
  datasDaSemana,
  fimSemana,
  formatarDataIso,
  inicioSemana,
} from './semana'

describe('semana', () => {
  it('calcula segunda e domingo da semana (quarta 2026-10-08)', () => {
    const ref = new Date(2026, 9, 8, 15, 0, 0)
    expect(inicioSemana(ref)).toBe('2026-10-05')
    expect(fimSemana(ref)).toBe('2026-10-11')
  })

  it('trata domingo como fim da semana corrente', () => {
    const domingo = new Date(2026, 9, 11, 10, 0, 0)
    expect(inicioSemana(domingo)).toBe('2026-10-05')
    expect(fimSemana(domingo)).toBe('2026-10-11')
  })

  it('mapeia Seg–Dom para datas da semana', () => {
    const mapa = datasDaSemana('2026-10-05')
    expect(mapa.SEGUNDA).toBe('2026-10-05')
    expect(mapa.QUARTA).toBe('2026-10-07')
    expect(mapa.DOMINGO).toBe('2026-10-11')
  })

  it('adiciona dias em ISO local', () => {
    expect(adicionarDiasIso('2026-10-05', 7)).toBe('2026-10-12')
    expect(formatarDataIso(new Date(2026, 9, 5))).toBe('2026-10-05')
  })
})
