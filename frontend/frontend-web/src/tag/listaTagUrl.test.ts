import { describe, expect, it } from 'vitest'

import { escreverTagIdsNaUrl, lerTagIdsDaUrl } from './listaTagUrl'

describe('listaTagUrl', () => {
  it('round-trip de tagId repetível', () => {
    const params = escreverTagIdsNaUrl(new URLSearchParams('page=1'), ['a', 'b'])
    expect(params.getAll('tagId')).toEqual(['a', 'b'])
    expect(lerTagIdsDaUrl(params)).toEqual(['a', 'b'])
  })
})
