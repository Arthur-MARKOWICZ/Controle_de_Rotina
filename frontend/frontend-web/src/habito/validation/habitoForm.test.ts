import { describe, expect, it } from 'vitest'

import { HABITO_FORM_VAZIO, validarHabitoForm } from './habitoForm'

describe('validarHabitoForm (T2)', () => {
  it('exige pelo menos um dia da semana', () => {
    const erros = validarHabitoForm({
      ...HABITO_FORM_VAZIO,
      titulo: 'Correr',
      duracaoMinutos: 30,
      diasSemana: [],
    })
    expect(erros.diasSemana).toMatch(/pelo menos um dia/i)
  })

  it('exige duração múltipla de 15 e ≥ 15', () => {
    expect(
      validarHabitoForm({
        ...HABITO_FORM_VAZIO,
        titulo: 'Correr',
        duracaoMinutos: 20,
        diasSemana: ['SEGUNDA'],
      }).duracaoMinutos,
    ).toMatch(/múltiplo de 15/i)

    expect(
      validarHabitoForm({
        ...HABITO_FORM_VAZIO,
        titulo: 'Correr',
        duracaoMinutos: 10,
        diasSemana: ['SEGUNDA'],
      }).duracaoMinutos,
    ).toMatch(/múltiplo de 15/i)
  })

  it('aceita formulário válido', () => {
    const erros = validarHabitoForm({
      titulo: 'Meditar',
      descricao: '',
      duracaoMinutos: 15,
      diasSemana: ['SEGUNDA', 'QUARTA'],
      horarioPreferencial: '07:30',
    })
    expect(erros).toEqual({})
  })
})
