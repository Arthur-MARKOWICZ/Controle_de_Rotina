import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as apiClient from '../../auth/session/apiClient'
import {
  arquivarProjeto,
  atualizarProjeto,
  buscarProjeto,
  criarProjeto,
  listarProjetos,
  reativarProjeto,
  type ProjetoResponse,
} from './projetoApi'

vi.mock('../../auth/session/apiClient', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../auth/session/apiClient')>()
  return {
    ...actual,
    apiFetch: vi.fn(),
  }
})

const projeto: ProjetoResponse = {
  id: 'p-1',
  nome: 'TCC',
  descricao: 'Trabalho de conclusão',
  status: 'ATIVO',
  createdAt: '2026-01-01T10:00:00Z',
  updatedAt: '2026-01-01T10:00:00Z',
}

describe('projetoApi', () => {
  beforeEach(() => {
    vi.mocked(apiClient.apiFetch).mockReset()
  })

  it('lista projetos com status, página e size via GET /projetos', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          content: [projeto],
          totalElements: 1,
          totalPages: 1,
          number: 0,
          size: 20,
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    )

    const page = await listarProjetos({ status: 'ARQUIVADO', page: 1, size: 10 })

    expect(apiClient.apiFetch).toHaveBeenCalledTimes(1)
    const [url] = vi.mocked(apiClient.apiFetch).mock.calls[0]
    expect(url).toContain('/projetos?')
    expect(url).toContain('status=ARQUIVADO')
    expect(url).toContain('page=1')
    expect(url).toContain('size=10')
    expect(page.content).toHaveLength(1)
  })

  it('cria projeto com POST /projetos', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify(projeto), {
        status: 201,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const criado = await criarProjeto({ nome: 'TCC', descricao: 'Trabalho de conclusão' })

    expect(apiClient.apiFetch).toHaveBeenCalledWith('/projetos', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ nome: 'TCC', descricao: 'Trabalho de conclusão' }),
    })
    expect(criado.id).toBe('p-1')
  })

  it('busca, atualiza, arquiva e reativa', async () => {
    const okJson = () =>
      new Response(JSON.stringify(projeto), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      })
    vi.mocked(apiClient.apiFetch).mockImplementation(async () => okJson())

    await buscarProjeto('p-1')
    expect(apiClient.apiFetch).toHaveBeenLastCalledWith('/projetos/p-1')

    await atualizarProjeto('p-1', { nome: 'TCC 2' })
    expect(apiClient.apiFetch).toHaveBeenLastCalledWith('/projetos/p-1', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ nome: 'TCC 2' }),
    })

    await arquivarProjeto('p-1')
    expect(apiClient.apiFetch).toHaveBeenLastCalledWith('/projetos/p-1/arquivar', {
      method: 'POST',
    })

    await reativarProjeto('p-1')
    expect(apiClient.apiFetch).toHaveBeenLastCalledWith('/projetos/p-1/reativar', {
      method: 'POST',
    })
  })
})
