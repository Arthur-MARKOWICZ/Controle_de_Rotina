import { describe, expect, it } from 'vitest'

import { focoEmCampoEditavel, rotaPorAtalhoGo } from './atalhos'

describe('atalhos helpers', () => {
  it('mapeia G+letra para rotas principais', () => {
    expect(rotaPorAtalhoGo('h')).toBe('/')
    expect(rotaPorAtalhoGo('T')).toBe('/tarefas')
    expect(rotaPorAtalhoGo('c')).toBe('/capacidade')
    expect(rotaPorAtalhoGo('i')).toBe('/historico')
    expect(rotaPorAtalhoGo('x')).toBeNull()
  })

  it('detecta foco em campos editáveis', () => {
    const input = document.createElement('input')
    const div = document.createElement('div')
    expect(focoEmCampoEditavel(input)).toBe(true)
    expect(focoEmCampoEditavel(div)).toBe(false)
  })
})
