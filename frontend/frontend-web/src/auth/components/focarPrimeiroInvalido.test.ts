import { createRef } from 'react'
import { describe, expect, it, vi } from 'vitest'

import { focarPrimeiroInvalido, temErro } from './focarPrimeiroInvalido'

describe('focarPrimeiroInvalido', () => {
  it('foca o primeiro campo com erro', () => {
    const primeiro = createRef<HTMLInputElement>()
    const segundo = createRef<HTMLInputElement>()
    primeiro.current = { focus: vi.fn() } as unknown as HTMLInputElement
    segundo.current = { focus: vi.fn() } as unknown as HTMLInputElement

    focarPrimeiroInvalido([
      ['obrigatório', primeiro],
      ['também', segundo],
    ])

    expect(primeiro.current?.focus).toHaveBeenCalledOnce()
    expect(segundo.current?.focus).not.toHaveBeenCalled()
  })

  it('pula campos sem erro e foca o próximo inválido', () => {
    const ok = createRef<HTMLInputElement>()
    const invalido = createRef<HTMLInputElement>()
    ok.current = { focus: vi.fn() } as unknown as HTMLInputElement
    invalido.current = { focus: vi.fn() } as unknown as HTMLInputElement

    focarPrimeiroInvalido([
      [undefined, ok],
      ['inválido', invalido],
    ])

    expect(ok.current?.focus).not.toHaveBeenCalled()
    expect(invalido.current?.focus).toHaveBeenCalledOnce()
  })

  it('não foca quando todos os erros estão vazios', () => {
    const campo = createRef<HTMLInputElement>()
    campo.current = { focus: vi.fn() } as unknown as HTMLInputElement

    focarPrimeiroInvalido([
      [undefined, campo],
      ['', campo],
    ])

    expect(campo.current?.focus).not.toHaveBeenCalled()
  })

  it('não lança quando o ref ainda não tem current', () => {
    const campo = createRef<HTMLInputElement>()

    expect(() => focarPrimeiroInvalido([['erro', campo]])).not.toThrow()
  })

  it('lista vazia não lança nem foca', () => {
    expect(() => focarPrimeiroInvalido([])).not.toThrow()
  })
})

describe('temErro', () => {
  it('é verdadeiro quando algum campo tem mensagem', () => {
    expect(temErro({ nome: 'obrigatório', email: undefined })).toBe(true)
  })

  it('é falso quando todos os valores são vazios', () => {
    expect(temErro({ nome: undefined, email: '' })).toBe(false)
    expect(temErro({})).toBe(false)
  })
})
