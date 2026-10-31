import { describe, expect, it } from 'vitest'

import {
  formatarDataCurtaPtBr,
  formatarDiaSemanaCurto,
  formatarMinutosTrabalhados,
  tituloDiaHistorico,
  tituloSemanaHistorico,
} from './formatacao'

describe('formatarMinutosTrabalhados', () => {
  it('formata horas e minutos', () => {
    expect(formatarMinutosTrabalhados(220)).toBe('3h40')
    expect(formatarMinutosTrabalhados(60)).toBe('1h')
    expect(formatarMinutosTrabalhados(45)).toBe('45min')
    expect(formatarMinutosTrabalhados(0)).toBe('0min')
  })
})

describe('tituloDiaHistorico (T2)', () => {
  it('retorna Hoje quando a data é o dia atual', () => {
    expect(tituloDiaHistorico('2026-10-27', '2026-10-27')).toBe('Hoje')
  })

  it('retorna Ontem quando a data é o dia anterior', () => {
    expect(tituloDiaHistorico('2026-10-26', '2026-10-27')).toBe('Ontem')
  })

  it('formata outra data em pt-BR (ex.: Segunda, 20/10)', () => {
    expect(tituloDiaHistorico('2026-10-20', '2026-10-27')).toBe('Terça, 20/10')
  })
})

describe('tituloSemanaHistorico', () => {
  it('distingue semana atual, passada e outras', () => {
    expect(tituloSemanaHistorico('2026-10-26', '2026-10-26')).toBe('Esta semana')
    expect(tituloSemanaHistorico('2026-10-19', '2026-10-26')).toBe('Semana passada')
    expect(tituloSemanaHistorico('2026-10-12', '2026-10-26')).toBe('Semana selecionada')
  })
})

describe('formatarDataCurtaPtBr', () => {
  it('formata ISO como dd/mm/aaaa', () => {
    expect(formatarDataCurtaPtBr('2026-10-27')).toBe('27/10/2026')
  })
})

describe('formatarDiaSemanaCurto', () => {
  it('retorna abreviação do dia em pt-BR', () => {
    expect(formatarDiaSemanaCurto('2026-10-26')).toBe('Seg')
    expect(formatarDiaSemanaCurto('2026-10-27')).toBe('Ter')
  })
})
