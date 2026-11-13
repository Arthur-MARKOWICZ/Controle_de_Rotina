import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../../auth/api/authApi'
import * as apiClient from '../../auth/session/apiClient'
import {
  consultarCapacidadeResolvida,
  type CapacidadeResolvidaResponse,
} from './capacidadeResolvidaApi'

vi.mock('../../auth/session/apiClient', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../auth/session/apiClient')>()
  return {
    ...actual,
    apiFetch: vi.fn(),
  }
})

const dia: CapacidadeResolvidaResponse = {
  data: '2026-10-08',
  horas: 1,
  origem: 'OVERRIDE',
}

describe('capacidadeResolvidaApi', () => {
  beforeEach(() => {
    vi.mocked(apiClient.apiFetch).mockReset()
  })

  it('consulta capacidade resolvida via GET /capacidades/resolvida', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify([dia]), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const lista = await consultarCapacidadeResolvida({
      dataDe: '2026-10-06',
      dataAte: '2026-10-12',
    })

    const [url] = vi.mocked(apiClient.apiFetch).mock.calls[0]
    expect(url).toContain('/capacidades/resolvida?')
    expect(url).toContain('dataDe=2026-10-06')
    expect(url).toContain('dataAte=2026-10-12')
    expect(lista).toHaveLength(1)
    expect(lista[0].origem).toBe('OVERRIDE')
  })

  it('propaga ApiError com detalhe da API', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          title: 'Bad Request',
          detail: 'dataDe e dataAte são obrigatórios.',
        }),
        { status: 400, headers: { 'Content-Type': 'application/json' } },
      ),
    )

    try {
      await consultarCapacidadeResolvida({ dataDe: '2026-10-06', dataAte: '2026-10-12' })
      expect.fail('deveria ter lançado ApiError')
    } catch (error) {
      expect(error).toBeInstanceOf(ApiError)
      expect(error).toMatchObject({
        status: 400,
        message: 'dataDe e dataAte são obrigatórios.',
      })
    }
  })

  it('propaga 401 quando o refresh da sessão falhou', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ title: 'Não autorizado', detail: 'Sessão expirada.' }), {
        status: 401,
        headers: { 'Content-Type': 'application/problem+json' },
      }),
    )

    await expect(
      consultarCapacidadeResolvida({ dataDe: '2026-10-06', dataAte: '2026-10-12' }),
    ).rejects.toMatchObject({ status: 401, message: 'Sessão expirada.' })
  })

  it('propaga 404', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ title: 'Não encontrado', detail: 'Usuário sem capacidade.' }), {
        status: 404,
        headers: { 'Content-Type': 'application/problem+json' },
      }),
    )

    await expect(
      consultarCapacidadeResolvida({ dataDe: '2026-10-06', dataAte: '2026-10-12' }),
    ).rejects.toMatchObject({ status: 404 })
  })

  it('propaga 409 de conflito de período', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ title: 'Conflito', detail: 'Intervalo inválido.' }), {
        status: 409,
        headers: { 'Content-Type': 'application/problem+json' },
      }),
    )

    await expect(
      consultarCapacidadeResolvida({ dataDe: '2026-10-12', dataAte: '2026-10-06' }),
    ).rejects.toMatchObject({ status: 409, message: 'Intervalo inválido.' })
  })

  it('propaga 500', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ title: 'Erro interno' }), {
        status: 500,
        headers: { 'Content-Type': 'application/problem+json' },
      }),
    )

    await expect(
      consultarCapacidadeResolvida({ dataDe: '2026-10-06', dataAte: '2026-10-12' }),
    ).rejects.toMatchObject({ status: 500, message: 'Erro interno' })
  })

  it('rejeita HTML da SPA no lugar de JSON', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response('<!doctype html><html><body><div id="root"></div></body></html>', {
        status: 200,
        headers: { 'Content-Type': 'text/html' },
      }),
    )

    await expect(
      consultarCapacidadeResolvida({ dataDe: '2026-10-06', dataAte: '2026-10-12' }),
    ).rejects.toBeInstanceOf(ApiError)
  })

  it('rejeita JSON inválido', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response('não-é-json', { status: 200, headers: { 'Content-Type': 'application/json' } }),
    )

    await expect(
      consultarCapacidadeResolvida({ dataDe: '2026-10-06', dataAte: '2026-10-12' }),
    ).rejects.toMatchObject({ message: 'Resposta inválida da API (não é JSON).' })
  })
})
