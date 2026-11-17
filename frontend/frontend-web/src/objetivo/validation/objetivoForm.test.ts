import { describe, expect, it } from 'vitest'

import {
  OBJETIVO_FORM_VAZIO,
  primeiroErroObjetivo,
  validarObjetivoForm,
  type ObjetivoFormValues,
} from './objetivoForm'

const HOJE = '2026-11-17'

function form(parcial: Partial<ObjetivoFormValues>): ObjetivoFormValues {
  return { ...OBJETIVO_FORM_VAZIO, ...parcial }
}

describe('validarObjetivoForm', () => {
  it('aceita título com descrição e prazo futuro', () => {
    const erros = validarObjetivoForm(
      form({ titulo: 'Perder 5 kg', descricao: 'Correr 3x', prazo: '2026-12-31' }),
      { hojeIso: HOJE },
    )
    expect(erros).toEqual({})
    expect(primeiroErroObjetivo(erros)).toBeNull()
  })

  it('aceita prazo igual a hoje e prazo vazio', () => {
    expect(validarObjetivoForm(form({ titulo: 'X', prazo: HOJE }), { hojeIso: HOJE })).toEqual({})
    expect(validarObjetivoForm(form({ titulo: 'X', prazo: '' }), { hojeIso: HOJE })).toEqual({})
  })

  it('aceita prazo vencido quando é o prazo original do objetivo', () => {
    const erros = validarObjetivoForm(form({ titulo: 'X', prazo: '2026-11-30' }), {
      hojeIso: '2026-12-05',
      prazoOriginal: '2026-11-30',
    })
    expect(erros).toEqual({})
  })

  it('aceita título com exatamente 80 caracteres e espaços nas pontas', () => {
    const erros = validarObjetivoForm(form({ titulo: `  ${'a'.repeat(80)}  ` }), { hojeIso: HOJE })
    expect(erros.titulo).toBeUndefined()
  })

  it('rejeita título só de espaços', () => {
    const erros = validarObjetivoForm(form({ titulo: '   ' }), { hojeIso: HOJE })
    expect(erros.titulo).toBe('Informe o título do objetivo.')
    expect(primeiroErroObjetivo(erros)).toBe('Informe o título do objetivo.')
  })

  it('rejeita título com 81 caracteres', () => {
    const erros = validarObjetivoForm(form({ titulo: 'a'.repeat(81) }), { hojeIso: HOJE })
    expect(erros.titulo).toBe('O título deve ter no máximo 80 caracteres.')
  })

  it('rejeita descrição com 501 caracteres', () => {
    const erros = validarObjetivoForm(form({ titulo: 'X', descricao: 'd'.repeat(501) }), {
      hojeIso: HOJE,
    })
    expect(erros.descricao).toBe('A descrição deve ter no máximo 500 caracteres.')
  })

  it('rejeita prazo de ontem num objetivo novo', () => {
    const erros = validarObjetivoForm(form({ titulo: 'X', prazo: '2026-11-16' }), { hojeIso: HOJE })
    expect(erros.prazo).toBe('O prazo não pode estar no passado.')
  })

  it('rejeita trocar o prazo por outro dia no passado', () => {
    const erros = validarObjetivoForm(form({ titulo: 'X', prazo: '2026-12-01' }), {
      hojeIso: '2026-12-05',
      prazoOriginal: '2026-11-30',
    })
    expect(erros.prazo).toBe('O prazo não pode estar no passado.')
  })

  it('rejeita prazo fora do formato de data', () => {
    const erros = validarObjetivoForm(form({ titulo: 'X', prazo: '31/12/2026' }), { hojeIso: HOJE })
    expect(erros.prazo).toBe('Informe uma data válida.')
  })
})
