import { act, renderHook, waitFor } from '@testing-library/react'
import { type ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../auth/api/authApi'
import { ToastProvider } from '../feedback/ToastProvider'
import { XpProgressProvider } from '../xp/XpProgressContext'
import * as execucaoApi from './api/execucaoApi'
import type { ExecucaoResponse, ItemExecucaoResponse } from './api/execucaoApi'
import { SessaoAtivaProvider, useSessaoAtiva } from './SessaoAtivaContext'

vi.mock('../auth/api/meApi', () => ({
  buscarMe: vi.fn().mockResolvedValue({
    id: 'u-1',
    nome: 'Teste',
    email: 't@t.com',
    xpTotal: 0,
    nivel: 1,
    xpNoNivel: 0,
    xpParaProximoNivel: 10,
  }),
}))

vi.mock('./api/execucaoApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api/execucaoApi')>()
  return {
    ...actual,
    listarExecucoes: vi.fn(),
    concluirExecucao: vi.fn(),
    abortarExecucao: vi.fn(),
  }
})

vi.mock('../planner/data', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../planner/data')>()
  return {
    ...actual,
    dataHojeIso: () => '2026-10-20',
  }
})

const itemAndamento: ItemExecucaoResponse = {
  bloco: { id: 'b-1', duracaoMinutos: 60, ordem: 1 },
  tarefaResumo: {
    id: 't-1',
    titulo: 'Estudar',
    prioridade: 'ALTA',
    status: 'EM_PROGRESSO',
    duracaoMinutos: 60,
  },
  execucao: {
    id: 'e-1',
    blocoPlanoId: 'b-1',
    status: 'EM_ANDAMENTO',
    iniciadoEm: '2026-10-20T14:00:00Z',
    concluidoEm: null,
    abortadoEm: null,
  },
}

function wrapper({ children }: { children: ReactNode }) {
  return (
    <ToastProvider>
      <XpProgressProvider>
        <SessaoAtivaProvider>{children}</SessaoAtivaProvider>
      </XpProgressProvider>
    </ToastProvider>
  )
}

describe('SessaoAtivaContext', () => {
  beforeEach(() => {
    vi.mocked(execucaoApi.listarExecucoes).mockReset().mockResolvedValue({
      planoAprovado: null,
      itens: [],
    })
    vi.mocked(execucaoApi.concluirExecucao).mockReset()
    vi.mocked(execucaoApi.abortarExecucao).mockReset()
  })

  it('carrega sessão em andamento do dia', async () => {
    vi.mocked(execucaoApi.listarExecucoes).mockResolvedValue({
      planoAprovado: { id: 'p-1', data: '2026-10-20', versao: 1 },
      itens: [itemAndamento],
    })
    const { result } = renderHook(() => useSessaoAtiva(), { wrapper })

    await waitFor(() => expect(result.current.sessao?.execucaoId).toBe('e-1'))
    expect(result.current.sessao?.titulo).toBe('Estudar')
  })

  it('registra início quando a execução tem bloco', async () => {
    const { result } = renderHook(() => useSessaoAtiva(), { wrapper })
    await waitFor(() => expect(result.current.sessao).toBeNull())

    const execucao: ExecucaoResponse = {
      id: 'e-2',
      blocoPlanoId: 'b-2',
      status: 'EM_ANDAMENTO',
      iniciadoEm: '2026-10-20T15:00:00Z',
      concluidoEm: null,
      abortadoEm: null,
    }
    act(() => {
      result.current.registrarInicio('Correr', 30, execucao, { habitoId: 'h-1' })
    })

    expect(result.current.sessao).toMatchObject({
      execucaoId: 'e-2',
      blocoId: 'b-2',
      habitoId: 'h-1',
    })
  })

  it('ignora registrarInicio sem blocoPlanoId (estado inalterado)', async () => {
    const { result } = renderHook(() => useSessaoAtiva(), { wrapper })
    await waitFor(() => expect(result.current.sessao).toBeNull())

    act(() => {
      result.current.registrarInicio('X', 15, {
        id: 'e-x',
        blocoPlanoId: null,
        status: 'EM_ANDAMENTO',
        iniciadoEm: null,
        concluidoEm: null,
        abortadoEm: null,
      })
    })

    expect(result.current.sessao).toBeNull()
  })

  it('preserva sessão avulsa de hábito quando a listagem do plano vem vazia', async () => {
    vi.mocked(execucaoApi.listarExecucoes).mockResolvedValue({
      planoAprovado: null,
      itens: [],
    })
    const { result } = renderHook(() => useSessaoAtiva(), { wrapper })
    await waitFor(() => expect(result.current.sessao).toBeNull())

    act(() => {
      result.current.registrarInicio('Correr', 30, {
        id: 'e-h',
        blocoPlanoId: 'b-h',
        status: 'EM_ANDAMENTO',
        iniciadoEm: '2026-10-20T15:00:00Z',
        concluidoEm: null,
        abortadoEm: null,
      }, { habitoId: 'h-1' })
    })
    await act(async () => {
      await result.current.refrescarDoDia()
    })

    expect(result.current.sessao?.habitoId).toBe('h-1')
  })

  it('mantém a sessão local quando listarExecucoes falha', async () => {
    const { result } = renderHook(() => useSessaoAtiva(), { wrapper })
    await waitFor(() => expect(result.current.sessao).toBeNull())
    act(() => {
      result.current.registrarInicio('Estudar', 60, {
        id: 'e-1',
        blocoPlanoId: 'b-1',
        status: 'EM_ANDAMENTO',
        iniciadoEm: '2026-10-20T14:00:00Z',
        concluidoEm: null,
        abortadoEm: null,
      })
    })
    vi.mocked(execucaoApi.listarExecucoes).mockRejectedValue(new ApiError(500, 'Falha'))

    await act(async () => {
      await result.current.refrescarDoDia()
    })

    expect(result.current.sessao?.execucaoId).toBe('e-1')
  })

  it('lança fora do provider', () => {
    expect(() => renderHook(() => useSessaoAtiva())).toThrow(
      /useSessaoAtiva deve ser usado dentro de SessaoAtivaProvider/,
    )
  })

  it('limpa a sessão local', async () => {
    const { result } = renderHook(() => useSessaoAtiva(), { wrapper })
    await waitFor(() => expect(result.current.sessao).toBeNull())
    act(() => {
      result.current.definirSessao({
        execucaoId: 'e-1',
        blocoId: 'b-1',
        titulo: 'Estudar',
        duracaoMinutos: 60,
        iniciadoEm: null,
      })
    })
    act(() => {
      result.current.limpar()
    })

    expect(result.current.sessao).toBeNull()
  })
})
