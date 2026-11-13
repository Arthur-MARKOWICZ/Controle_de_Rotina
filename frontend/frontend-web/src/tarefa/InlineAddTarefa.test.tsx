import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../auth/api/authApi'
import { ToastProvider } from '../feedback/ToastProvider'
import * as tarefaApi from './api/tarefaApi'
import type { TarefaResponse } from './api/tarefaApi'
import { InlineAddTarefa } from './InlineAddTarefa'

vi.mock('./api/tarefaApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api/tarefaApi')>()
  return {
    ...actual,
    criarTarefa: vi.fn(),
  }
})

const criada: TarefaResponse = {
  id: 't-1',
  titulo: 'Estudar',
  descricao: '',
  prioridade: 'MEDIA',
  prazo: null,
  status: 'PENDENTE',
  duracaoMinutos: 30,
  createdAt: '2026-01-01T10:00:00Z',
  updatedAt: '2026-01-01T10:00:00Z',
  deletedAt: null,
  deletada: false,
}

const onCriada = vi.fn()

function renderAdd(projetoId?: string) {
  return render(
    <ToastProvider>
      <MemoryRouter>
        <InlineAddTarefa projetoId={projetoId} onCriada={onCriada} />
      </MemoryRouter>
    </ToastProvider>,
  )
}

describe('InlineAddTarefa', () => {
  beforeEach(() => {
    onCriada.mockReset()
    vi.mocked(tarefaApi.criarTarefa).mockReset()
  })

  it('cria tarefa com prioridade e duração padrão', async () => {
    vi.mocked(tarefaApi.criarTarefa).mockResolvedValue(criada)
    const user = userEvent.setup()
    renderAdd()

    await user.type(screen.getByPlaceholderText('Adicionar tarefa…'), 'Estudar')
    await user.click(screen.getByRole('button', { name: /adicionar/i }))

    await waitFor(() => {
      expect(tarefaApi.criarTarefa).toHaveBeenCalledWith({
        titulo: 'Estudar',
        prioridade: 'MEDIA',
        duracaoMinutos: 30,
      })
    })
    expect(onCriada).toHaveBeenCalledWith(criada)
    expect(screen.getByPlaceholderText('Adicionar tarefa…')).toHaveValue('')
  })

  it('inclui projetoId do filtro ativo', async () => {
    vi.mocked(tarefaApi.criarTarefa).mockResolvedValue({ ...criada, projetoId: 'p-1' })
    const user = userEvent.setup()
    renderAdd('p-1')

    await user.type(screen.getByPlaceholderText('Adicionar tarefa…'), 'Estudar')
    await user.click(screen.getByRole('button', { name: /adicionar/i }))

    await waitFor(() => {
      expect(tarefaApi.criarTarefa).toHaveBeenCalledWith(
        expect.objectContaining({ projetoId: 'p-1' }),
      )
    })
  })

  it('não cria com título vazio (estado inalterado)', async () => {
    const user = userEvent.setup()
    renderAdd()

    await user.click(screen.getByRole('button', { name: /adicionar/i }))

    expect(await screen.findByText('Informe um título.')).toBeInTheDocument()
    expect(tarefaApi.criarTarefa).not.toHaveBeenCalled()
    expect(onCriada).not.toHaveBeenCalled()
  })

  it('não cria com título só de espaços', async () => {
    const user = userEvent.setup()
    renderAdd()

    await user.type(screen.getByPlaceholderText('Adicionar tarefa…'), '   ')
    await user.click(screen.getByRole('button', { name: /adicionar/i }))

    expect(await screen.findByText('Informe um título.')).toBeInTheDocument()
    expect(tarefaApi.criarTarefa).not.toHaveBeenCalled()
  })

  it('limpa o campo com Escape sem chamar a API', async () => {
    const user = userEvent.setup()
    renderAdd()
    const campo = screen.getByPlaceholderText('Adicionar tarefa…')
    await user.type(campo, 'Rascunho')
    await user.keyboard('{Escape}')

    expect(campo).toHaveValue('')
    expect(tarefaApi.criarTarefa).not.toHaveBeenCalled()
  })

  it('mostra erro da API e preserva o título', async () => {
    vi.mocked(tarefaApi.criarTarefa).mockRejectedValue(new ApiError(400, 'Título inválido'))
    const user = userEvent.setup()
    renderAdd()

    await user.type(screen.getByPlaceholderText('Adicionar tarefa…'), 'Estudar')
    await user.click(screen.getByRole('button', { name: /adicionar/i }))

    expect(await screen.findByText('Título inválido')).toBeInTheDocument()
    expect(screen.getByPlaceholderText('Adicionar tarefa…')).toHaveValue('Estudar')
    expect(onCriada).not.toHaveBeenCalled()
  })

  it('mostra fallback quando o erro não é ApiError', async () => {
    vi.mocked(tarefaApi.criarTarefa).mockRejectedValue(new TypeError('Failed to fetch'))
    const user = userEvent.setup()
    renderAdd()

    await user.type(screen.getByPlaceholderText('Adicionar tarefa…'), 'Estudar')
    await user.click(screen.getByRole('button', { name: /adicionar/i }))

    expect(await screen.findByText('Não foi possível criar a tarefa. Tente novamente.')).toBeInTheDocument()
    expect(onCriada).not.toHaveBeenCalled()
  })
})
