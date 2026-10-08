import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../../auth/api/authApi'
import * as apiClient from '../../auth/session/apiClient'
import {
  consultarCapacidadeResolvida,
  type CapacidadeResolvidaResponse,
} from './capacidadeResolvidaApi'

vi.mock('../../auth/session/apiClient', () => ({
  apiFetch: vi.fn(),
}))

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
})
