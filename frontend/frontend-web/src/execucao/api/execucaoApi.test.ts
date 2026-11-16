import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiFetch } from '../../auth/session/apiClient'
import {
  abortarExecucao,
  buildExecucoesQuery,
  buscarSessaoAtiva,
  concluirExecucao,
  iniciarExecucao,
  listarExecucoes,
} from './execucaoApi'

vi.mock('../../auth/session/apiClient', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../auth/session/apiClient')>()
  return {
    ...actual,
    apiFetch: vi.fn(),
  }
})

describe('execucaoApi', () => {
  beforeEach(() => {
    vi.mocked(apiFetch).mockReset()
  })

  it('monta query com data', () => {
    expect(buildExecucoesQuery('2026-10-27')).toBe('/execucoes?data=2026-10-27')
    expect(buildExecucoesQuery()).toBe('/execucoes')
  })

  it('lista execuções do dia', async () => {
    vi.mocked(apiFetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          planoAprovado: { id: 'p-1', data: '2026-10-27', versao: 1 },
          itens: [],
        }),
        { status: 200 },
      ),
    )

    const resultado = await listarExecucoes('2026-10-27')

    expect(apiFetch).toHaveBeenCalledWith('/execucoes?data=2026-10-27')
    expect(resultado.planoAprovado?.id).toBe('p-1')
  })

  it('inicia execução com blocoId', async () => {
    vi.mocked(apiFetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          id: 'e-1',
          blocoPlanoId: 'b-1',
          status: 'EM_ANDAMENTO',
          iniciadoEm: '2026-10-27T14:00:00Z',
          concluidoEm: null,
          abortadoEm: null,
        }),
        { status: 201 },
      ),
    )

    const resultado = await iniciarExecucao('b-1')

    expect(apiFetch).toHaveBeenCalledWith('/execucoes/iniciar', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ blocoId: 'b-1' }),
    })
    expect(resultado.status).toBe('EM_ANDAMENTO')
  })

  it('conclui e aborta por id', async () => {
    vi.mocked(apiFetch)
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            id: 'e-1',
            blocoPlanoId: 'b-1',
            status: 'CONCLUIDO',
            iniciadoEm: '2026-10-27T14:00:00Z',
            concluidoEm: '2026-10-27T15:00:00Z',
            abortadoEm: null,
          }),
          { status: 200 },
        ),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            id: 'e-2',
            blocoPlanoId: 'b-2',
            status: 'ABORTADO',
            iniciadoEm: '2026-10-27T14:00:00Z',
            concluidoEm: null,
            abortadoEm: '2026-10-27T14:10:00Z',
          }),
          { status: 200 },
        ),
      )

    await expect(concluirExecucao('e-1')).resolves.toMatchObject({ status: 'CONCLUIDO' })
    await expect(abortarExecucao('e-2')).resolves.toMatchObject({ status: 'ABORTADO' })
  })

  it('propaga 400', async () => {
    vi.mocked(apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ title: 'Bad Request', detail: 'blocoId é obrigatório.' }), {
        status: 400,
      }),
    )
    await expect(iniciarExecucao('')).rejects.toMatchObject({
      status: 400,
      message: 'blocoId é obrigatório.',
    })
  })

  it('propaga 401 quando o refresh falhou', async () => {
    vi.mocked(apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ title: 'Não autorizado', detail: 'Sessão expirada.' }), {
        status: 401,
      }),
    )
    await expect(listarExecucoes('2026-10-27')).rejects.toMatchObject({ status: 401 })
  })

  it('propaga 404', async () => {
    vi.mocked(apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ title: 'Não encontrado', detail: 'Execução inexistente.' }), {
        status: 404,
      }),
    )
    await expect(concluirExecucao('e-sumiu')).rejects.toMatchObject({ status: 404 })
  })

  it('propaga 409', async () => {
    vi.mocked(apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ title: 'Conflito', detail: 'Já em andamento.' }), {
        status: 409,
      }),
    )
    await expect(iniciarExecucao('b-1')).rejects.toMatchObject({
      status: 409,
      message: 'Já em andamento.',
    })
  })

  it('propaga 500', async () => {
    vi.mocked(apiFetch).mockResolvedValue(
      new Response(JSON.stringify({ title: 'Erro interno' }), { status: 500 }),
    )
    await expect(abortarExecucao('e-1')).rejects.toMatchObject({ status: 500 })
  })

  it('rejeita HTML da SPA e JSON inválido', async () => {
    vi.mocked(apiFetch).mockResolvedValue(
      new Response('<!doctype html><html><div id="root"></div></html>', {
        status: 200,
        headers: { 'Content-Type': 'text/html' },
      }),
    )
    await expect(listarExecucoes('2026-10-27')).rejects.toThrow()

    vi.mocked(apiFetch).mockResolvedValue(
      new Response('não-é-json', { status: 200, headers: { 'Content-Type': 'application/json' } }),
    )
    await expect(listarExecucoes('2026-10-27')).rejects.toMatchObject({
      message: 'Resposta inválida da API (não é JSON).',
    })
  })

  describe('buscarSessaoAtiva', () => {
    const item = {
      bloco: { id: 'b-1', duracaoMinutos: 30, ordem: 1 },
      tarefaResumo: null,
      habitoResumo: { id: 'h-1', titulo: 'Ler' },
      execucao: {
        id: 'e-1',
        blocoPlanoId: 'b-1',
        status: 'EM_ANDAMENTO',
        iniciadoEm: '2026-10-27T11:00:00Z',
        concluidoEm: null,
        abortadoEm: null,
      },
    }

    it('devolve o item da sessão em andamento (200)', async () => {
      vi.mocked(apiFetch).mockResolvedValue(new Response(JSON.stringify(item), { status: 200 }))

      const resultado = await buscarSessaoAtiva()

      expect(apiFetch).toHaveBeenCalledWith('/execucoes/ativa')
      expect(resultado?.execucao.id).toBe('e-1')
      expect(resultado?.habitoResumo?.id).toBe('h-1')
    })

    it('devolve null quando não há sessão (204)', async () => {
      vi.mocked(apiFetch).mockResolvedValue(new Response(null, { status: 204 }))

      await expect(buscarSessaoAtiva()).resolves.toBeNull()
    })

    it('propaga 400 com a mensagem do servidor', async () => {
      vi.mocked(apiFetch).mockResolvedValue(
        new Response(JSON.stringify({ title: 'Requisição inválida', detail: 'Parâmetro inválido.' }), {
          status: 400,
        }),
      )
      await expect(buscarSessaoAtiva()).rejects.toMatchObject({
        status: 400,
        message: 'Parâmetro inválido.',
      })
    })

    it('propaga 401 quando o refresh falhou (o retry fica no apiFetch)', async () => {
      vi.mocked(apiFetch).mockResolvedValue(
        new Response(JSON.stringify({ title: 'Não autorizado', detail: 'Sessão expirada.' }), {
          status: 401,
        }),
      )
      await expect(buscarSessaoAtiva()).rejects.toMatchObject({ status: 401 })
      expect(apiFetch).toHaveBeenCalledTimes(1)
    })

    it('propaga 404 e 409', async () => {
      vi.mocked(apiFetch).mockResolvedValueOnce(
        new Response(JSON.stringify({ title: 'Não encontrado', detail: 'Rota inexistente.' }), {
          status: 404,
        }),
      )
      await expect(buscarSessaoAtiva()).rejects.toMatchObject({ status: 404 })

      vi.mocked(apiFetch).mockResolvedValueOnce(
        new Response(JSON.stringify({ title: 'Conflito', detail: 'Conflito.' }), { status: 409 }),
      )
      await expect(buscarSessaoAtiva()).rejects.toMatchObject({ status: 409 })
    })

    it('usa a mensagem padrão em 500 sem detalhe', async () => {
      vi.mocked(apiFetch).mockResolvedValue(new Response(JSON.stringify({}), { status: 500 }))
      await expect(buscarSessaoAtiva()).rejects.toMatchObject({
        status: 500,
        message: 'Não foi possível carregar a sessão ativa.',
      })
    })

    it('propaga timeout de rede', async () => {
      vi.mocked(apiFetch).mockRejectedValue(new TypeError('Failed to fetch'))
      await expect(buscarSessaoAtiva()).rejects.toThrow('Failed to fetch')
    })

    it('rejeita HTML do proxy e JSON inválido', async () => {
      vi.mocked(apiFetch).mockResolvedValueOnce(
        new Response('<!doctype html><html><div id="root"></div></html>', {
          status: 200,
          headers: { 'Content-Type': 'text/html' },
        }),
      )
      await expect(buscarSessaoAtiva()).rejects.toThrow()

      vi.mocked(apiFetch).mockResolvedValueOnce(
        new Response('não-é-json', { status: 200, headers: { 'Content-Type': 'application/json' } }),
      )
      await expect(buscarSessaoAtiva()).rejects.toMatchObject({
        message: 'Resposta inválida da API (não é JSON).',
      })
    })
  })
})
