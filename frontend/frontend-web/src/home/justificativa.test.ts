import { describe, expect, it } from 'vitest'

import {
  escolherBlocoMenorPrioridade,
  formatarJustificativaBloco,
  formatarPrazoRelativo,
} from './justificativa'

describe('justificativa', () => {
  const ref = new Date('2026-10-20T15:00:00-03:00')

  it('formata prazo relativo em PT', () => {
    expect(formatarPrazoRelativo('2026-10-20T12:00:00Z', ref)).toBe('Prazo hoje')
    expect(formatarPrazoRelativo('2026-10-21T12:00:00Z', ref)).toBe('Prazo amanhã')
    expect(formatarPrazoRelativo('2026-10-22T12:00:00Z', ref)).toBe('Prazo em 2 dias')
    expect(formatarPrazoRelativo('2026-10-19T12:00:00Z', ref)).toBe('Atrasada há 1 dia')
  })

  it('monta justificativa com prazo e prioridade', () => {
    expect(formatarJustificativaBloco('2026-10-22T12:00:00Z', 'ALTA', ref)).toBe(
      'Prazo em 2 dias · prioridade alta',
    )
  })

  it('escolhe bloco de menor prioridade', () => {
    const escolhido = escolherBlocoMenorPrioridade([
      { id: 'a', ordem: 1, tarefa: { prioridade: 'ALTA' as const } },
      { id: 'b', ordem: 2, tarefa: { prioridade: 'BAIXA' as const } },
      { id: 'c', ordem: 3, tarefa: { prioridade: 'MEDIA' as const } },
    ])
    expect(escolhido?.id).toBe('b')
  })
})
