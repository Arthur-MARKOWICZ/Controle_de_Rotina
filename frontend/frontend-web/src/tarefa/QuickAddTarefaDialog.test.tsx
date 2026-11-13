import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ToastProvider } from '../feedback/ToastProvider'
import * as tarefaApi from './api/tarefaApi'
import { QuickAddTarefaDialog } from './QuickAddTarefaDialog'

vi.mock('./api/tarefaApi', () => ({
  criarTarefa: vi.fn(),
}))

function Local() {
  const location = useLocation()
  return (
    <p data-testid="local">
      {location.pathname}
      {location.state && typeof location.state === 'object' && 'titulo' in location.state
        ? `:${(location.state as { titulo?: string }).titulo ?? ''}`
        : ''}
    </p>
  )
}

function renderDialog(open = true) {
  return render(
    <MemoryRouter>
      <ToastProvider>
        <Routes>
          <Route
            path="/"
            element={
              <>
                <QuickAddTarefaDialog open={open} onClose={vi.fn()} />
                <Local />
              </>
            }
          />
          <Route path="/tarefas/nova" element={<Local />} />
        </Routes>
      </ToastProvider>
    </MemoryRouter>,
  )
}

describe('QuickAddTarefaDialog', () => {
  beforeEach(() => {
    vi.mocked(tarefaApi.criarTarefa).mockReset()
  })

  it('T4: submit só com título chama API sem prioridade/duração; Mais detalhes leva ao drawer com título', async () => {
    vi.mocked(tarefaApi.criarTarefa).mockResolvedValue({
      id: 't-99',
      titulo: 'Ler docs',
      descricao: '',
      prioridade: 'MEDIA',
      prazo: null,
      status: 'PENDENTE',
      duracaoMinutos: 30,
      createdAt: '2026-10-28T12:00:00Z',
      updatedAt: '2026-10-28T12:00:00Z',
      deletedAt: null,
      deletada: false,
    })
    const user = userEvent.setup()
    renderDialog()

    expect(screen.queryByLabelText(/duração/i)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/prioridade/i)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/prazo/i)).not.toBeInTheDocument()

    await user.type(screen.getByLabelText(/^título$/i), 'Ler docs')
    await user.click(screen.getByRole('button', { name: /criar tarefa/i }))

    await waitFor(() => {
      expect(tarefaApi.criarTarefa).toHaveBeenCalledWith({ titulo: 'Ler docs' })
    })
    expect(await screen.findByText('Tarefa criada')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Abrir' })).toBeInTheDocument()
  })

  it('T4: Mais detalhes navega para /tarefas/nova com o título', async () => {
    const user = userEvent.setup()
    renderDialog()

    await user.type(screen.getByLabelText(/^título$/i), 'Ler artigo')
    await user.click(screen.getByRole('button', { name: /mais detalhes/i }))

    expect(screen.getByTestId('local')).toHaveTextContent('/tarefas/nova:Ler artigo')
    expect(tarefaApi.criarTarefa).not.toHaveBeenCalled()
  })

  it('título vazio exibe erro inline e não chama API', async () => {
    const user = userEvent.setup()
    renderDialog()

    await user.click(screen.getByRole('button', { name: /criar tarefa/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/título/i)
    expect(tarefaApi.criarTarefa).not.toHaveBeenCalled()
  })

  it('título só com espaços não chama API', async () => {
    const user = userEvent.setup()
    renderDialog()
    await user.type(screen.getByLabelText(/^título$/i), '   ')
    await user.click(screen.getByRole('button', { name: /criar tarefa/i }))
    expect(await screen.findByRole('alert')).toHaveTextContent(/título/i)
    expect(tarefaApi.criarTarefa).not.toHaveBeenCalled()
  })

  it('mostra ApiError e preserva o título', async () => {
    const { ApiError } = await import('../auth/api/authApi')
    vi.mocked(tarefaApi.criarTarefa).mockRejectedValue(new ApiError(409, 'Título duplicado.'))
    const user = userEvent.setup()
    renderDialog()
    await user.type(screen.getByLabelText(/^título$/i), 'Ler docs')
    await user.click(screen.getByRole('button', { name: /criar tarefa/i }))
    expect(await screen.findByText('Título duplicado.')).toBeInTheDocument()
    expect(screen.getByLabelText(/^título$/i)).toHaveValue('Ler docs')
  })

  it('erro de rede usa fallback e não navega', async () => {
    vi.mocked(tarefaApi.criarTarefa).mockRejectedValue(new Error('offline'))
    const user = userEvent.setup()
    renderDialog()
    await user.type(screen.getByLabelText(/^título$/i), 'Ler docs')
    await user.click(screen.getByRole('button', { name: /criar tarefa/i }))
    expect(await screen.findByText(/não foi possível criar a tarefa/i)).toBeInTheDocument()
    expect(screen.getByTestId('local')).toHaveTextContent('/')
  })

  it('não renderiza o formulário quando fechado', () => {
    renderDialog(false)
    expect(screen.queryByLabelText(/^título$/i)).not.toBeInTheDocument()
  })
})
