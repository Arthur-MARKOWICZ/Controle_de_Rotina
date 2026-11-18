import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../../auth/api/authApi'
import * as apiClient from '../../auth/session/apiClient'
import {
  arquivarObjetivo,
  atualizarObjetivo,
  buildObjetivosQuery,
  buscarObjetivo,
  concluirObjetivo,
  criarObjetivo,
  listarObjetivos,
  reabrirObjetivo,
  type ObjetivoResponse,
} from './objetivoApi'

vi.mock('../../auth/session/apiClient', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../auth/session/apiClient')>()
  return { ...actual, apiFetch: vi.fn() }
})

const objetivo: ObjetivoResponse = {
  id: 'obj-1',
  titulo: 'Perder 5 kg',
  descricao: null,
  prazo: '2026-12-31',
  status: 'ATIVO',
  concluidoEm: null,
  arquivadoEm: null,
  createdAt: '2026-11-17T12:00:00Z',
  updatedAt: '2026-11-17T12:00:00Z',
  progresso: null,
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function problem(status: number, title: string, detail?: string): Response {
  return json({ title, ...(detail ? { detail } : {}) }, status)
}

describe('objetivoApi', () => {
  beforeEach(() => {
    vi.mocked(apiClient.apiFetch).mockReset()
  })

  it('lista objetivos com status, page e size na query', async () => {
    const pagina = {
      content: [objetivo],
      page: { size: 20, number: 1, totalElements: 21, totalPages: 2 },
    }
    vi.mocked(apiClient.apiFetch).mockResolvedValue(json(pagina))

    await expect(listarObjetivos({ status: 'CONCLUIDO', page: 1 })).resolves.toEqual(pagina)
    expect(apiClient.apiFetch).toHaveBeenCalledWith('/objetivos?status=CONCLUIDO&page=1&size=20')
  })

  it('omite status da query quando não informado', () => {
    expect(buildObjetivosQuery()).toBe('/objetivos?page=0&size=20')
  })

  it('cria objetivo via POST /objetivos', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(json(objetivo, 201))

    await expect(criarObjetivo({ titulo: 'Perder 5 kg' })).resolves.toEqual(objetivo)
    expect(apiClient.apiFetch).toHaveBeenCalledWith('/objetivos', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ titulo: 'Perder 5 kg' }),
    })
  })

  it('busca objetivo via GET /objetivos/{id}', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(json(objetivo))

    await expect(buscarObjetivo('obj-1')).resolves.toEqual(objetivo)
    expect(apiClient.apiFetch).toHaveBeenCalledWith('/objetivos/obj-1')
  })

  it('envia prazo null explícito no PATCH para remover o prazo', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(json({ ...objetivo, prazo: null }))

    await atualizarObjetivo('obj-1', { titulo: 'Perder 5 kg', prazo: null })

    const [url, init] = vi.mocked(apiClient.apiFetch).mock.calls[0]
    expect(url).toBe('/objetivos/obj-1')
    expect(init?.method).toBe('PATCH')
    expect(JSON.parse(String(init?.body))).toEqual({ titulo: 'Perder 5 kg', prazo: null })
  })

  it.each([
    ['concluir', concluirObjetivo],
    ['arquivar', arquivarObjetivo],
    ['reabrir', reabrirObjetivo],
  ] as const)('%s via POST /objetivos/{id}/%s', async (acao, fn) => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(json(objetivo))

    await fn('obj-1')
    expect(apiClient.apiFetch).toHaveBeenCalledWith(`/objetivos/obj-1/${acao}`, { method: 'POST' })
  })

  it('propaga 400 com a mensagem da validação', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      problem(400, 'Prazo inválido', 'O prazo não pode estar no passado.'),
    )

    await expect(criarObjetivo({ titulo: 'X', prazo: '2020-01-01' })).rejects.toMatchObject({
      status: 400,
      message: 'O prazo não pode estar no passado.',
    })
  })

  it('propaga 401 quando o refresh falhou', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      problem(401, 'Não autorizado', 'Sessão expirada.'),
    )

    await expect(listarObjetivos()).rejects.toMatchObject({ status: 401 })
  })

  it('propaga 404 de objetivo inexistente ou de outro usuário', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      problem(404, 'Objetivo não encontrado', 'Objetivo inexistente ou sem permissão de acesso.'),
    )

    await expect(buscarObjetivo('alheio')).rejects.toMatchObject({
      status: 404,
      message: 'Objetivo inexistente ou sem permissão de acesso.',
    })
  })

  it('propaga 409 de transição inválida', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      problem(409, 'Objetivo já concluído', 'O objetivo informado já está concluído.'),
    )

    await expect(concluirObjetivo('obj-1')).rejects.toMatchObject({
      status: 409,
      message: 'O objetivo informado já está concluído.',
    })
  })

  it('usa a mensagem padrão em 500 sem corpo útil', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(json({}, 500))

    await expect(reabrirObjetivo('obj-1')).rejects.toMatchObject({
      status: 500,
      message: 'Não foi possível reabrir o objetivo.',
    })
  })

  it('propaga falha de rede/timeout do fetch', async () => {
    vi.mocked(apiClient.apiFetch).mockRejectedValue(new TypeError('Failed to fetch'))

    await expect(listarObjetivos()).rejects.toBeInstanceOf(TypeError)
  })

  it('rejeita HTML do proxy e JSON inválido', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response('<!doctype html><html><div id="root"></div></html>', {
        status: 200,
        headers: { 'Content-Type': 'text/html' },
      }),
    )
    await expect(listarObjetivos()).rejects.toBeInstanceOf(ApiError)

    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response('não-é-json', { status: 200, headers: { 'Content-Type': 'application/json' } }),
    )
    await expect(buscarObjetivo('obj-1')).rejects.toMatchObject({
      message: 'Resposta inválida da API (não é JSON).',
    })
  })
})
