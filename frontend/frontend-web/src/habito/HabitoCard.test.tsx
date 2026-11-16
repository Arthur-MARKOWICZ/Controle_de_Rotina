import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../auth/api/authApi'
import { SessaoAtivaProvider } from '../execucao/SessaoAtivaContext'
import { ToastProvider } from '../feedback/ToastProvider'
import { XpProgressProvider } from '../xp/XpProgressContext'
import * as execucaoApi from '../execucao/api/execucaoApi'
import type { ExecucaoResponse, ItemExecucaoResponse } from '../execucao/api/execucaoApi'
import * as habitoApi from './api/habitoApi'
import type { HabitoResponse } from './api/habitoApi'
import { HabitoCard, podeSessaoHabito } from './HabitoCard'

vi.mock('../tag/api/tagApi', () => ({
  listarTags: vi.fn().mockResolvedValue([]),
  criarTag: vi.fn(),
  TAG_COR_PADRAO: '#6B7280',
}))

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

vi.mock('../execucao/api/execucaoApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../execucao/api/execucaoApi')>()
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
    dataHojeIso: () => '2026-11-11',
  }
})

vi.mock('./api/habitoApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api/habitoApi')>()
  return {
    ...actual,
    iniciarHabitoAvulso: vi.fn(),
    concluirHabitoDireto: vi.fn(),
  }
})

