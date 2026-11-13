import { describe, expect, it } from 'vitest'

import { compararOrdem, mesmaOrdem, moverItem } from './listaOrdem'

describe('listaOrdem', () => {
  it('move o item para o índice de destino', () => {
    expect(moverItem(['a', 'b', 'c'], 2, 0)).toEqual(['c', 'a', 'b'])
    expect(moverItem(['a', 'b', 'c'], 0, 2)).toEqual(['b', 'c', 'a'])
  })

  it('ignora índices inválidos', () => {
    expect(moverItem(['a', 'b'], 0, 0)).toEqual(['a', 'b'])
    expect(moverItem(['a', 'b'], -1, 1)).toEqual(['a', 'b'])
    expect(moverItem(['a', 'b'], 0, 5)).toEqual(['a', 'b'])
  })

  it('ordena por ordem e desempata por criação', () => {
    const itens = [
      { id: 'b', ordem: 1, createdAt: '2026-01-01T12:00:00Z' },
      { id: 'a', ordem: 0, createdAt: '2026-01-01T18:00:00Z' },
      { id: 'c', ordem: 1, createdAt: '2026-01-01T10:00:00Z' },
    ]
    expect([...itens].sort(compararOrdem).map((item) => item.id)).toEqual(['a', 'c', 'b'])
  })

  it('compara duas sequências de ids', () => {
    expect(mesmaOrdem(['a', 'b'], ['a', 'b'])).toBe(true)
    expect(mesmaOrdem(['a', 'b'], ['b', 'a'])).toBe(false)
  })

  it('move de um único item devolve a mesma referência', () => {
    const itens = ['a']
    expect(moverItem(itens, 0, 0)).toBe(itens)
  })

  it('mesmaOrdem é falso com comprimentos diferentes', () => {
    expect(mesmaOrdem(['a'], ['a', 'b'])).toBe(false)
    expect(mesmaOrdem([], ['a'])).toBe(false)
  })

  it('compararOrdem trata ordem ausente como zero', () => {
    const itens = [
      { id: 'b', createdAt: '2026-01-02T00:00:00Z' },
      { id: 'a', createdAt: '2026-01-01T00:00:00Z' },
    ]
    expect([...itens].sort(compararOrdem).map((item) => item.id)).toEqual(['a', 'b'])
  })
})
