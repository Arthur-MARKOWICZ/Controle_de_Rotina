import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../../auth/api/authApi'
import * as apiClient from '../../auth/session/apiClient'
import {
  adicionarBloco,
  aprovarPlano,
  buscarPlano,
  gerarPlano,
  removerBloco,
  type PlanoResponse,
} from './planoApi'

vi.mock('../../auth/session/apiClient', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../auth/session/apiClient')>()
  return {
    ...actual,
    apiFetch: vi.fn(),
  }
})

const plano: PlanoResponse = {
  id: 'p-1',
  data: '2026-10-20',
  versao: 1,
  status: 'RASCUNHO',
  temConflito: false,
  mensagem: 'Sem tempo neste dia. Nada planejado.',
  geradoEm: '2026-10-20T09:00:00Z',
  createdAt: '2026-10-20T09:00:00Z',
  updatedAt: '2026-10-20T09:00:00Z',
  blocos: [],
}

describe('planoApi', () => {
  beforeEach(() => {
    vi.mocked(apiClient.apiFetch).mockReset()
  })

  it('busca plano do dia via GET /planos?data=', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify(plano), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const resultado = await buscarPlano({ data: '2026-10-20' })

    const [url] = vi.mocked(apiClient.apiFetch).mock.calls[0]
    expect(url).toContain('/planos?')
    expect(url).toContain('data=2026-10-20')
    expect(resultado).toEqual(plano)
  })

  it('inclui versao opcional na query', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify(plano), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    await buscarPlano({ data: '2026-10-20', versao: 2 })

    const [url] = vi.mocked(apiClient.apiFetch).mock.calls[0]
    expect(url).toContain('versao=2')
  })

  it('retorna null quando não há plano (404)', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(new Response(null, { status: 404 }))

    const resultado = await buscarPlano({ data: '2026-10-20' })

    expect(resultado).toBeNull()
  })

  it('propaga ApiError com detalhe da API', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          title: 'Bad Request',
          detail: 'data é obrigatória.',
        }),
        { status: 400, headers: { 'Content-Type': 'application/json' } },
      ),
    )

    try {
      await buscarPlano({ data: '2026-10-20' })
      expect.fail('deveria ter lançado ApiError')
    } catch (error) {
      expect(error).toBeInstanceOf(ApiError)
      expect(error).toMatchObject({
        status: 400,
        message: 'data é obrigatória.',
      })
    }
  })

  it('gera plano via POST /planos/gerar?data=', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify(plano), {
        status: 201,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const resultado = await gerarPlano('2026-10-20')

    expect(apiClient.apiFetch).toHaveBeenCalledWith('/planos/gerar?data=2026-10-20', {
      method: 'POST',
    })
    expect(resultado).toEqual(plano)
  })

  it('aprova plano via POST /planos/{id}/aprovar', async () => {
    const aprovado = { ...plano, status: 'APROVADO' as const }
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify(aprovado), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const resultado = await aprovarPlano('p-1')

    expect(apiClient.apiFetch).toHaveBeenCalledWith('/planos/p-1/aprovar', {
      method: 'POST',
    })
    expect(resultado.status).toBe('APROVADO')
  })

  it('adiciona bloco via POST /planos/{id}/blocos', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify(plano), {
        status: 201,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    await adicionarBloco('p-1', 't-2')

    expect(apiClient.apiFetch).toHaveBeenCalledWith('/planos/p-1/blocos', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ tarefaId: 't-2' }),
    })
  })

  it('remove bloco via DELETE /planos/{id}/blocos/{blocoId}', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(new Response(null, { status: 204 }))

    await removerBloco('p-1', 'b-1')

    expect(apiClient.apiFetch).toHaveBeenCalledWith('/planos/p-1/blocos/b-1', {
      method: 'DELETE',
    })
  })
})
