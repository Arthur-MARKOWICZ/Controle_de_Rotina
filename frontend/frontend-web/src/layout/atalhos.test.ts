import { describe, expect, it } from 'vitest'

import { focoEmCampoEditavel, rotaPorAtalhoGo } from './atalhos'

describe('atalhos helpers', () => {
  it('mapeia G+letra para rotas principais', () => {
    expect(rotaPorAtalhoGo('h')).toBe('/plano')
    expect(rotaPorAtalhoGo('T')).toBe('/tarefas')
    expect(rotaPorAtalhoGo('p')).toBe('/projetos')
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

  it('detecta textarea, select e contentEditable', () => {
    const textarea = document.createElement('textarea')
    const select = document.createElement('select')
    const editavel = document.createElement('div')
    Object.defineProperty(editavel, 'isContentEditable', { get: () => true })
    expect(focoEmCampoEditavel(textarea)).toBe(true)
    expect(focoEmCampoEditavel(select)).toBe(true)
    expect(focoEmCampoEditavel(editavel)).toBe(true)
  })

  it('target nulo ou não-elemento não é campo editável', () => {
    expect(focoEmCampoEditavel(null)).toBe(false)
    expect(focoEmCampoEditavel(document.createTextNode('x'))).toBe(false)
  })

  it('letra desconhecida não muda o mapa de rotas', () => {
    expect(rotaPorAtalhoGo('z')).toBeNull()
    expect(rotaPorAtalhoGo('h')).toBe('/plano')
  })

  it('aceita maiúsculas e minúsculas para o mesmo destino', () => {
    expect(rotaPorAtalhoGo('I')).toBe('/historico')
    expect(rotaPorAtalhoGo('i')).toBe('/historico')
  })

  it('string vazia não navega', () => {
    expect(rotaPorAtalhoGo('')).toBeNull()
  })
})
