import { describe, expect, it } from 'vitest'

import type { TarefaResponse } from './api/tarefaApi'
import {
  ehVisaoArvore,
  ehVisaoPlana,
  filhosNaPagina,
  tarefasNoNivelDaLista,
  temFiltroIsolador,
} from './listaArvore'
import { FILTROS_LISTA_INICIAIS } from './listaUrl'

function tarefa(parcial: Partial<TarefaResponse> & { id: string }): TarefaResponse {
  return {
    titulo: parcial.titulo ?? parcial.id,
    descricao: '',
    prioridade: 'MEDIA',
    prazo: null,
    status: 'PENDENTE',
    duracaoMinutos: 60,
    createdAt: '2026-01-01T10:00:00Z',
    updatedAt: '2026-01-01T10:00:00Z',
    deletedAt: null,
    deletada: false,
    ...parcial,
  }
}

describe('listaArvore', () => {
  it('visão todas sem filtro isolador é árvore', () => {
    expect(ehVisaoArvore(FILTROS_LISTA_INICIAIS)).toBe(true)
    expect(ehVisaoArvore({ ...FILTROS_LISTA_INICIAIS, sort: 'duracao', direction: 'desc' })).toBe(
      true,
    )
  })

  it('prioridade, prazo, duração, projeto ou tag isolam o filho', () => {
    expect(temFiltroIsolador({ ...FILTROS_LISTA_INICIAIS, prioridade: ['ALTA'] })).toBe(true)
    expect(temFiltroIsolador({ ...FILTROS_LISTA_INICIAIS, prazoDe: '2026-10-28T00:00' })).toBe(true)
    expect(temFiltroIsolador({ ...FILTROS_LISTA_INICIAIS, duracaoDe: '30' })).toBe(true)
    expect(temFiltroIsolador({ ...FILTROS_LISTA_INICIAIS, projetoId: 'p-1' })).toBe(true)
    expect(temFiltroIsolador({ ...FILTROS_LISTA_INICIAIS, tagIds: ['tag-1'] })).toBe(true)
    expect(temFiltroIsolador({ ...FILTROS_LISTA_INICIAIS, semPrazo: true })).toBe(true)
    expect(temFiltroIsolador({ ...FILTROS_LISTA_INICIAIS, semProjeto: true })).toBe(true)
    expect(temFiltroIsolador({ ...FILTROS_LISTA_INICIAIS, semTag: true })).toBe(true)
    expect(ehVisaoArvore({ ...FILTROS_LISTA_INICIAIS, prioridade: ['ALTA'] })).toBe(false)
  })

  it('visão diferente de todas não é árvore; excluídas é plana', () => {
    expect(ehVisaoArvore({ ...FILTROS_LISTA_INICIAIS, visao: 'pendentes' })).toBe(false)
    expect(ehVisaoPlana({ ...FILTROS_LISTA_INICIAIS, visao: 'excluidas' })).toBe(true)
    expect(ehVisaoPlana(FILTROS_LISTA_INICIAIS)).toBe(false)
  })

  it('filho cujo pai está no resultado sai do nível da lista', () => {
    const pai = tarefa({ id: 'pai' })
    const filho = tarefa({ id: 'filho', parentId: 'pai' })
    const solto = tarefa({ id: 'solto', parentId: 'outro' })

    expect(tarefasNoNivelDaLista([pai, filho, solto]).map((item) => item.id)).toEqual([
      'pai',
      'solto',
    ])
  })

  it('filhos na página vêm só daquele pai, na ordem manual', () => {
    const segundo = tarefa({
      id: 'b',
      parentId: 'pai',
      ordem: 0,
      createdAt: '2026-01-01T12:00:00Z',
    })
    const primeiro = tarefa({
      id: 'a',
      parentId: 'pai',
      ordem: 1,
      createdAt: '2026-01-01T10:00:00Z',
    })
    const outro = tarefa({ id: 'c', parentId: 'outro' })

    expect(filhosNaPagina([primeiro, segundo, outro], 'pai').map((item) => item.id)).toEqual([
      'b',
      'a',
    ])
  })

  it('lista vazia não inventa nós', () => {
    expect(tarefasNoNivelDaLista([])).toEqual([])
    expect(filhosNaPagina([], 'pai')).toEqual([])
  })

  it('naoDeveIsolarPorPrazoAteVazioNemInventarFilhosDePaiAusente', () => {
    expect(temFiltroIsolador({ ...FILTROS_LISTA_INICIAIS, prazoAte: '2026-10-28T00:00' })).toBe(true)
    expect(temFiltroIsolador({ ...FILTROS_LISTA_INICIAIS, prazoAte: '' })).toBe(false)
    expect(ehVisaoArvore({ ...FILTROS_LISTA_INICIAIS, prazoAte: '' })).toBe(true)
    const orfao = tarefa({ id: 'filho', parentId: 'pai-sumiu' })
    expect(tarefasNoNivelDaLista([orfao]).map((item) => item.id)).toEqual(['filho'])
    expect(filhosNaPagina([orfao], 'outro-pai')).toEqual([])
  })
})
