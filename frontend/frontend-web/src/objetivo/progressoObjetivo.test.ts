import { describe, expect, it } from 'vitest'

import { normalizarProgresso } from './progressoObjetivo'

function progresso(percentual: number, minutosConcluidos: number, minutosTotais: number) {
  return { percentual, minutosConcluidos, minutosTotais }
}

describe('normalizarProgresso', () => {
  it('devolve percentual e texto em tempo no caminho feliz', () => {
    expect(normalizarProgresso(progresso(40, 120, 300))).toEqual({
      pct: 40,
      texto: '2h de 5h concluídas',
    })
  })

  it('mostra 0% com os minutos totais quando nada foi concluído', () => {
    expect(normalizarProgresso(progresso(0, 0, 90))).toEqual({
      pct: 0,
      texto: '0min de 1h 30min concluídas',
    })
  })

  it('mostra 100% quando tudo foi concluído', () => {
    expect(normalizarProgresso(progresso(100, 300, 300))).toEqual({
      pct: 100,
      texto: '5h de 5h concluídas',
    })
  })

  it('limita a 100 o percentual acima do máximo vindo da API, sem mexer nos minutos', () => {
    expect(normalizarProgresso(progresso(130, 45, 30))).toEqual({
      pct: 100,
      texto: '45min de 30min concluídas',
    })
  })

  it('limita a 0 o percentual negativo vindo da API', () => {
    expect(normalizarProgresso(progresso(-5, 0, 60))?.pct).toBe(0)
  })

  it('arredonda percentual não inteiro e trata não numérico como 0', () => {
    expect(normalizarProgresso(progresso(40.6, 10, 25))?.pct).toBe(41)
    expect(normalizarProgresso(progresso(Number.NaN, 10, 25))?.pct).toBe(0)
  })

  it('devolve null quando o progresso é nulo ou indefinido', () => {
    expect(normalizarProgresso(null)).toBeNull()
    expect(normalizarProgresso(undefined)).toBeNull()
  })

  it('devolve null quando o total de minutos é zero, mesmo com objeto presente', () => {
    expect(normalizarProgresso(progresso(0, 0, 0))).toBeNull()
  })

  it('devolve null quando o total de minutos é negativo', () => {
    expect(normalizarProgresso(progresso(50, 10, -30))).toBeNull()
  })
})
