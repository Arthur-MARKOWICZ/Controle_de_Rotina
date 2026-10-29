import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiFetch } from '../../auth/session/apiClient'
import {
  abortarExecucao,
  buildExecucoesQuery,
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
})
