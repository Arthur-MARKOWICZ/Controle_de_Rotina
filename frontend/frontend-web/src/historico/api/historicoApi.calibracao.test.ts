import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../../auth/api/authApi'
import { clearAccessToken, getAccessToken, setAccessToken } from '../../auth/session/tokenStore'
import { consultarCalibracao, type CalibracaoResponse } from './historicoApi'

const FALLBACK = 'Não foi possível carregar sua calibração.'

const CALIBRACAO: CalibracaoResponse = {
  janela: { inicio: '2026-08-16', fim: '2026-11-13' },
  minimoAmostras: 5,
  geral: { amostras: 6, suficiente: true, razao: 1.25, desvioMedioMinutos: 15 },
  porTag: [],
  porProjeto: [],
}

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function problem(status: number, title: string, detail?: string): Response {
  return new Response(JSON.stringify({ status, title, detail }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })
}

async function erroDe(promise: Promise<unknown>): Promise<ApiError> {
  try {
    await promise
  } catch (e) {
    expect(e).toBeInstanceOf(ApiError)
    return e as ApiError
  }
  throw new Error('esperava erro')
}

describe('consultarCalibracao', () => {
  const fetchMock = vi.fn<typeof fetch>()

  beforeEach(() => {
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
    setAccessToken('token-antigo')
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    clearAccessToken()
  })

  it('devolve a calibração em 200 chamando o endpoint com o token', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, CALIBRACAO))

    await expect(consultarCalibracao()).resolves.toEqual(CALIBRACAO)
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/historico/calibracao')
    expect(new Headers(init?.headers).get('Authorization')).toBe('Bearer token-antigo')
  })

  it('aceita geral sem dados com razão nula', async () => {
    const semDados = {
      ...CALIBRACAO,
      geral: { amostras: 2, suficiente: false, razao: null, desvioMedioMinutos: null },
    }
    fetchMock.mockResolvedValueOnce(jsonResponse(200, semDados))

    const r = await consultarCalibracao()

    expect(r.geral.razao).toBeNull()
    expect(r.geral.suficiente).toBe(false)
  })

  it('renova o token no 401 e repete a chamada', async () => {
    fetchMock
      .mockResolvedValueOnce(problem(401, 'Não autorizado', 'Não autenticado.'))
      .mockResolvedValueOnce(jsonResponse(200, { accessToken: 'token-novo' }))
      .mockResolvedValueOnce(jsonResponse(200, CALIBRACAO))

    await expect(consultarCalibracao()).resolves.toEqual(CALIBRACAO)
    expect(fetchMock.mock.calls[1][0]).toBe('/auth/refresh')
    expect(getAccessToken()).toBe('token-novo')
    expect(new Headers(fetchMock.mock.calls[2][1]?.headers).get('Authorization')).toBe(
      'Bearer token-novo',
    )
  })

  it('propaga 401 quando o refresh também falha e limpa o token', async () => {
    fetchMock
      .mockResolvedValueOnce(problem(401, 'Não autorizado', 'Não autenticado.'))
      .mockResolvedValueOnce(problem(401, 'Não autorizado'))

    const erro = await erroDe(consultarCalibracao())

    expect(erro.status).toBe(401)
    expect(erro.message).toBe('Não autenticado.')
    expect(getAccessToken()).toBeNull()
  })

  it('usa o detail do ProblemDetail em 400', async () => {
    fetchMock.mockResolvedValueOnce(problem(400, 'Requisição inválida', 'Parâmetro inesperado.'))

    const erro = await erroDe(consultarCalibracao())

    expect(erro.status).toBe(400)
    expect(erro.message).toBe('Parâmetro inesperado.')
  })

  it('usa o title em 404 sem detail', async () => {
    fetchMock.mockResolvedValueOnce(problem(404, 'Recurso não encontrado'))

    const erro = await erroDe(consultarCalibracao())

    expect(erro.status).toBe(404)
    expect(erro.message).toBe('Recurso não encontrado')
  })

  it('usa o title em 409', async () => {
    fetchMock.mockResolvedValueOnce(problem(409, 'Conflito'))

    const erro = await erroDe(consultarCalibracao())

    expect(erro.status).toBe(409)
    expect(erro.message).toBe('Conflito')
  })

  it('cai no fallback em 500 sem title nem detail', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(500, {}))

    const erro = await erroDe(consultarCalibracao())

    expect(erro.status).toBe(500)
    expect(erro.message).toBe(FALLBACK)
  })

  it('informa resposta vazia em 500 sem corpo', async () => {
    fetchMock.mockResolvedValueOnce(new Response('', { status: 500 }))

    const erro = await erroDe(consultarCalibracao())

    expect(erro.status).toBe(500)
    expect(erro.message).toBe('Resposta vazia da API.')
  })

  it('propaga falha de rede/timeout do fetch', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'))

    await expect(consultarCalibracao()).rejects.toThrow('Failed to fetch')
  })

  it('explica quando o proxy devolve o index.html da SPA', async () => {
    fetchMock.mockResolvedValueOnce(
      new Response('<!doctype html><html><div id="root"></div></html>', { status: 200 }),
    )

    const erro = await erroDe(consultarCalibracao())

    expect(erro.message).toContain('Resposta HTML da SPA em vez de JSON')
  })

  it('rejeita JSON inválido', async () => {
    fetchMock.mockResolvedValueOnce(new Response('{"geral":', { status: 200 }))

    const erro = await erroDe(consultarCalibracao())

    expect(erro.message).toBe('Resposta inválida da API (não é JSON).')
  })
})
