import { describe, expect, it } from 'vitest'

import { chipsAtivos } from './filtrosAtivos'
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
})
