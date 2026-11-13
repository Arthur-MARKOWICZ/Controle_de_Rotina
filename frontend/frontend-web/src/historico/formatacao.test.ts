import { describe, expect, it } from 'vitest'

import {
  formatarDataCurtaPtBr,
  formatarDiaSemanaCurto,
  formatarMinutosTrabalhados,
  formatarRazao,
  fraseCalibracao,
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

describe('formatarRazao', () => {
  it('formata com uma casa e vírgula', () => {
    expect(formatarRazao(1.4)).toBe('1,4×')
    expect(formatarRazao(0.6)).toBe('0,6×')
    expect(formatarRazao(2)).toBe('2,0×')
  })

  it('arredonda meio para cima', () => {
    expect(formatarRazao(1.25)).toBe('1,3×')
    expect(formatarRazao(1.24)).toBe('1,2×')
  })

  it('não inventa número sem dados', () => {
    expect(formatarRazao(null)).toBeNull()
    expect(formatarRazao(Number.NaN)).toBeNull()
    expect(formatarRazao(Number.POSITIVE_INFINITY)).toBeNull()
  })
})

describe('fraseCalibracao', () => {
  it('diz que leva mais que o estimado acima de 1,05', () => {
    expect(fraseCalibracao(1.4)).toBe('Você costuma levar 1,4× o estimado')
    expect(fraseCalibracao(1.06)).toBe('Você costuma levar 1,1× o estimado')
  })

  it('considera em dia nos limites inclusivos 0,95 e 1,05', () => {
    expect(fraseCalibracao(0.95)).toBe('Suas estimativas estão em dia')
    expect(fraseCalibracao(1.03)).toBe('Suas estimativas estão em dia')
    expect(fraseCalibracao(1.05)).toBe('Suas estimativas estão em dia')
  })

  it('diz que termina antes abaixo de 0,95', () => {
    expect(fraseCalibracao(0.8)).toBe('Você costuma terminar em 0,8× do estimado')
    expect(fraseCalibracao(0.94)).toBe('Você costuma terminar em 0,9× do estimado')
  })

  it('devolve null sem dados', () => {
    expect(fraseCalibracao(null)).toBeNull()
    expect(fraseCalibracao(Number.NaN)).toBeNull()
  })
})
