import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../../auth/api/authApi'
import * as apiClient from '../../auth/session/apiClient'
import {
  buildHistoricoDiaUrl,
  buildHistoricoSemanaUrl,
  consultarHistoricoDia,
  consultarHistoricoSemana,
} from './historicoApi'

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

const resumoDia = {
  periodo: { escopo: 'DIA' as const, inicio: '2026-10-27', fim: '2026-10-27' },
  tarefasFinalizadas: 1,
  blocosConcluidos: 2,
  minutosTrabalhados: 90,
  xpGanho: 100,
}

describe('historicoApi', () => {
  beforeEach(() => {
    vi.mocked(apiClient.apiFetch).mockReset()
  })

  it('monta URLs com query de período', () => {
    expect(buildHistoricoDiaUrl('2026-10-27')).toBe('/historico/dia?data=2026-10-27')
    expect(buildHistoricoSemanaUrl('2026-10-26')).toBe(
      '/historico/semana?semanaInicio=2026-10-26',
    )
  })

  it('consulta histórico do dia (2xx)', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(jsonRes(resumoDia))

    await expect(consultarHistoricoDia('2026-10-27')).resolves.toEqual(resumoDia)
    expect(apiClient.apiFetch).toHaveBeenCalledWith('/historico/dia?data=2026-10-27')
  })

  it('consulta histórico da semana (2xx)', async () => {
    const resumo = {
      periodo: { escopo: 'SEMANA' as const, inicio: '2026-10-26', fim: '2026-11-01' },
      tarefasFinalizadas: 3,
      blocosConcluidos: 5,
      minutosTrabalhados: 200,
      xpGanho: 400,
    }
    vi.mocked(apiClient.apiFetch).mockResolvedValue(jsonRes(resumo))

    await expect(consultarHistoricoSemana('2026-10-26')).resolves.toEqual(resumo)
    expect(apiClient.apiFetch).toHaveBeenCalledWith(
      '/historico/semana?semanaInicio=2026-10-26',
    )
  })

  it('propaga 400', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(problem(400, 'Bad Request', 'data inválida'))
    await expect(consultarHistoricoDia('nope')).rejects.toMatchObject({
      status: 400,
      message: 'data inválida',
    })
  })

  it('propaga 401 quando o refresh falhou', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(problem(401, 'Não autorizado', 'Sessão expirada.'))
    await expect(consultarHistoricoDia('2026-10-27')).rejects.toMatchObject({ status: 401 })
  })

  it('propaga 404', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(problem(404, 'Não encontrado', 'Sem histórico.'))
    await expect(consultarHistoricoSemana('2026-10-26')).rejects.toMatchObject({ status: 404 })
  })

  it('propaga 409 e 500', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(problem(409, 'Conflito', 'Semana não começa na segunda.'))
    await expect(consultarHistoricoSemana('2026-10-27')).rejects.toMatchObject({
      status: 409,
      message: 'Semana não começa na segunda.',
    })

    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ title: 'Erro interno' }), { status: 500 }),
    )
    await expect(consultarHistoricoDia()).rejects.toBeInstanceOf(ApiError)
  })

  it('rejeita HTML da SPA e JSON inválido', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response('<!doctype html><html><div id="root"></div></html>', {
        status: 200,
        headers: { 'Content-Type': 'text/html' },
      }),
    )
    await expect(consultarHistoricoDia('2026-10-27')).rejects.toBeInstanceOf(ApiError)

    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response('não-é-json', { status: 200, headers: { 'Content-Type': 'application/json' } }),
    )
    await expect(consultarHistoricoDia('2026-10-27')).rejects.toMatchObject({
      message: 'Resposta inválida da API (não é JSON).',
    })
  })
})
