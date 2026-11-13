import { describe, expect, it } from 'vitest'

import { escreverTagIdsNaUrl, lerTagIdsDaUrl } from './listaTagUrl'

describe('listaTagUrl', () => {
  it('round-trip de tagId repetível', () => {
    const params = escreverTagIdsNaUrl(new URLSearchParams('page=1'), ['a', 'b'])
    expect(params.getAll('tagId')).toEqual(['a', 'b'])
    expect(lerTagIdsDaUrl(params)).toEqual(['a', 'b'])
  })

  it('ignora vazios e espaços e deduplica', () => {
    const params = new URLSearchParams()
    params.append('tagId', ' a ')
    params.append('tagId', '')
    params.append('tagId', 'a')
    params.append('tagId', '  ')
    expect(lerTagIdsDaUrl(params)).toEqual(['a'])
  })

  it('escrever com lista vazia remove tagId e preserva outros params', () => {
    const origem = new URLSearchParams('page=2&tagId=x')
    const params = escreverTagIdsNaUrl(origem, [])
    expect(params.getAll('tagId')).toEqual([])
    expect(params.get('page')).toBe('2')
    expect(origem.getAll('tagId')).toEqual(['x'])
  })

  it('não muta o URLSearchParams original ao escrever', () => {
    const origem = new URLSearchParams('q=1')
    escreverTagIdsNaUrl(origem, ['z'])
    expect(origem.getAll('tagId')).toEqual([])
  })

  it('lista vazia na leitura não lança', () => {
    expect(lerTagIdsDaUrl(new URLSearchParams())).toEqual([])
  })

  it('preserva a ordem de ids distintos', () => {
    expect(lerTagIdsDaUrl(escreverTagIdsNaUrl(new URLSearchParams(), ['c', 'a']))).toEqual(['c', 'a'])
  })

  it('regrava substituindo ids anteriores', () => {
    const params = escreverTagIdsNaUrl(new URLSearchParams('tagId=old'), ['novo'])
    expect(params.getAll('tagId')).toEqual(['novo'])
  })
})
