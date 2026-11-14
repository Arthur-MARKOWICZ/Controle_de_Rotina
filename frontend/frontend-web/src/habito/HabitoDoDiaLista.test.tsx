import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../auth/api/authApi'
import * as execucaoApi from '../execucao/api/execucaoApi'
import { ToastProvider } from '../feedback/ToastProvider'
import * as habitoApi from './api/habitoApi'
import type { HabitoDoDia } from './api/habitoApi'
import { HabitoDoDiaLista } from './HabitoDoDiaLista'

vi.mock('./api/habitoApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api/habitoApi')>()
  return {
    ...actual,
    listarHabitosDoDia: vi.fn(),
    iniciarHabitoAvulso: vi.fn(),
    concluirHabitoDireto: vi.fn(),
  }
})

vi.mock('../execucao/api/execucaoApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../execucao/api/execucaoApi')>()
  return {
    ...actual,
    concluirExecucao: vi.fn(),
    abortarExecucao: vi.fn(),
  }
})

const pendente: HabitoDoDia = {
  id: 'h-1',
  titulo: 'Correr',
  duracaoMinutos: 30,
  horarioPreferencial: '07:00:00',
  streakAtual: 2,
  cumprido: false,
  noPlanoAprovado: false,
  execucaoAvulsa: null,
  execucaoNoPlano: null,
}

const emAndamento: HabitoDoDia = {
  ...pendente,
  execucaoAvulsa: {
    blocoId: 'b-1',
    execucaoId: 'e-1',
    status: 'EM_ANDAMENTO',
    iniciadoEm: '2026-11-11T07:00:00Z',
  },
}

function renderLista(habitos: HabitoDoDia[], onAtualizado = vi.fn()) {
  return render(
    <ToastProvider>
      <MemoryRouter>
        <HabitoDoDiaLista habitos={habitos} data="2026-11-11" onAtualizado={onAtualizado} />
      </MemoryRouter>
    </ToastProvider>,
  )
}

