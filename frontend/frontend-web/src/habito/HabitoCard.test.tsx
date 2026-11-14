import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../auth/api/authApi'
import { SessaoAtivaProvider } from '../execucao/SessaoAtivaContext'
import { ToastProvider } from '../feedback/ToastProvider'
import { XpProgressProvider } from '../xp/XpProgressContext'
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
    listarExecucoes: vi.fn().mockResolvedValue({ planoAprovado: null, itens: [] }),
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
})
