import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../../auth/api/authApi'
import * as apiClient from '../../auth/session/apiClient'
import { buildCalendarioUrl, consultarCalendario } from './calendarioApi'

vi.mock('../../auth/session/apiClient', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../auth/session/apiClient')>()
  return {
    ...actual,
    apiFetch: vi.fn(),
  }
})

function jsonRes(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function problem(status: number, title: string, detail: string) {
  return new Response(JSON.stringify({ title, detail, status }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })
}

const payload = {
  periodo: { inicio: '2026-10-26', fim: '2026-11-01' },
  dias: [],
}

describe('calendarioApi', () => {
  beforeEach(() => {
    vi.mocked(apiClient.apiFetch).mockReset()
  })

  it('monta URL com dataDe e dataAte', () => {
    expect(buildCalendarioUrl('2026-10-26', '2026-11-01')).toBe(
      '/calendario?dataDe=2026-10-26&dataAte=2026-11-01',
    )
  })

  it('consulta calendário no intervalo (2xx)', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(jsonRes(payload))

    await expect(consultarCalendario('2026-10-26', '2026-11-01')).resolves.toEqual(payload)
    expect(apiClient.apiFetch).toHaveBeenCalledWith(
      '/calendario?dataDe=2026-10-26&dataAte=2026-11-01',
    )
  })

  it('propaga 400', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(problem(400, 'Bad Request', 'período inválido'))
    await expect(consultarCalendario('2026-11-01', '2026-10-26')).rejects.toMatchObject({
      status: 400,
      message: 'período inválido',
    })
  })

  it('propaga 401 quando o refresh falhou', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(problem(401, 'Não autorizado', 'Sessão expirada.'))
    await expect(consultarCalendario('2026-10-26', '2026-11-01')).rejects.toMatchObject({
      status: 401,
    })
  })

  it('propaga 404', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(problem(404, 'Não encontrado', 'Sem dados.'))
    await expect(consultarCalendario('2026-10-26', '2026-11-01')).rejects.toMatchObject({
      status: 404,
    })
  })

  it('propaga 409', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(problem(409, 'Conflito', 'Limite de período.'))
    await expect(consultarCalendario('2026-10-26', '2026-11-01')).rejects.toMatchObject({
      status: 409,
      message: 'Limite de período.',
    })
  })

  it('propaga 500', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ title: 'Erro interno' }), { status: 500 }),
    )
    await expect(consultarCalendario('2026-10-26', '2026-11-01')).rejects.toBeInstanceOf(ApiError)
  })

  it('rejeita HTML da SPA e JSON inválido', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response('<!doctype html><html><div id="root"></div></html>', {
        status: 200,
        headers: { 'Content-Type': 'text/html' },
      }),
    )
    await expect(consultarCalendario('2026-10-26', '2026-11-01')).rejects.toBeInstanceOf(ApiError)

    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response('não-é-json', { status: 200, headers: { 'Content-Type': 'application/json' } }),
    )
    await expect(consultarCalendario('2026-10-26', '2026-11-01')).rejects.toMatchObject({
      message: 'Resposta inválida da API (não é JSON).',
    })
  })
})
