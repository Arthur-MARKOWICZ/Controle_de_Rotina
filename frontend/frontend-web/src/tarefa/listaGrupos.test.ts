import { describe, expect, it } from 'vitest'

import type { TarefaResponse } from './api/tarefaApi'
import {
  agruparPorPrazo,
  classificarGrupoPrazo,
  fimDaSemanaBrt,
  formatarPrazoLista,
} from './listaGrupos'

function tarefa(parcial: Partial<TarefaResponse> & { id: string; prazo: string }): TarefaResponse {
  return {
    titulo: parcial.titulo ?? parcial.id,
    descricao: '',
    prioridade: 'MEDIA',
    status: 'PENDENTE',
    duracaoHoras: 1,
    createdAt: '2026-10-01T00:00:00Z',
    updatedAt: '2026-10-01T00:00:00Z',
    deletedAt: null,
    deletada: false,
    ...parcial,
  }
}

describe('listaGrupos (T3)', () => {
  // Quarta 2026-10-28 15:00 UTC = 12:00 BRT
  const ref = new Date('2026-10-28T15:00:00.000Z')

  it('fim da semana BRT de uma quarta é domingo 2026-11-01', () => {
    expect(fimDaSemanaBrt('2026-10-28')).toBe('2026-11-01')
  })

  it('classifica atrasadas / hoje / esta semana / depois', () => {
    expect(classificarGrupoPrazo('2026-10-27T15:00:00Z', ref)).toBe('atrasadas')
    expect(classificarGrupoPrazo('2026-10-28T20:00:00Z', ref)).toBe('hoje')
    expect(classificarGrupoPrazo('2026-10-30T15:00:00Z', ref)).toBe('esta_semana')
    expect(classificarGrupoPrazo('2026-11-05T15:00:00Z', ref)).toBe('depois')
  })

  it('formatarPrazoLista usa textos curtos do plano', () => {
    expect(formatarPrazoLista('2026-10-29T15:00:00Z', ref)).toBe('amanhã')
    expect(formatarPrazoLista('2026-10-31T15:00:00Z', ref)).toBe('em 3 dias')
    expect(formatarPrazoLista('2026-10-26T15:00:00Z', ref)).toBe('atrasada há 2 dias')
  })

  it('agrupa com contadores e omite vazios', () => {
    const grupos = agruparPorPrazo(
      [
        tarefa({ id: 'a', prazo: '2026-10-20T12:00:00Z', titulo: 'Atrasada' }),
        tarefa({ id: 'h', prazo: '2026-10-28T18:00:00Z', titulo: 'Hoje' }),
        tarefa({ id: 's', prazo: '2026-10-31T12:00:00Z', titulo: 'Semana' }),
        tarefa({ id: 'd', prazo: '2026-11-10T12:00:00Z', titulo: 'Depois' }),
      ],
      ref,
    )

    expect(grupos.map((g) => `${g.label} (${g.tarefas.length})`)).toEqual([
      'Atrasadas (1)',
      'Hoje (1)',
      'Esta semana (1)',
      'Depois (1)',
    ])
    expect(grupos.find((g) => g.id === 'sem_prazo')).toBeUndefined()
  })
})
