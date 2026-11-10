import { describe, expect, it } from 'vitest'

import { calcularTagMatchScore, ordenarPorTagMatch } from './tagMatch'

describe('tagMatch', () => {
  const filtro = ['t1', 't2']

  it('score E=2 e OU=1 com duas tags no filtro', () => {
    expect(
      calcularTagMatchScore(
        [
          { id: 't1', nome: 'A', cor: '#000' },
          { id: 't2', nome: 'B', cor: '#000' },
        ],
        filtro,
      ),
    ).toBe(2)
    expect(calcularTagMatchScore([{ id: 't1', nome: 'A', cor: '#000' }], filtro)).toBe(1)
  })

  it('prefere score da API quando presente', () => {
    expect(calcularTagMatchScore([], filtro, 2)).toBe(2)
  })

  it('ordenarPorTagMatch coloca E antes de OU', () => {
    const itens = [
      { id: 'a', tags: [{ id: 't1', nome: 'A', cor: '#000' }], tagMatchScore: 1 },
      { id: 'b', tags: [{ id: 't1', nome: 'A', cor: '#000' }, { id: 't2', nome: 'B', cor: '#000' }], tagMatchScore: 2 },
    ]
    expect(ordenarPorTagMatch(itens, filtro).map((i) => i.id)).toEqual(['b', 'a'])
  })
})
