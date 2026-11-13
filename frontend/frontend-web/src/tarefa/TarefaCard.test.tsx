import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../auth/api/authApi'
import * as execucaoApi from '../execucao/api/execucaoApi'
import { SessaoAtivaProvider } from '../execucao/SessaoAtivaContext'
import { ToastProvider } from '../feedback/ToastProvider'
import { XpProgressProvider } from '../xp/XpProgressContext'
import * as tarefaApi from './api/tarefaApi'
import type { TarefaResponse } from './api/tarefaApi'
import { TarefaCard } from './TarefaCard'

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
  }
})

vi.mock('./api/tarefaApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api/tarefaApi')>()
  return {
    ...actual,
    iniciarTarefaSessao: vi.fn(),
    concluirTarefaDireto: vi.fn(),
  }
})

vi.mock('../planner/data', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../planner/data')>()
  return {
    ...actual,
    dataHojeIso: () => '2026-10-20',
  }
})

const pendente: TarefaResponse = {
  id: 't-1',
  titulo: 'Estudar',
  descricao: '',
  prioridade: 'ALTA',
  prazo: '2030-01-01T12:00:00Z',
  status: 'PENDENTE',
  duracaoMinutos: 60,
  restanteMinutos: 60,
  createdAt: '2026-01-01T10:00:00Z',
  updatedAt: '2026-01-01T10:00:00Z',
  deletedAt: null,
  deletada: false,
}

const onExcluir = vi.fn()
const onRestaurar = vi.fn()
const onSessaoAlterada = vi.fn()

function renderCard(tarefa: TarefaResponse = pendente) {
  return render(
    <ToastProvider>
      <XpProgressProvider>
        <SessaoAtivaProvider>
          <MemoryRouter>
            <TarefaCard
              tarefa={tarefa}
              visao="todas"
              search=""
              onExcluir={onExcluir}
              onRestaurar={onRestaurar}
              onSessaoAlterada={onSessaoAlterada}
            />
          </MemoryRouter>
        </SessaoAtivaProvider>
      </XpProgressProvider>
    </ToastProvider>,
  )
}

describe('TarefaCard', () => {
  beforeEach(() => {
    onExcluir.mockReset()
    onRestaurar.mockReset()
    onSessaoAlterada.mockReset()
    vi.mocked(execucaoApi.listarExecucoes).mockReset().mockResolvedValue({
      planoAprovado: null,
      itens: [],
    })
    vi.mocked(tarefaApi.iniciarTarefaSessao).mockReset()
    vi.mocked(tarefaApi.concluirTarefaDireto).mockReset()
  })

  it('mostra título e ações de sessão para tarefa pendente', async () => {
    renderCard()

    expect(await screen.findByRole('link', { name: /estudar/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /iniciar estudar/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /concluir estudar/i })).toBeInTheDocument()
  })

  it('não oferece sessão para subtarefa-pai (temFilhos)', () => {
    renderCard({ ...pendente, temFilhos: true })

    expect(screen.queryByRole('button', { name: /iniciar estudar/i })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: /estudar/i })).toBeInTheDocument()
  })

  it('não oferece sessão para tarefa bloqueada por espera', () => {
    renderCard({ ...pendente, bloqueada: true, esperas: [] })

    expect(screen.queryByRole('button', { name: /iniciar estudar/i })).not.toBeInTheDocument()
    expect(screen.getByText(/bloqueada por espera/i)).toBeInTheDocument()
  })

  it('mostra Restaurar e não Iniciar quando a tarefa está excluída', () => {
    renderCard({ ...pendente, deletada: true, deletedAt: '2026-10-01T00:00:00Z' })

    expect(screen.queryByRole('button', { name: /iniciar estudar/i })).not.toBeInTheDocument()
    expect(screen.getByText('Excluída')).toBeInTheDocument()
  })

  it('mostra toast e não inicia sessão quando o plano do dia falha', async () => {
    vi.mocked(execucaoApi.listarExecucoes).mockRejectedValue(new ApiError(500, 'Plano indisponível'))
    const user = userEvent.setup()
    renderCard()

    await user.click(await screen.findByRole('button', { name: /iniciar estudar/i }))

    expect(await screen.findByText('Plano indisponível')).toBeInTheDocument()
    expect(tarefaApi.iniciarTarefaSessao).not.toHaveBeenCalled()
    expect(onSessaoAlterada).not.toHaveBeenCalled()
  })

  it('mostra toast quando iniciar a sessão falha', async () => {
    vi.mocked(tarefaApi.iniciarTarefaSessao).mockRejectedValue(new ApiError(409, 'Já em andamento'))
    const user = userEvent.setup()
    renderCard()

    await user.click(await screen.findByRole('button', { name: /iniciar estudar/i }))
    await user.click(await screen.findByRole('button', { name: /15 min/i }))

    expect(await screen.findByText('Já em andamento')).toBeInTheDocument()
    expect(onSessaoAlterada).not.toHaveBeenCalled()
  })

  it('dispara onExcluir pelo menu de ações', async () => {
    const user = userEvent.setup()
    renderCard()

    await user.click(screen.getByRole('button', { name: /excluir estudar/i }))

    expect(onExcluir).toHaveBeenCalledWith(pendente)
  })
})
