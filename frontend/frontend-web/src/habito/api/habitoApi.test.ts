import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../../auth/api/authApi'
import * as apiClient from '../../auth/session/apiClient'
import {
  atualizarHabito,
  buscarHabito,
  concluirHabitoDireto,
  criarHabito,
  excluirHabito,
  iniciarHabitoAvulso,
  listarHabitos,
  listarHabitosDoDia,
  pausarHabito,
  reativarHabito,
  restaurarHabito,
  type HabitoResponse,
} from './habitoApi'

vi.mock('../../auth/session/apiClient', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../auth/session/apiClient')>()
  return {
    ...actual,
    apiFetch: vi.fn(),
  }
})

const habito: HabitoResponse = {
  id: 'h-1',
  titulo: 'Correr',
  descricao: null,
  duracaoMinutos: 30,
  diasSemana: ['SEGUNDA', 'QUARTA'],
  horarioPreferencial: '07:00:00',
  status: 'ATIVO',
  streakAtual: 4,
  melhorStreak: 10,
  createdAt: '2026-01-01T10:00:00Z',
  updatedAt: '2026-01-01T10:00:00Z',
  deletedAt: null,
  deletada: false,
}

const pagina = {
  content: [habito],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 20,
}

function jsonRes(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function problemRes(status: number, title: string, detail: string) {
  return new Response(JSON.stringify({ title, detail, status }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })
}

describe('habitoApi', () => {
  beforeEach(() => {
    vi.mocked(apiClient.apiFetch).mockReset()
  })

  it('lista hábitos via GET /habitos (2xx)', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(jsonRes(pagina))

    const resultado = await listarHabitos({ status: 'ATIVO', page: 0, size: 20 })

    const [url] = vi.mocked(apiClient.apiFetch).mock.calls[0]
    expect(url).toContain('/habitos?')
    expect(url).toContain('status=ATIVO')
    expect(resultado.content[0].titulo).toBe('Correr')
  })

  it('propaga 400 com detail do ProblemDetail', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      problemRes(400, 'Requisição inválida', 'título é obrigatório.'),
    )

    await expect(
      criarHabito({ titulo: '', duracaoMinutos: 30, diasSemana: ['SEGUNDA'] }),
    ).rejects.toMatchObject({
      status: 400,
      message: 'título é obrigatório.',
    })
    await expect(
      criarHabito({ titulo: '', duracaoMinutos: 30, diasSemana: ['SEGUNDA'] }),
    ).rejects.toBeInstanceOf(ApiError)
  })

  it('propaga 401 quando o refresh da sessão falhou', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      problemRes(401, 'Não autorizado', 'Não autenticado. Faça login novamente.'),
    )

    await expect(listarHabitos()).rejects.toMatchObject({
      status: 401,
      message: 'Não autenticado. Faça login novamente.',
    })
  })

  it('propaga 404 ao buscar hábito inexistente', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      problemRes(404, 'Não encontrado', 'Hábito não encontrado.'),
    )

    await expect(buscarHabito('h-sumiu')).rejects.toMatchObject({
      status: 404,
      message: 'Hábito não encontrado.',
    })
  })

  it('propaga 409 quando o hábito já está pausado', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      problemRes(409, 'Conflito', 'Hábito já está pausado.'),
    )

    await expect(pausarHabito('h-1')).rejects.toMatchObject({
      status: 409,
      message: 'Hábito já está pausado.',
    })
  })

  it('propaga 500 com title quando não há detail', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ title: 'Erro interno' }), {
        status: 500,
        headers: { 'Content-Type': 'application/problem+json' },
      }),
    )

    await expect(reativarHabito('h-1')).rejects.toMatchObject({
      status: 500,
      message: 'Erro interno',
    })
  })

  it('rejeita HTML da SPA no lugar de JSON (proxy)', async () => {
    vi.mocked(apiClient.apiFetch).mockImplementation(
      async () =>
        new Response('<!doctype html><html><body><div id="root"></div></body></html>', {
          status: 200,
          headers: { 'Content-Type': 'text/html' },
        }),
    )

    await expect(listarHabitosDoDia('2026-11-11')).rejects.toMatchObject({
      status: 200,
      message: expect.stringMatching(/SPA|HTML/i),
    })
  })

  it('rejeita JSON inválido na resposta', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response('não-é-json', {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    await expect(listarHabitosDoDia()).rejects.toMatchObject({
      message: 'Resposta inválida da API (não é JSON).',
    })
  })

  it('cria, atualiza, exclui e restaura hábito', async () => {
    vi.mocked(apiClient.apiFetch)
      .mockResolvedValueOnce(jsonRes(habito, 201))
      .mockResolvedValueOnce(jsonRes({ ...habito, titulo: 'Correr 5km' }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(jsonRes(habito))

    await criarHabito({ titulo: 'Correr', duracaoMinutos: 30, diasSemana: ['SEGUNDA'] })
    await atualizarHabito('h-1', { titulo: 'Correr 5km' })
    await excluirHabito('h-1')
    await restaurarHabito('h-1')

    expect(vi.mocked(apiClient.apiFetch).mock.calls[0][0]).toBe('/habitos')
    expect(vi.mocked(apiClient.apiFetch).mock.calls[1][0]).toBe('/habitos/h-1')
    expect(vi.mocked(apiClient.apiFetch).mock.calls[2]).toEqual(['/habitos/h-1', { method: 'DELETE' }])
    expect(vi.mocked(apiClient.apiFetch).mock.calls[3][0]).toBe('/habitos/h-1/restore')
  })

  it('inicia e conclui hábito avulso com data', async () => {
    const execucao = {
      id: 'e-1',
      blocoPlanoId: 'b-1',
      status: 'EM_ANDAMENTO',
      iniciadoEm: '2026-11-11T15:00:00Z',
      concluidoEm: null,
      abortadoEm: null,
    }
    vi.mocked(apiClient.apiFetch)
      .mockResolvedValueOnce(jsonRes(execucao, 201))
      .mockResolvedValueOnce(jsonRes({ ...execucao, status: 'CONCLUIDO' }))

    await iniciarHabitoAvulso('h-1', '2026-11-11')
    await concluirHabitoDireto('h-1', '2026-11-11')

    expect(vi.mocked(apiClient.apiFetch).mock.calls[0][0]).toBe('/habitos/h-1/iniciar?data=2026-11-11')
    expect(vi.mocked(apiClient.apiFetch).mock.calls[1][0]).toBe(
      '/habitos/h-1/concluir-direto?data=2026-11-11',
    )
  })
})
