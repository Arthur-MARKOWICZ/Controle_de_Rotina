import { act, renderHook, screen, waitFor } from '@testing-library/react'
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
    buscarSessaoAtiva: vi.fn(),
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

const itemHabitoAvulso: ItemExecucaoResponse = {
  bloco: { id: 'b-h', duracaoMinutos: 30, ordem: 1 },
  tarefaResumo: null,
  habitoResumo: { id: 'h-1', titulo: 'Ler', horarioPreferencial: null, streakAtual: 0 },
  execucao: {
    id: 'e-h',
    blocoPlanoId: 'b-h',
    status: 'EM_ANDAMENTO',
    iniciadoEm: '2026-10-20T11:00:00Z',
    concluidoEm: null,
    abortadoEm: null,
  },
}

function adiada<T>() {
  let resolver!: (valor: T) => void
  const promessa = new Promise<T>((resolve) => {
    resolver = resolve
  })
  return { promessa, resolver }
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
    vi.mocked(execucaoApi.buscarSessaoAtiva).mockReset().mockResolvedValue(null)
    vi.mocked(execucaoApi.concluirExecucao).mockReset()
    vi.mocked(execucaoApi.abortarExecucao).mockReset()
  })

  it('restaura sessão avulsa de hábito do servidor ao montar (F5)', async () => {
    vi.mocked(execucaoApi.buscarSessaoAtiva).mockResolvedValue(itemHabitoAvulso)
    const { result } = renderHook(() => useSessaoAtiva(), { wrapper })

    await waitFor(() => expect(result.current.sessao?.execucaoId).toBe('e-h'))
    expect(result.current.sessao).toMatchObject({
      titulo: 'Ler',
      habitoId: 'h-1',
      iniciadoEm: '2026-10-20T11:00:00Z',
    })
    expect(screen.getByText('Ler')).toBeInTheDocument()
  })

  it('restaura sessão de tarefa avulsa do servidor ao montar', async () => {
    vi.mocked(execucaoApi.buscarSessaoAtiva).mockResolvedValue(itemAndamento)
    const { result } = renderHook(() => useSessaoAtiva(), { wrapper })

    await waitFor(() => expect(result.current.sessao?.execucaoId).toBe('e-1'))
    expect(result.current.sessao?.titulo).toBe('Estudar')
    expect(result.current.sessao?.habitoId).toBeNull()
  })

  it('não mostra barra quando o servidor responde sem sessão (204)', async () => {
    const { result } = renderHook(() => useSessaoAtiva(), { wrapper })

    await waitFor(() => expect(execucaoApi.buscarSessaoAtiva).toHaveBeenCalled())
    expect(result.current.sessao).toBeNull()
    expect(screen.queryByRole('button', { name: /concluir/i })).not.toBeInTheDocument()
  })

  it('mantém tarefa avulsa após registrarInicio seguido de refresh', async () => {
    const { result } = renderHook(() => useSessaoAtiva(), { wrapper })
    await waitFor(() => expect(execucaoApi.buscarSessaoAtiva).toHaveBeenCalled())

    act(() => {
      result.current.registrarInicio('Estudar', 60, itemAndamento.execucao)
    })
    vi.mocked(execucaoApi.buscarSessaoAtiva).mockResolvedValue(itemAndamento)
    await act(async () => {
      await result.current.refrescarDoDia()
    })

    expect(result.current.sessao?.execucaoId).toBe('e-1')
  })

  it('remove a sessão quando ela foi encerrada no servidor', async () => {
    vi.mocked(execucaoApi.buscarSessaoAtiva).mockResolvedValue(itemHabitoAvulso)
    const { result } = renderHook(() => useSessaoAtiva(), { wrapper })
    await waitFor(() => expect(result.current.sessao?.execucaoId).toBe('e-h'))

    vi.mocked(execucaoApi.buscarSessaoAtiva).mockResolvedValue(null)
    await act(async () => {
      await result.current.refrescarDoDia()
    })

    expect(result.current.sessao).toBeNull()
  })

  it('não mostra barra nem quebra quando o mount recebe HTML do proxy', async () => {
    vi.mocked(execucaoApi.buscarSessaoAtiva).mockRejectedValue(
      new ApiError(200, 'Resposta inválida da API (não é JSON).'),
    )
    const { result } = renderHook(() => useSessaoAtiva(), { wrapper })

    await waitFor(() => expect(execucaoApi.buscarSessaoAtiva).toHaveBeenCalled())
    expect(result.current.sessao).toBeNull()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('conclui pela barra e limpa a sessão restaurada', async () => {
    vi.mocked(execucaoApi.buscarSessaoAtiva).mockResolvedValue(itemHabitoAvulso)
    vi.mocked(execucaoApi.concluirExecucao).mockResolvedValue({
      ...itemHabitoAvulso.execucao,
      status: 'CONCLUIDO',
      concluidoEm: '2026-10-20T11:30:00Z',
      xp: null,
    })
    const { result } = renderHook(() => useSessaoAtiva(), { wrapper })
    await waitFor(() => expect(result.current.sessao?.execucaoId).toBe('e-h'))

    await act(async () => {
      screen.getByRole('button', { name: /concluir/i }).click()
    })

    await waitFor(() => expect(result.current.sessao).toBeNull())
    expect(execucaoApi.concluirExecucao).toHaveBeenCalledWith('e-h')
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

  it('mantém a sessão local quando buscarSessaoAtiva falha (500)', async () => {
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
    vi.mocked(execucaoApi.buscarSessaoAtiva).mockRejectedValue(new ApiError(500, 'Falha'))

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

  describe('respostas fora de ordem', () => {
    it('consulta lenta iniciada antes de limpar não traz a barra de volta', async () => {
      const { result } = renderHook(() => useSessaoAtiva(), { wrapper })
      await waitFor(() => expect(execucaoApi.buscarSessaoAtiva).toHaveBeenCalled())
      act(() => {
        result.current.registrarInicio('Ler', 30, itemHabitoAvulso.execucao, { habitoId: 'h-1' })
      })
      const lenta = adiada<ItemExecucaoResponse | null>()
      vi.mocked(execucaoApi.buscarSessaoAtiva).mockReturnValueOnce(lenta.promessa)

      let pendente!: Promise<void>
      act(() => {
        pendente = result.current.refrescarDoDia()
      })
      act(() => {
        result.current.limpar()
      })
      await act(async () => {
        lenta.resolver(itemHabitoAvulso)
        await pendente
      })

      expect(result.current.sessao).toBeNull()
      expect(screen.queryByRole('region', { name: 'Agora' })).not.toBeInTheDocument()
    })

    it('consulta antiga não sobrescreve a resposta de uma consulta mais nova', async () => {
      const { result } = renderHook(() => useSessaoAtiva(), { wrapper })
      await waitFor(() => expect(execucaoApi.buscarSessaoAtiva).toHaveBeenCalled())
      const antiga = adiada<ItemExecucaoResponse | null>()
      vi.mocked(execucaoApi.buscarSessaoAtiva)
        .mockReturnValueOnce(antiga.promessa)
        .mockResolvedValueOnce(itemAndamento)

      let primeira!: Promise<void>
      act(() => {
        primeira = result.current.refrescarDoDia()
      })
      await act(async () => {
        await result.current.refrescarDoDia()
      })
      expect(result.current.sessao?.execucaoId).toBe('e-1')

      await act(async () => {
        antiga.resolver(null)
        await primeira
      })

      expect(result.current.sessao?.execucaoId).toBe('e-1')
    })

    it('consulta lenta não desfaz um registrarInicio feito depois dela', async () => {
      const { result } = renderHook(() => useSessaoAtiva(), { wrapper })
      await waitFor(() => expect(execucaoApi.buscarSessaoAtiva).toHaveBeenCalled())
      const lenta = adiada<ItemExecucaoResponse | null>()
      vi.mocked(execucaoApi.buscarSessaoAtiva).mockReturnValueOnce(lenta.promessa)

      let pendente!: Promise<void>
      act(() => {
        pendente = result.current.refrescarDoDia()
      })
      act(() => {
        result.current.registrarInicio('Ler', 30, itemHabitoAvulso.execucao, { habitoId: 'h-1' })
      })
      await act(async () => {
        lenta.resolver(null)
        await pendente
      })

      expect(result.current.sessao?.execucaoId).toBe('e-h')
    })
  })
})