describe('HabitoDoDiaLista', () => {
  beforeEach(() => {
    vi.mocked(habitoApi.listarHabitosDoDia).mockReset().mockResolvedValue([])
    vi.mocked(habitoApi.iniciarHabitoAvulso).mockReset()
    vi.mocked(habitoApi.concluirHabitoDireto).mockReset()
    vi.mocked(execucaoApi.concluirExecucao).mockReset()
    vi.mocked(execucaoApi.abortarExecucao).mockReset()
  })

  it('mostra estado vazio quando não há hábitos no dia', () => {
    renderLista([])

    expect(screen.getByText('Nenhum hábito neste dia.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /iniciar/i })).not.toBeInTheDocument()
  })

  it('inicia o hábito e recarrega a lista', async () => {
    const onAtualizado = vi.fn()
    vi.mocked(habitoApi.iniciarHabitoAvulso).mockResolvedValue({
      id: 'e-1',
      blocoPlanoId: 'b-1',
      status: 'EM_ANDAMENTO',
      iniciadoEm: '2026-11-11T07:00:00Z',
      concluidoEm: null,
      abortadoEm: null,
    })
    vi.mocked(habitoApi.listarHabitosDoDia).mockResolvedValue([emAndamento])
    const user = userEvent.setup()
    renderLista([pendente], onAtualizado)

    await user.click(screen.getByRole('button', { name: /iniciar correr/i }))

    await waitFor(() => {
      expect(habitoApi.iniciarHabitoAvulso).toHaveBeenCalledWith('h-1', '2026-11-11')
    })
    expect(onAtualizado).toHaveBeenCalledWith([emAndamento])
  })

  it('não chama a API ao concluir quando não há execução (estado inalterado)', () => {
    const onAtualizado = vi.fn()
    renderLista(
      [
        {
          ...pendente,
          cumprido: true,
          execucaoAvulsa: {
            blocoId: 'b-1',
            execucaoId: null,
            status: 'CONCLUIDO',
            iniciadoEm: null,
          },
        },
      ],
      onAtualizado,
    )

    expect(screen.queryByRole('button', { name: /concluir correr/i })).not.toBeInTheDocument()
    expect(onAtualizado).not.toHaveBeenCalled()
    expect(execucaoApi.concluirExecucao).not.toHaveBeenCalled()
  })

  it('mostra toast e não atualiza a lista quando iniciar falha', async () => {
    const onAtualizado = vi.fn()
    vi.mocked(habitoApi.iniciarHabitoAvulso).mockRejectedValue(new ApiError(409, 'Já em andamento'))
    const user = userEvent.setup()
    renderLista([pendente], onAtualizado)

    await user.click(screen.getByRole('button', { name: /iniciar correr/i }))

    expect(await screen.findByText('Já em andamento')).toBeInTheDocument()
    expect(onAtualizado).not.toHaveBeenCalled()
  })

  it('mostra toast quando concluir direto falha', async () => {
    const onAtualizado = vi.fn()
    vi.mocked(habitoApi.concluirHabitoDireto).mockRejectedValue(new ApiError(500, 'Falha ao concluir'))
    const user = userEvent.setup()
    renderLista([pendente], onAtualizado)

    await user.click(screen.getByRole('button', { name: /concluir direto correr/i }))
    await user.click(screen.getByRole('button', { name: 'Concluir' }))

    expect(await screen.findByText('Falha ao concluir')).toBeInTheDocument()
    expect(onAtualizado).not.toHaveBeenCalled()
  })

  it('concluir direto envia os minutos confirmados no diálogo', async () => {
    const onAtualizado = vi.fn()
    vi.mocked(habitoApi.concluirHabitoDireto).mockResolvedValue({
      id: 'e-1',
      blocoPlanoId: 'b-1',
      status: 'CONCLUIDO',
      iniciadoEm: null,
      concluidoEm: '2026-11-11T15:20:00Z',
      abortadoEm: null,
    })
    vi.mocked(habitoApi.listarHabitosDoDia).mockResolvedValue([])
    const user = userEvent.setup()
    renderLista([pendente], onAtualizado)

    await user.click(screen.getByRole('button', { name: /concluir direto correr/i }))
    await user.clear(screen.getByLabelText('Minutos dedicados'))
    await user.type(screen.getByLabelText('Minutos dedicados'), '15')
    await user.click(screen.getByRole('button', { name: 'Concluir' }))

    await waitFor(() => expect(onAtualizado).toHaveBeenCalled())
    expect(vi.mocked(habitoApi.concluirHabitoDireto).mock.calls[0][2]).toBe(15)
  })

  it('cancelar o diálogo não chama a API nem recarrega', async () => {
    const onAtualizado = vi.fn()
    const user = userEvent.setup()
    renderLista([pendente], onAtualizado)

    await user.click(screen.getByRole('button', { name: /concluir direto correr/i }))
    await user.click(screen.getByRole('button', { name: 'Cancelar' }))

    expect(habitoApi.concluirHabitoDireto).not.toHaveBeenCalled()
    expect(onAtualizado).not.toHaveBeenCalled()
  })

  it('400 de minutos inválidos mostra o erro e mantém o hábito pendente', async () => {
    const onAtualizado = vi.fn()
    vi.mocked(habitoApi.concluirHabitoDireto).mockRejectedValue(
      new ApiError(400, 'Minutos informados inválidos'),
    )
    const user = userEvent.setup()
    renderLista([pendente], onAtualizado)

    await user.click(screen.getByRole('button', { name: /concluir direto correr/i }))
    await user.click(screen.getByRole('button', { name: 'Concluir' }))

    expect(await screen.findByText('Minutos informados inválidos')).toBeInTheDocument()
    expect(onAtualizado).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: /concluir direto correr/i })).toBeInTheDocument()
  })

  it('mostra toast quando abortar falha', async () => {
    const onAtualizado = vi.fn()
    vi.mocked(execucaoApi.abortarExecucao).mockRejectedValue(new ApiError(404, 'Execução não encontrada'))
    const user = userEvent.setup()
    renderLista([emAndamento], onAtualizado)

    await user.click(screen.getByRole('button', { name: /abortar correr/i }))

    expect(await screen.findByText('Execução não encontrada')).toBeInTheDocument()
    expect(onAtualizado).not.toHaveBeenCalled()
  })

  it('aborta a sessão em andamento e recarrega', async () => {
    const onAtualizado = vi.fn()
    vi.mocked(execucaoApi.abortarExecucao).mockResolvedValue({
      id: 'e-1',
      blocoPlanoId: 'b-1',
      status: 'ABORTADO',
      iniciadoEm: '2026-11-11T07:00:00Z',
      concluidoEm: null,
      abortadoEm: '2026-11-11T07:10:00Z',
    })
    vi.mocked(habitoApi.listarHabitosDoDia).mockResolvedValue([pendente])
    const user = userEvent.setup()
    renderLista([emAndamento], onAtualizado)

    await user.click(screen.getByRole('button', { name: /abortar correr/i }))

    await waitFor(() => {
      expect(execucaoApi.abortarExecucao).toHaveBeenCalledWith('e-1')
    })
    expect(onAtualizado).toHaveBeenCalledWith([pendente])
  })
})
