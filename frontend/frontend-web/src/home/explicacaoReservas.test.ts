import { describe, expect, it } from 'vitest'

import { diaSemanaDeDataIso, formatarExplicacaoReservas } from './explicacaoReservas'

describe('explicacaoReservas', () => {
  it('mapeia data ISO para DiaSemana', () => {
    // 2026-10-08 é quinta-feira
    expect(diaSemanaDeDataIso('2026-10-08')).toBe('QUINTA')
    expect(diaSemanaDeDataIso('2026-10-05')).toBe('SEGUNDA')
  })

  it('formata copy reserva vs livre', () => {
    const texto = formatarExplicacaoReservas(
      [
        {
          id: 'r-1',
          projetoId: 'p-tcc',
          diaSemana: 'SEGUNDA',
          minutosReservados: 60,
          createdAt: '2026-01-01T10:00:00Z',
          updatedAt: '2026-01-01T10:00:00Z',
        },
      ],
      { 'p-tcc': 'TCC' },
    )

    expect(texto).toBe(
      '1h reservadas ao projeto TCC; o restante segue a prioridade geral.',
    )
  })

  it('retorna null sem reservas', () => {
    expect(formatarExplicacaoReservas([], {})).toBeNull()
  })

  it('formata minutos soltos e horas quebradas', () => {
    const reserva = {
      id: 'r-1',
      projetoId: 'p-1',
      diaSemana: 'SEGUNDA' as const,
      minutosReservados: 45,
      createdAt: '',
      updatedAt: '',
    }
    expect(formatarExplicacaoReservas([{ ...reserva }], { 'p-1': 'TCC' })).toContain('45 min reservadas')
    expect(
      formatarExplicacaoReservas([{ ...reserva, minutosReservados: 90 }], { 'p-1': 'TCC' }),
    ).toContain('1h 30 min reservadas')
  })

  it('usa fallback de nome quando o projeto não está no mapa', () => {
    expect(
      formatarExplicacaoReservas(
        [
          {
            id: 'r-1',
            projetoId: 'p-x',
            diaSemana: 'SEGUNDA',
            minutosReservados: 60,
            createdAt: '',
            updatedAt: '',
          },
        ],
        {},
      ),
    ).toContain('projeto projeto')
  })

  it('mapeia domingo e sábado', () => {
    expect(diaSemanaDeDataIso('2026-11-01')).toBe('DOMINGO')
    expect(diaSemanaDeDataIso('2026-10-31')).toBe('SABADO')
  })

  it('soma duas reservas no mesmo texto', () => {
    const texto = formatarExplicacaoReservas(
      [
        {
          id: 'r-1',
          projetoId: 'p-a',
          diaSemana: 'SEGUNDA',
          minutosReservados: 60,
          createdAt: '',
          updatedAt: '',
        },
        {
          id: 'r-2',
          projetoId: 'p-b',
          diaSemana: 'SEGUNDA',
          minutosReservados: 30,
          createdAt: '',
          updatedAt: '',
        },
      ],
      { 'p-a': 'A', 'p-b': 'B' },
    )
    expect(texto).toMatch(/A/)
    expect(texto).toMatch(/B/)
  })
})
