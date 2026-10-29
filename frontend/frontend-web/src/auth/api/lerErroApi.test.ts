import { describe, expect, it } from 'vitest'

import { ApiError } from './authApi'
import { lerErroApi } from './lerErroApi'

describe('lerErroApi', () => {
  it('usa detail do ProblemDetail', async () => {
    const response = new Response(
      JSON.stringify({
        title: 'Dados inválidos',
        detail: 'Um ou mais campos estão inválidos.',
        status: 400,
      }),
      { status: 400, headers: { 'Content-Type': 'application/problem+json' } },
    )

    const erro = await lerErroApi(response, 'fallback')

    expect(erro).toBeInstanceOf(ApiError)
    expect(erro.status).toBe(400)
    expect(erro.message).toBe('Um ou mais campos estão inválidos.')
  })

  it('usa title quando detail ausente', async () => {
    const response = new Response(
      JSON.stringify({ title: 'Não autorizado', status: 401 }),
      { status: 401, headers: { 'Content-Type': 'application/problem+json' } },
    )

    const erro = await lerErroApi(response, 'fallback')

    expect(erro.message).toBe('Não autorizado')
  })

  it('usa mensagens de errors quando detail e title ausentes', async () => {
    const response = new Response(
      JSON.stringify({
        status: 400,
        errors: [{ property: 'itens[0].horas', message: 'must be greater than or equal to 0' }],
      }),
      { status: 400, headers: { 'Content-Type': 'application/json' } },
    )

    const erro = await lerErroApi(response, 'fallback')

    expect(erro.message).toBe('must be greater than or equal to 0')
  })

  it('cai no fallback quando corpo não ajuda', async () => {
    const response = new Response(JSON.stringify({}), {
      status: 500,
      headers: { 'Content-Type': 'application/json' },
    })

    const erro = await lerErroApi(response, 'Falha genérica')

    expect(erro.message).toBe('Falha genérica')
  })
})
