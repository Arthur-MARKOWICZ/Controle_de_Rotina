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
  listarItensObjetivo,
  listarObjetivos,
  obterEvolucaoObjetivo,
  reabrirObjetivo,
  type ObjetivoEvolucao,
  type ObjetivoItens,
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

    await expect(listarObjetivos({ status: ['CONCLUIDO'], page: 1 })).resolves.toEqual(pagina)
    expect(apiClient.apiFetch).toHaveBeenCalledWith('/objetivos?status=CONCLUIDO&page=1&size=20')
  })

  it('omite status da query quando não informado', () => {
    expect(buildObjetivosQuery()).toBe('/objetivos?page=0&size=20')
  })

  it('omite status da query quando a lista vem vazia', () => {
    expect(buildObjetivosQuery({ status: [] })).toBe('/objetivos?page=0&size=20')
  })

  it('repete status na query para os encerrados', async () => {
    const pagina = {
      content: [objetivo],
      page: { size: 20, number: 0, totalElements: 1, totalPages: 1 },
    }
    vi.mocked(apiClient.apiFetch).mockResolvedValue(json(pagina))

    await listarObjetivos({ status: ['CONCLUIDO', 'ARQUIVADO'] })

    expect(apiClient.apiFetch).toHaveBeenCalledWith(
      '/objetivos?status=CONCLUIDO&status=ARQUIVADO&page=0&size=20',
    )
  })

  it('propaga o 400 de combinação de status inválida', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(
      problem(
        400,
        'Combinação de status inválida',
        'Status ATIVO não pode ser combinado com CONCLUIDO ou ARQUIVADO.',
      ),
    )

    await expect(listarObjetivos({ status: ['ATIVO', 'CONCLUIDO'] })).rejects.toMatchObject({
      status: 400,
      message: 'Status ATIVO não pode ser combinado com CONCLUIDO ou ARQUIVADO.',
    })
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

  describe('listarItensObjetivo', () => {
    const itens: ObjetivoItens = {
      tarefas: [
        {
          id: 't-1',
          titulo: 'Simulado 1',
          status: 'PENDENTE',
          origem: 'VIA_PROJETO',
          projetoId: 'p-1',
          projetoNome: 'Estudos AWS',
        },
      ],
      habitos: [{ id: 'h-1', titulo: 'Flashcards', status: 'ATIVO', origem: 'DIRETO' }],
      projetos: [{ id: 'p-1', nome: 'Estudos AWS', status: 'ATIVO', origem: 'DIRETO' }],
    }

    it('busca os itens via GET /objetivos/{id}/itens', async () => {
      vi.mocked(apiClient.apiFetch).mockResolvedValue(json(itens))

      await expect(listarItensObjetivo('obj-1')).resolves.toEqual(itens)
      expect(apiClient.apiFetch).toHaveBeenCalledWith('/objetivos/obj-1/itens')
    })

    it('aceita objetivo sem itens (três grupos vazios)', async () => {
      const vazio: ObjetivoItens = { tarefas: [], habitos: [], projetos: [] }
      vi.mocked(apiClient.apiFetch).mockResolvedValue(json(vazio))

      await expect(listarItensObjetivo('obj-1')).resolves.toEqual(vazio)
    })

    it('propaga 401 quando o refresh falhou', async () => {
      vi.mocked(apiClient.apiFetch).mockResolvedValue(
        problem(401, 'Não autorizado', 'Sessão expirada.'),
      )

      await expect(listarItensObjetivo('obj-1')).rejects.toMatchObject({ status: 401 })
    })

    it('propaga 404 de objetivo inexistente ou de outro usuário', async () => {
      vi.mocked(apiClient.apiFetch).mockResolvedValue(
        problem(404, 'Objetivo não encontrado', 'Objetivo inexistente ou sem permissão de acesso.'),
      )

      await expect(listarItensObjetivo('alheio')).rejects.toMatchObject({
        status: 404,
        message: 'Objetivo inexistente ou sem permissão de acesso.',
      })
    })

    it('usa a mensagem padrão em 500 sem corpo útil', async () => {
      vi.mocked(apiClient.apiFetch).mockResolvedValue(json({}, 500))

      await expect(listarItensObjetivo('obj-1')).rejects.toMatchObject({
        status: 500,
        message: 'Não foi possível carregar os itens vinculados.',
      })
    })

    it('propaga falha de rede/timeout do fetch', async () => {
      vi.mocked(apiClient.apiFetch).mockRejectedValue(new TypeError('Failed to fetch'))

      await expect(listarItensObjetivo('obj-1')).rejects.toBeInstanceOf(TypeError)
    })

    it('rejeita HTML do proxy e JSON inválido', async () => {
      vi.mocked(apiClient.apiFetch).mockResolvedValue(
        new Response('<!doctype html><html><div id="root"></div></html>', {
          status: 200,
          headers: { 'Content-Type': 'text/html' },
        }),
      )
      await expect(listarItensObjetivo('obj-1')).rejects.toBeInstanceOf(ApiError)

      vi.mocked(apiClient.apiFetch).mockResolvedValue(
        new Response('não-é-json', {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      )
      await expect(listarItensObjetivo('obj-1')).rejects.toMatchObject({
        message: 'Resposta inválida da API (não é JSON).',
      })
    })
  })

  describe('obterEvolucaoObjetivo', () => {
    const evolucao: ObjetivoEvolucao = {
      antesDoHistorico: 60,
      pontos: [
        { data: '2026-11-03', minutosNoDia: 30, minutosAcumulados: 90 },
        { data: '2026-11-05', minutosNoDia: 45, minutosAcumulados: 135 },
      ],
    }

    it('busca a evolução via GET /objetivos/{id}/evolucao', async () => {
      vi.mocked(apiClient.apiFetch).mockResolvedValue(json(evolucao))

      await expect(obterEvolucaoObjetivo('obj-1')).resolves.toEqual(evolucao)
      expect(apiClient.apiFetch).toHaveBeenCalledWith('/objetivos/obj-1/evolucao')
    })

    it('aceita evolução sem nada (zero antes do histórico e sem pontos)', async () => {
      const vazia: ObjetivoEvolucao = { antesDoHistorico: 0, pontos: [] }
      vi.mocked(apiClient.apiFetch).mockResolvedValue(json(vazia))

      await expect(obterEvolucaoObjetivo('obj-1')).resolves.toEqual(vazia)
    })

    it('propaga 400 com a mensagem da API', async () => {
      vi.mocked(apiClient.apiFetch).mockResolvedValue(
        problem(400, 'Parâmetro inválido', 'O id informado é inválido.'),
      )

      await expect(obterEvolucaoObjetivo('xx')).rejects.toMatchObject({
        status: 400,
        message: 'O id informado é inválido.',
      })
    })

    it('propaga 401 quando o refresh falhou', async () => {
      vi.mocked(apiClient.apiFetch).mockResolvedValue(
        problem(401, 'Não autorizado', 'Sessão expirada.'),
      )

      await expect(obterEvolucaoObjetivo('obj-1')).rejects.toMatchObject({ status: 401 })
    })

    it('propaga 404 de objetivo inexistente ou de outro usuário', async () => {
      vi.mocked(apiClient.apiFetch).mockResolvedValue(
        problem(404, 'Objetivo não encontrado', 'Objetivo inexistente ou sem permissão de acesso.'),
      )

      await expect(obterEvolucaoObjetivo('alheio')).rejects.toMatchObject({
        status: 404,
        message: 'Objetivo inexistente ou sem permissão de acesso.',
      })
    })

    it('propaga 409 com a mensagem da API', async () => {
      vi.mocked(apiClient.apiFetch).mockResolvedValue(
        problem(409, 'Conflito', 'Estado alterado em outra aba.'),
      )

      await expect(obterEvolucaoObjetivo('obj-1')).rejects.toMatchObject({
        status: 409,
        message: 'Estado alterado em outra aba.',
      })
    })

    it('usa a mensagem padrão em 500 sem corpo útil', async () => {
      vi.mocked(apiClient.apiFetch).mockResolvedValue(json({}, 500))

      await expect(obterEvolucaoObjetivo('obj-1')).rejects.toMatchObject({
        status: 500,
        message: 'Não foi possível carregar a evolução.',
      })
    })

    it('propaga falha de rede/timeout do fetch', async () => {
      vi.mocked(apiClient.apiFetch).mockRejectedValue(new TypeError('Failed to fetch'))

      await expect(obterEvolucaoObjetivo('obj-1')).rejects.toBeInstanceOf(TypeError)
    })

    it('rejeita HTML do proxy e JSON inválido', async () => {
      vi.mocked(apiClient.apiFetch).mockResolvedValue(
        new Response('<!doctype html><html><div id="root"></div></html>', {
          status: 200,
          headers: { 'Content-Type': 'text/html' },
        }),
      )
      await expect(obterEvolucaoObjetivo('obj-1')).rejects.toBeInstanceOf(ApiError)

      vi.mocked(apiClient.apiFetch).mockResolvedValue(
        new Response('não-é-json', {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      )
      await expect(obterEvolucaoObjetivo('obj-1')).rejects.toMatchObject({
        message: 'Resposta inválida da API (não é JSON).',
      })
    })
  })
})
