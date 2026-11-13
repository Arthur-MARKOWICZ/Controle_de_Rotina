import { describe, expect, it } from 'vitest'

import { chipsAtivos, filtrosLimpos } from './filtrosAtivos'
import { FILTROS_LISTA_INICIAIS } from './listaUrl'

describe('chipsAtivos', () => {
  it('mostra nomes das tags no chip, não UUID truncado', () => {
    const chips = chipsAtivos(
      {
        ...FILTROS_LISTA_INICIAIS,
        tagIds: ['90063d2d-b69b-4c63-bcb7-4b19e4f8a191', '04c0b441-338b-4e0a-a28e-3b85cf34399a'],
      },
      [],
      [
        { id: '90063d2d-b69b-4c63-bcb7-4b19e4f8a191', nome: 'teqwaqw', cor: '#aaa' },
        { id: '04c0b441-338b-4e0a-a28e-3b85cf34399a', nome: 'test', cor: '#bbb' },
      ],
    )

    expect(chips).toHaveLength(1)
    expect(chips[0].texto).toBe('Tags: teqwaqw, test')
    expect(chips[0].texto).not.toMatch(/90063d2d|04c0b441/)
  })

  it('mostra chips de ausência sem prazo, projeto e tag', () => {
    const chips = chipsAtivos(
      {
        ...FILTROS_LISTA_INICIAIS,
        semPrazo: true,
        semProjeto: true,
        semTag: true,
        prazoDe: '2026-11-01T10:00',
        projetoId: 'proj-tcc',
        tagIds: ['tag-1'],
      },
      [{ id: 'proj-tcc', nome: 'TCC', descricao: null, status: 'ATIVO', createdAt: '', updatedAt: '' }],
      [{ id: 'tag-1', nome: 'backend', cor: '#aaa' }],
    )

    expect(chips.map((chip) => chip.texto)).toEqual(['Sem prazo', 'Sem projeto', 'Sem tag'])
  })

  it('não gera chips quando os filtros estão no default', () => {
    expect(chipsAtivos(FILTROS_LISTA_INICIAIS, [], [])).toEqual([])
  })

  it('mostra prioridade e intervalo de prazo', () => {
    const chips = chipsAtivos(
      { ...FILTROS_LISTA_INICIAIS, prioridade: ['ALTA'], prazoDe: '2026-11-01T10:00', prazoAte: '2026-11-02T10:00' },
      [],
    )
    expect(chips.map((chip) => chip.texto)).toEqual([
      expect.stringMatching(/^Prioridade: Alta$/),
      expect.stringMatching(/^Prazo:/),
    ])
  })

  it('usa fallback de projeto e tag desconhecidos sem alterar os ids originais', () => {
    const filtros = { ...FILTROS_LISTA_INICIAIS, projetoId: 'p-x', tagIds: ['abcdef12zzzz'] }
    const chips = chipsAtivos(filtros, [], [])
    expect(chips.find((c) => c.id === 'projeto')?.texto).toBe('Projeto: Projeto')
    expect(chips.find((c) => c.id === 'tags')?.texto).toBe('Tags: abcdef12')
    expect(filtros.tagIds).toEqual(['abcdef12zzzz'])
  })

  it('mostra só duração a partir de quando ate está vazio', () => {
    const chips = chipsAtivos({ ...FILTROS_LISTA_INICIAIS, duracaoDe: '30' }, [], [])
    expect(chips[0].texto).toBe('Duração: a partir de 30 min')
  })

  it('filtrosLimpos preserva visão e size e zera o resto', () => {
    const limpos = filtrosLimpos({
      ...FILTROS_LISTA_INICIAIS,
      visao: 'pendentes',
      size: 50,
      prioridade: ['ALTA'],
      tagIds: ['t1'],
    })
    expect(limpos.visao).toBe('pendentes')
    expect(limpos.size).toBe(50)
    expect(limpos.prioridade).toEqual([])
    expect(limpos.tagIds).toEqual([])
  })
})
