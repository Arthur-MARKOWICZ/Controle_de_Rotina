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
})
