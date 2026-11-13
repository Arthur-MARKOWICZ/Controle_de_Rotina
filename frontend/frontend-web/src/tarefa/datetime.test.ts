import { describe, expect, it } from 'vitest'

import {
  datetimeLocalParaInstant,
  formatarDataHora,
  instantParaDatetimeLocal,
} from './datetime'

describe('datetime', () => {
  it('converte Instant ISO para datetime-local no fuso local', () => {
    const iso = '2026-10-20T15:30:00.000Z'
    const local = instantParaDatetimeLocal(iso)

    expect(local).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/)
    expect(datetimeLocalParaInstant(local)).toBe(new Date(iso).toISOString())
  })

  it('devolve string vazia para prazo nulo ou indefinido', () => {
    expect(instantParaDatetimeLocal(null)).toBe('')
    expect(instantParaDatetimeLocal(undefined)).toBe('')
    expect(instantParaDatetimeLocal('')).toBe('')
  })

  it('devolve string vazia para Instant inválido sem lançar', () => {
    expect(instantParaDatetimeLocal('não-é-data')).toBe('')
    expect(instantParaDatetimeLocal('2026-13-99T99:99:99Z')).toBe('')
  })

  it('converte datetime-local preenchido para Instant ISO', () => {
    const instant = datetimeLocalParaInstant('2026-10-20T12:00')

    expect(instant).toBe(new Date('2026-10-20T12:00').toISOString())
  })

  it('converte datetime-local vazio ou só espaços para null', () => {
    expect(datetimeLocalParaInstant('')).toBeNull()
    expect(datetimeLocalParaInstant('   ')).toBeNull()
  })

  it('formata prazo ausente como Sem prazo', () => {
    expect(formatarDataHora(null)).toBe('Sem prazo')
    expect(formatarDataHora(undefined)).toBe('Sem prazo')
    expect(formatarDataHora('')).toBe('Sem prazo')
  })

  it('devolve o texto original quando a data é inválida', () => {
    expect(formatarDataHora('prazo-quebrado')).toBe('prazo-quebrado')
  })

  it('formata Instant válido em pt-BR', () => {
    const texto = formatarDataHora('2026-10-20T15:30:00.000Z')

    expect(texto).toMatch(/\d/)
    expect(texto).not.toBe('Sem prazo')
    expect(texto).not.toBe('2026-10-20T15:30:00.000Z')
  })
})