const ativo: HabitoResponse = {
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

const execucaoCorrer: ExecucaoResponse = {
  id: 'e-1',
  blocoPlanoId: 'b-1',
  status: 'EM_ANDAMENTO',
  iniciadoEm: '2026-11-11T10:00:00Z',
  concluidoEm: null,
  abortadoEm: null,
}

function sessaoDoServidor(habitoId: string, titulo: string): ItemExecucaoResponse {
  return {
    bloco: { id: 'b-1', duracaoMinutos: 30, ordem: 1 },
    tarefaResumo: null,
    habitoResumo: { id: habitoId, titulo, horarioPreferencial: null, streakAtual: 0 },
    execucao: execucaoCorrer,
  }
}

const callbacks = {
  onPausar: vi.fn(),
  onReativar: vi.fn(),
  onExcluir: vi.fn(),
  onRestaurar: vi.fn(),
}

function renderCard(habito: HabitoResponse = ativo, acaoId: string | null = null) {
  return render(
    <ToastProvider>
      <XpProgressProvider>
        <SessaoAtivaProvider>
          <MemoryRouter>
            <ul>
              <HabitoCard habito={habito} acaoId={acaoId} {...callbacks} />
            </ul>
          </MemoryRouter>
        </SessaoAtivaProvider>
      </XpProgressProvider>
    </ToastProvider>,
  )
}

describe('podeSessaoHabito', () => {
  it('permite sessão de hábito ativo no dia da semana', () => {
    expect(podeSessaoHabito(ativo, '2026-11-11')).toBe(true)
  })

  it('bloqueia hábito pausado, excluído ou fora do dia', () => {
    expect(podeSessaoHabito({ ...ativo, status: 'PAUSADO' }, '2026-11-11')).toBe(false)
    expect(podeSessaoHabito({ ...ativo, deletada: true }, '2026-11-11')).toBe(false)
    expect(podeSessaoHabito(ativo, '2026-11-10')).toBe(false)
  })
})

describe('HabitoCard', () => {
  beforeEach(() => {
    Object.values(callbacks).forEach((fn) => fn.mockReset())
    vi.mocked(habitoApi.iniciarHabitoAvulso).mockReset()
    vi.mocked(habitoApi.concluirHabitoDireto).mockReset()
    vi.mocked(execucaoApi.buscarSessaoAtiva).mockReset().mockResolvedValue(null)
    vi.mocked(execucaoApi.concluirExecucao).mockReset()
    vi.mocked(execucaoApi.abortarExecucao).mockReset()
  })

  it('mostra Iniciar e Concluir para hábito ativo no dia', async () => {
    renderCard()

    expect(await screen.findByRole('link', { name: 'Correr' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /iniciar correr/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /concluir correr/i })).toBeInTheDocument()
  })

  it('não inicia sessão quando o hábito está pausado (estado inalterado)', () => {
    renderCard({ ...ativo, status: 'PAUSADO' })

    expect(screen.queryByRole('button', { name: /iniciar correr/i })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /reativar/i })).toBeInTheDocument()
  })

  it('não inicia sessão quando o hábito está excluído', () => {
    renderCard({ ...ativo, deletada: true, deletedAt: '2026-11-01T00:00:00Z' })

    expect(screen.queryByRole('button', { name: /iniciar correr/i })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /restaurar/i })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^excluir$/i })).not.toBeInTheDocument()
  })

  it('mostra Restaurando… sem disparar outra ação quando acaoId coincide', () => {
    renderCard({ ...ativo, deletada: true, deletedAt: '2026-11-01T00:00:00Z' }, 'h-1')

    expect(screen.getByRole('button', { name: /restaurando/i })).toBeDisabled()
  })

  it('dispara onPausar e onExcluir no hábito ativo', async () => {
    const user = userEvent.setup()
    renderCard()

    await user.click(await screen.findByRole('button', { name: /^pausar$/i }))
    await user.click(screen.getByRole('button', { name: /^excluir$/i }))

    expect(callbacks.onPausar).toHaveBeenCalledWith('h-1')
    expect(callbacks.onExcluir).toHaveBeenCalledWith('h-1', 'Correr')
  })

  it('mostra toast de erro e não trava o botão quando iniciar falha', async () => {
    vi.mocked(habitoApi.iniciarHabitoAvulso).mockRejectedValue(new ApiError(409, 'Já cumprido hoje'))
    const user = userEvent.setup()
    renderCard()

    await user.click(await screen.findByRole('button', { name: /iniciar correr/i }))

    expect(await screen.findByText('Já cumprido hoje')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /iniciar correr/i })).not.toBeDisabled()
  })

  it('mostra toast de erro quando concluir direto falha', async () => {
    vi.mocked(habitoApi.concluirHabitoDireto).mockRejectedValue(
      new ApiError(500, 'Falha ao concluir'),
    )
    const user = userEvent.setup()
    renderCard()

    await user.click(await screen.findByRole('button', { name: /concluir correr/i }))
    await user.click(screen.getByRole('button', { name: 'Concluir' }))

    expect(await screen.findByText('Falha ao concluir')).toBeInTheDocument()
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /concluir correr/i })).not.toBeDisabled()
    })
  })

  it('pede os minutos e envia o valor informado ao concluir direto', async () => {
    vi.mocked(habitoApi.concluirHabitoDireto).mockResolvedValue({
      id: 'e-1',
      blocoPlanoId: 'b-1',
      status: 'CONCLUIDO',
      iniciadoEm: null,
      concluidoEm: '2026-11-11T15:20:00Z',
      abortadoEm: null,
    })
    const user = userEvent.setup()
    renderCard()

    await user.click(await screen.findByRole('button', { name: /concluir correr/i }))
    const campo = screen.getByLabelText('Minutos dedicados')
    expect(campo).toHaveValue(30)
    await user.clear(campo)
    await user.type(campo, '20')
    await user.click(screen.getByRole('button', { name: 'Concluir' }))

    await waitFor(() => {
      expect(habitoApi.concluirHabitoDireto).toHaveBeenCalledWith('h-1', expect.any(String), 20)
    })
  })

  it('cancelar o diálogo de minutos não conclui o hábito', async () => {
    const user = userEvent.setup()
    renderCard()

    await user.click(await screen.findByRole('button', { name: /concluir correr/i }))
    await user.click(screen.getByRole('button', { name: 'Cancelar' }))

    expect(habitoApi.concluirHabitoDireto).not.toHaveBeenCalled()
    expect(screen.queryByLabelText('Minutos dedicados')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /concluir correr/i })).not.toBeDisabled()
  })

  it('mostra a mensagem do servidor quando os minutos são rejeitados (400)', async () => {
    vi.mocked(habitoApi.concluirHabitoDireto).mockRejectedValue(
      new ApiError(400, 'Minutos informados inválidos'),
    )
    const user = userEvent.setup()
    renderCard()

    await user.click(await screen.findByRole('button', { name: /concluir correr/i }))
    await user.click(screen.getByRole('button', { name: 'Concluir' }))

    expect(await screen.findByText('Minutos informados inválidos')).toBeInTheDocument()
  })

  describe('sessão restaurada do servidor', () => {
    it('mostra Finalizar e Pausar após F5 quando a sessão ativa é deste hábito', async () => {
      vi.mocked(execucaoApi.buscarSessaoAtiva).mockResolvedValue(sessaoDoServidor('h-1', 'Correr'))
      renderCard()

      expect(await screen.findByRole('button', { name: /finalizar correr/i })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /pausar sessão de correr/i })).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /iniciar correr/i })).not.toBeInTheDocument()
    })

    it('mostra Iniciar quando a sessão ativa é de outro hábito', async () => {
      vi.mocked(execucaoApi.buscarSessaoAtiva).mockResolvedValue(sessaoDoServidor('h-2', 'Meditar'))
      renderCard()

      await waitFor(() => expect(screen.getByRole('region', { name: 'Agora' })).toBeInTheDocument())
      expect(screen.getByRole('button', { name: /iniciar correr/i })).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /finalizar correr/i })).not.toBeInTheDocument()
    })

    it('mostra Iniciar e nenhuma barra quando não há sessão no servidor', async () => {
      renderCard()

      await waitFor(() => expect(execucaoApi.buscarSessaoAtiva).toHaveBeenCalled())
      expect(screen.getByRole('button', { name: /iniciar correr/i })).toBeInTheDocument()
      expect(screen.queryByRole('region', { name: 'Agora' })).not.toBeInTheDocument()
    })

    it('retoma sem erro quando iniciar devolve a execução já em andamento (200)', async () => {
      vi.mocked(habitoApi.iniciarHabitoAvulso).mockResolvedValue(execucaoCorrer)
      const user = userEvent.setup()
      renderCard()
      await waitFor(() => expect(execucaoApi.buscarSessaoAtiva).toHaveBeenCalled())
      vi.mocked(execucaoApi.buscarSessaoAtiva).mockResolvedValue(sessaoDoServidor('h-1', 'Correr'))

      await user.click(screen.getByRole('button', { name: /iniciar correr/i }))

      expect(await screen.findByRole('button', { name: /finalizar correr/i })).toBeInTheDocument()
      expect(screen.getByRole('region', { name: 'Agora' })).toBeInTheDocument()
      expect(screen.queryByText(/não foi possível/i)).not.toBeInTheDocument()
    })

    it('volta para Iniciar depois de concluir pela barra global', async () => {
      vi.mocked(execucaoApi.buscarSessaoAtiva).mockResolvedValue(sessaoDoServidor('h-1', 'Correr'))
      vi.mocked(execucaoApi.concluirExecucao).mockResolvedValue({
        ...execucaoCorrer,
        status: 'CONCLUIDO',
        concluidoEm: '2026-11-11T10:30:00Z',
        xp: null,
      })
      const user = userEvent.setup()
      renderCard()
      const barra = await screen.findByRole('region', { name: 'Agora' })

      await user.click(within(barra).getByRole('button', { name: /concluir correr/i }))

      expect(await screen.findByRole('button', { name: /iniciar correr/i })).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /finalizar correr/i })).not.toBeInTheDocument()
    })

    it('mostra a mensagem da API e mantém Iniciar quando o hábito já foi concluído (409)', async () => {
      vi.mocked(habitoApi.iniciarHabitoAvulso).mockRejectedValue(
        new ApiError(409, 'Não é possível iniciar um bloco já concluído.'),
      )
      const user = userEvent.setup()
      renderCard()

      await user.click(await screen.findByRole('button', { name: /iniciar correr/i }))

      expect(
        await screen.findByText('Não é possível iniciar um bloco já concluído.'),
      ).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /iniciar correr/i })).toBeInTheDocument()
      expect(screen.queryByRole('region', { name: 'Agora' })).not.toBeInTheDocument()
    })

    it('mostra a mensagem e não registra sessão quando iniciar responde 404', async () => {
      vi.mocked(habitoApi.iniciarHabitoAvulso).mockRejectedValue(
        new ApiError(404, 'Hábito não encontrado'),
      )
      const user = userEvent.setup()
      renderCard()

      await user.click(await screen.findByRole('button', { name: /iniciar correr/i }))

      expect(await screen.findByText('Hábito não encontrado')).toBeInTheDocument()
      expect(screen.queryByRole('region', { name: 'Agora' })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /finalizar correr/i })).not.toBeInTheDocument()
    })

    it('mostra Iniciar/Concluir sem barra nem toast quando a consulta falha com 500 na montagem', async () => {
      vi.mocked(execucaoApi.buscarSessaoAtiva).mockRejectedValue(new ApiError(500, 'Erro interno'))
      renderCard()

      await waitFor(() => expect(execucaoApi.buscarSessaoAtiva).toHaveBeenCalled())
      expect(screen.getByRole('button', { name: /iniciar correr/i })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /concluir correr/i })).toBeInTheDocument()
      expect(screen.queryByRole('region', { name: 'Agora' })).not.toBeInTheDocument()
      expect(screen.queryByText('Erro interno')).not.toBeInTheDocument()
      expect(habitoApi.iniciarHabitoAvulso).not.toHaveBeenCalled()
    })

    it('mostra Iniciar/Concluir sem barra nem toast quando a rede falha (timeout) na montagem', async () => {
      vi.mocked(execucaoApi.buscarSessaoAtiva).mockRejectedValue(new TypeError('Failed to fetch'))
      renderCard()

      await waitFor(() => expect(execucaoApi.buscarSessaoAtiva).toHaveBeenCalled())
      expect(screen.getByRole('button', { name: /iniciar correr/i })).toBeInTheDocument()
      expect(screen.queryByRole('region', { name: 'Agora' })).not.toBeInTheDocument()
      expect(screen.queryByText('Failed to fetch')).not.toBeInTheDocument()
    })
  })
})
