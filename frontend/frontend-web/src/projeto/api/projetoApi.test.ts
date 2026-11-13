import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../../auth/api/authApi'
import * as apiClient from '../../auth/session/apiClient'
import {
  arquivarProjeto,
  atualizarProjeto,
  buscarProjeto,
  criarEsperaProjeto,
  criarProjeto,
  excluirEsperaProjeto,
  listarEsperasProjeto,
  listarProjetos,
  reativarProjeto,
  reordenarProjetos,
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

  it('lista esperas do projeto via GET /projetos/{id}/esperas', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(
        JSON.stringify([
          { id: 'e-1', alvoTipo: 'TAREFA', alvoId: 't-1', alvoTitulo: 'Revisar API', resolvida: false },
        ]),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    )

    const esperas = await listarEsperasProjeto('p-1')

    expect(apiClient.apiFetch).toHaveBeenCalledWith('/projetos/p-1/esperas')
    expect(esperas[0].alvoTitulo).toBe('Revisar API')
  })

  it('cria e remove espera do projeto', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          id: 'e-1',
          alvoTipo: 'TAREFA',
          alvoId: 't-1',
          alvoTitulo: 'Revisar API',
          resolvida: false,
        }),
        { status: 201, headers: { 'Content-Type': 'application/json' } },
      ),
    )

    const espera = await criarEsperaProjeto('p-1', { alvoTipo: 'TAREFA', alvoId: 't-1' })

    expect(apiClient.apiFetch).toHaveBeenCalledWith('/projetos/p-1/esperas', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ alvoTipo: 'TAREFA', alvoId: 't-1' }),
    })
    expect(espera.id).toBe('e-1')

    vi.mocked(apiClient.apiFetch).mockResolvedValue(new Response(null, { status: 204 }))
    await excluirEsperaProjeto('p-1', 'e-1')
    expect(apiClient.apiFetch).toHaveBeenCalledWith('/projetos/p-1/esperas/e-1', {
      method: 'DELETE',
    })
  })

  it('reordena projetos via PUT /projetos/ordem', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(new Response(null, { status: 204 }))

    await reordenarProjetos(['p-2', 'p-1'])

    expect(apiClient.apiFetch).toHaveBeenCalledWith('/projetos/ordem', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ids: ['p-2', 'p-1'] }),
    })
  })

  it('propaga 400', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ title: 'Bad Request', detail: 'Nome vazio.' }), { status: 400 }),
    )
    await expect(criarProjeto({ nome: '' })).rejects.toMatchObject({
      status: 400,
      message: 'Nome vazio.',
    })
  })

  it('propaga 401 quando o refresh falhou', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ title: 'Não autorizado', detail: 'Sessão expirada.' }), {
        status: 401,
      }),
    )
    await expect(listarProjetos()).rejects.toMatchObject({ status: 401 })
  })

  it('propaga 404', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ title: 'Não encontrado', detail: 'Projeto inexistente.' }), {
        status: 404,
      }),
    )
    await expect(buscarProjeto('p-sumiu')).rejects.toMatchObject({ status: 404 })
  })

  it('propaga 409', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ title: 'Conflito', detail: 'Nome duplicado.' }), { status: 409 }),
    )
    await expect(criarProjeto({ nome: 'TCC' })).rejects.toMatchObject({
      status: 409,
      message: 'Nome duplicado.',
    })
  })

  it('propaga 500', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ title: 'Erro interno' }), { status: 500 }),
    )
    await expect(listarProjetos()).rejects.toBeInstanceOf(ApiError)
  })

  it('rejeita HTML do proxy em erro e JSON inválido no 2xx', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response('<!doctype html><html><div id="root"></div></html>', {
        status: 404,
        headers: { 'Content-Type': 'text/html' },
      }),
    )
    await expect(listarProjetos()).rejects.toBeInstanceOf(ApiError)

    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      new Response('não-é-json', { status: 200, headers: { 'Content-Type': 'application/json' } }),
    )
    await expect(listarProjetos()).rejects.toThrow()
  })
})
