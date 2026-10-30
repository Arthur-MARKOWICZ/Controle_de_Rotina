import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ToastProvider } from '../feedback/ToastProvider'
import * as projetoApi from '../projeto/api/projetoApi'
import * as tarefaApi from './api/tarefaApi'
import { fimDoDiaBrtParaDatetimeLocal } from './prazoAtalhos'
import { QuickAddTarefaDialog } from './QuickAddTarefaDialog'

vi.mock('./api/tarefaApi', () => ({
  criarTarefa: vi.fn(),
}))

vi.mock('../projeto/api/projetoApi', () => ({
  listarProjetos: vi.fn(),
}))

function renderDialog(open = true) {
  return render(
    <MemoryRouter>
      <ToastProvider>
        <QuickAddTarefaDialog open={open} onClose={vi.fn()} />
      </ToastProvider>
    </MemoryRouter>,
  )
}

describe('QuickAddTarefaDialog', () => {
  beforeEach(() => {
    vi.mocked(tarefaApi.criarTarefa).mockReset()
    vi.mocked(projetoApi.listarProjetos).mockReset()
    vi.mocked(projetoApi.listarProjetos).mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      number: 0,
      size: 20,
    })
    vi.useFakeTimers({ shouldAdvanceTime: true })
    vi.setSystemTime(new Date('2026-10-28T15:00:00.000Z'))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('T3: chip Amanhã preenche o prazo esperado', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderDialog()

    await user.click(screen.getByRole('button', { name: 'Amanhã' }))

    expect(screen.getByLabelText(/prazo \(opcional\)/i)).toHaveValue(
      fimDoDiaBrtParaDatetimeLocal(2026, 10, 29),
    )
  })

  it('T6: select de prioridade mostra labels PT, não enum cru', () => {
    renderDialog()

    const select = screen.getByLabelText(/prioridade/i)
    expect(select).toHaveTextContent('Alta')
    expect(select).toHaveTextContent('Média')
    expect(select).toHaveTextContent('Baixa')
    expect(select).not.toHaveTextContent('ALTA')
    expect(select).not.toHaveTextContent('MEDIA')
    expect(select).not.toHaveTextContent('BAIXA')
  })

  it('T5: título vazio exibe erro inline e não chama API', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderDialog()

    await user.click(screen.getByRole('button', { name: 'Hoje' }))
    await user.click(screen.getByRole('button', { name: /criar tarefa/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/título/i)
    expect(tarefaApi.criarTarefa).not.toHaveBeenCalled()
  })

  it('T4: submit válido chama API e dispara toast de sucesso', async () => {
    vi.mocked(tarefaApi.criarTarefa).mockResolvedValue({
      id: 't-99',
      titulo: 'Ler docs',
      descricao: '',
      prioridade: 'MEDIA',
      prazo: '2026-10-28T26:59:00.000Z',
      status: 'PENDENTE',
      duracaoMinutos: 60,
      createdAt: '2026-10-28T12:00:00Z',
      updatedAt: '2026-10-28T12:00:00Z',
      deletedAt: null,
      deletada: false,
    })
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderDialog()

    await user.type(screen.getByLabelText(/^título$/i), 'Ler docs')
    await user.click(screen.getByRole('button', { name: 'Hoje' }))
    await user.click(screen.getByRole('button', { name: /criar tarefa/i }))

    await waitFor(() => {
      expect(tarefaApi.criarTarefa).toHaveBeenCalledWith(
        expect.objectContaining({
          titulo: 'Ler docs',
          descricao: '',
          prioridade: 'MEDIA',
          duracaoMinutos: 60,
        }),
      )
    })
    expect(await screen.findByText('Tarefa criada')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Ver' })).toBeInTheDocument()
  })
})
