import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../../auth/api/authApi'
import * as apiClient from '../../auth/session/apiClient'
import {
  atualizarTarefa,
  buscarTarefa,
  criarTarefa,
  excluirTarefa,
  listarTarefas,
  restaurarTarefa,
  type TarefaResponse,
} from './tarefaApi'

vi.mock('../../auth/session/apiClient', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../auth/session/apiClient')>()
  return {
    ...actual,
    apiFetch: vi.fn(),
  }
})

const tarefa: TarefaResponse = {
  id: 't-1',
  titulo: 'Estudar',
  descricao: 'Spring',
  prioridade: 'MEDIA',
  prazo: '2030-01-01T12:00:00Z',
  status: 'PENDENTE',
  duracaoMinutos: 120,
  createdAt: '2026-01-01T10:00:00Z',
  updatedAt: '2026-01-01T10:00:00Z',
  deletedAt: null,
  deletada: false,
}

describe('tarefaApi', () => {
  beforeEach(() => {
    vi.mocked(apiClient.apiFetch).mockReset()
  })

  it('lista tarefas com filtros, página e ordenação via GET /tarefas', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          content: [tarefa],
          totalElements: 1,
          totalPages: 1,
          number: 0,
          size: 20,
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    )

    const page = await listarTarefas({
      status: ['PENDENTE', 'ATRASADA'],
      prioridade: ['ALTA'],
      prazoDe: '2030-01-01T00:00:00Z',
      page: 1,
      size: 10,
      sort: 'duracao',
      direction: 'desc',
    })

    expect(apiClient.apiFetch).toHaveBeenCalledTimes(1)
    const [url] = vi.mocked(apiClient.apiFetch).mock.calls[0]
    expect(url).toContain('/tarefas?')
    expect(url).toContain('status=PENDENTE')
    expect(url).toContain('status=ATRASADA')
    expect(url).toContain('prioridade=ALTA')
    expect(url).toContain('prazoDe=2030-01-01T00%3A00%3A00Z')
    expect(url).toContain('page=1')
    expect(url).toContain('size=10')
    expect(url).toContain('sort=duracao%2Cdesc')
    expect(page.content).toHaveLength(1)
    expect(page.totalElements).toBe(1)
  })

  it('cria tarefa com POST /tarefas', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify(tarefa), {
        status: 201,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const body = {
      titulo: 'Estudar',
      descricao: 'Spring',
      prioridade: 'MEDIA' as const,
      prazo: '2030-01-01T12:00:00Z',
      duracaoMinutos: 120,
    }
    const criada = await criarTarefa(body)

    expect(apiClient.apiFetch).toHaveBeenCalledWith('/tarefas', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
    expect(criada.id).toBe('t-1')
  })

  it('busca tarefa por id', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify(tarefa), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const encontrada = await buscarTarefa('t-1')
    expect(apiClient.apiFetch).toHaveBeenCalledWith('/tarefas/t-1')
    expect(encontrada.titulo).toBe('Estudar')
  })

  it('atualiza tarefa com PATCH', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ ...tarefa, titulo: 'Novo' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const atualizada = await atualizarTarefa('t-1', { titulo: 'Novo' })
    expect(apiClient.apiFetch).toHaveBeenCalledWith('/tarefas/t-1', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ titulo: 'Novo' }),
    })
    expect(atualizada.titulo).toBe('Novo')
  })

  it('propaga ApiError com detalhe da API', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          title: 'Prazo inválido',
          detail: 'O prazo não pode estar no passado.',
        }),
        { status: 400, headers: { 'Content-Type': 'application/json' } },
      ),
    )

    try {
      await criarTarefa({
        titulo: 'X',
        descricao: 'Y',
        prioridade: 'BAIXA',
        prazo: '2000-01-01T00:00:00Z',
        duracaoMinutos: 60,
      })
      expect.fail('deveria ter lançado ApiError')
    } catch (error) {
      expect(error).toBeInstanceOf(ApiError)
      expect(error).toMatchObject({
        status: 400,
        message: 'O prazo não pode estar no passado.',
      })
    }
  })

  it('lista com filtro projetoId', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          content: [],
          totalElements: 0,
          totalPages: 0,
          number: 0,
          size: 20,
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    )

    await listarTarefas({ projetoId: 'proj-1', page: 0, size: 20 })

    const [url] = vi.mocked(apiClient.apiFetch).mock.calls[0]
    expect(url).toContain('projetoId=proj-1')
  })

  it('lista com filtros de deletadas', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          content: [],
          totalElements: 0,
          totalPages: 0,
          number: 0,
          size: 20,
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    )

    await listarTarefas({ incluirDeletadas: true, somenteDeletadas: false })

    const [url] = vi.mocked(apiClient.apiFetch).mock.calls[0]
    expect(url).toContain('incluirDeletadas=true')
    expect(url).toContain('somenteDeletadas=false')
  })

  it('exclui tarefa com DELETE /tarefas/{id}', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(new Response(null, { status: 204 }))

    await excluirTarefa('t-1')

    expect(apiClient.apiFetch).toHaveBeenCalledWith('/tarefas/t-1', { method: 'DELETE' })
  })

  it('restaura tarefa com POST /tarefas/{id}/restore', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify(tarefa), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const restaurada = await restaurarTarefa('t-1')

    expect(apiClient.apiFetch).toHaveBeenCalledWith('/tarefas/t-1/restore', { method: 'POST' })
    expect(restaurada.deletada).toBe(false)
  })

  it('propaga erro de status ATRASADA com prazo futuro no PATCH', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          title: 'Status inválido',
          detail: 'Não é possível marcar como ATRASADA quando o prazo ainda está no futuro.',
        }),
        { status: 400, headers: { 'Content-Type': 'application/json' } },
      ),
    )

    try {
      await atualizarTarefa('t-1', { status: 'ATRASADA' })
      expect.fail('deveria ter lançado ApiError')
    } catch (error) {
      expect(error).toBeInstanceOf(ApiError)
      expect(error).toMatchObject({
        status: 400,
        message: 'Não é possível marcar como ATRASADA quando o prazo ainda está no futuro.',
      })
    }
  })
})
