import { describe, expect, it } from 'vitest'

import { PROJETO_FORM_VAZIO, validarProjetoForm } from './projetoForm'

describe('validarProjetoForm', () => {
  it('aceita nome válido', () => {
    expect(validarProjetoForm({ nome: 'TCC', descricao: '' })).toBeNull()
  })

  it('rejeita nome vazio', () => {
    expect(validarProjetoForm(PROJETO_FORM_VAZIO)).toBe('Informe o nome do projeto.')
  })

  it('rejeita nome só com espaços sem alterar o valor original', () => {
    const values = { nome: '   ', descricao: 'ok' }

    expect(validarProjetoForm(values)).toBe('Informe o nome do projeto.')
    expect(values.nome).toBe('   ')
  })

  it('rejeita nome acima de 80 caracteres', () => {
    expect(validarProjetoForm({ nome: 'A'.repeat(81), descricao: '' })).toBe(
      'O nome deve ter no máximo 80 caracteres.',
    )
  })

  it('aceita nome com exatamente 80 caracteres', () => {
    expect(validarProjetoForm({ nome: 'A'.repeat(80), descricao: '' })).toBeNull()
  })

  it('rejeita descrição acima de 500 caracteres', () => {
    expect(validarProjetoForm({ nome: 'TCC', descricao: 'B'.repeat(501) })).toBe(
      'A descrição deve ter no máximo 500 caracteres.',
    )
  })

  it('aceita descrição com exatamente 500 caracteres', () => {
    expect(validarProjetoForm({ nome: 'TCC', descricao: 'B'.repeat(500) })).toBeNull()
  })
})
