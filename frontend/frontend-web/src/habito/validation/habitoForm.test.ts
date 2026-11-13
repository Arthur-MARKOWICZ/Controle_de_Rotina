import { describe, expect, it } from 'vitest'

import { HABITO_FORM_VAZIO, primeiroErroHabito, rotuloDiasSemana, validarHabitoForm } from './habitoForm'

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

  it('rejeita título vazio sem alterar o valor original', () => {
    const values = { ...HABITO_FORM_VAZIO, titulo: '   ', duracaoMinutos: 30, diasSemana: ['SEGUNDA' as const] }
    const erros = validarHabitoForm(values)
    expect(erros.titulo).toMatch(/título/i)
    expect(values.titulo).toBe('   ')
  })

  it('rejeita título acima de 120 caracteres', () => {
    expect(
      validarHabitoForm({
        ...HABITO_FORM_VAZIO,
        titulo: 'A'.repeat(121),
        duracaoMinutos: 30,
        diasSemana: ['SEGUNDA'],
      }).titulo,
    ).toMatch(/120/)
  })

  it('rejeita descrição acima de 2000 e horário inválido', () => {
    const erros = validarHabitoForm({
      titulo: 'Correr',
      descricao: 'x'.repeat(2001),
      duracaoMinutos: 30,
      diasSemana: ['SEGUNDA'],
      horarioPreferencial: '7:30',
    })
    expect(erros.descricao).toMatch(/2000/)
    expect(erros.horarioPreferencial).toMatch(/horário válido/i)
  })

  it('primeiroErroHabito devolve o primeiro campo e null quando vazio', () => {
    expect(primeiroErroHabito({ titulo: 'Informe o título.' })).toBe('Informe o título.')
    expect(primeiroErroHabito({})).toBeNull()
  })

  it('rotuloDiasSemana ordena e ignora lista vazia', () => {
    expect(rotuloDiasSemana([])).toBe('')
    expect(rotuloDiasSemana(['DOMINGO', 'SEGUNDA'])).toBe('Seg, Dom')
  })
})
