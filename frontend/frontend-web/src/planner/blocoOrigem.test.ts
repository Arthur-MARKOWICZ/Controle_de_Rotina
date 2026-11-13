import { describe, expect, it } from 'vitest'

import type { BlocoPlanoResponse } from './api/planoApi'
import {
  chaveOrigemBloco,
  ehBlocoHabito,
  origemDoBloco,
  tarefaIdsNoPlano,
  tituloDoBloco,
} from './blocoOrigem'

const tarefa: BlocoPlanoResponse = {
  id: 'b-1',
  duracaoMinutos: 60,
  ordem: 1,
  origem: 'TAREFA',
  tarefaId: 't-1',
  tarefa: {
    id: 't-1',
    titulo: 'Estudar',
    prioridade: 'MEDIA',
    status: 'PENDENTE',
    duracaoMinutos: 60,
    prazo: null,
  },
}

const habito: BlocoPlanoResponse = {
  id: 'b-2',
  duracaoMinutos: 30,
  ordem: 2,
  origem: 'HABITO',
  habitoId: 'h-1',
  habito: { id: 'h-1', titulo: 'Correr', horarioPreferencial: '07:00:00', streakAtual: 3 },
}

describe('blocoOrigem', () => {
  it('usa origem explícita quando presente', () => {
    expect(origemDoBloco(tarefa)).toBe('TAREFA')
    expect(origemDoBloco(habito)).toBe('HABITO')
    expect(ehBlocoHabito(habito)).toBe(true)
    expect(ehBlocoHabito(tarefa)).toBe(false)
  })

  it('trata fixture antiga sem origem como TAREFA', () => {
    const legado: BlocoPlanoResponse = { id: 'b-3', duracaoMinutos: 20, ordem: 1 }
    expect(origemDoBloco(legado)).toBe('TAREFA')
    expect(ehBlocoHabito(legado)).toBe(false)
    expect(tituloDoBloco(legado)).toBe('Tarefa')
  })

  it('infere hábito pelo habitoId mesmo sem origem', () => {
    const inferido: BlocoPlanoResponse = { id: 'b-4', duracaoMinutos: 20, ordem: 1, habitoId: 'h-9' }
    expect(origemDoBloco(inferido)).toBe('HABITO')
    expect(tituloDoBloco(inferido)).toBe('Hábito')
    expect(chaveOrigemBloco(inferido)).toBe('H:h-9')
  })

  it('não promove bloco de tarefa a hábito por ter habito nulo', () => {
    const misto: BlocoPlanoResponse = { ...tarefa, habitoId: null, habito: null }
    expect(origemDoBloco(misto)).toBe('TAREFA')
    expect(tarefaIdsNoPlano([misto])).toEqual(['t-1'])
  })

  it('devolve título do hábito ou fallback', () => {
    expect(tituloDoBloco(habito)).toBe('Correr')
    expect(tituloDoBloco({ ...habito, habito: null })).toBe('Hábito')
    expect(tituloDoBloco(tarefa)).toBe('Estudar')
  })

  it('chave estável usa id da origem e não o id do bloco', () => {
    expect(chaveOrigemBloco(tarefa)).toBe('T:t-1')
    expect(chaveOrigemBloco(habito)).toBe('H:h-1')
    expect(chaveOrigemBloco({ ...tarefa, id: 'bloco-novo' })).toBe('T:t-1')
  })

  it('lista só ids de tarefa e ignora hábitos', () => {
    const semId: BlocoPlanoResponse = { id: 'b-5', duracaoMinutos: 10, ordem: 3 }
    expect(tarefaIdsNoPlano([tarefa, habito, semId])).toEqual(['t-1'])
    expect(tarefaIdsNoPlano([])).toEqual([])
  })
})
