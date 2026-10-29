import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../../auth/api/authApi'
import * as tarefaApi from '../api/tarefaApi'
import { TarefaFormPage } from './TarefaFormPage'

vi.mock('../api/tarefaApi', () => ({
  criarTarefa: vi.fn(),
  buscarTarefa: vi.fn(),
  atualizarTarefa: vi.fn(),
  excluirTarefa: vi.fn(),
  restaurarTarefa: vi.fn(),
}))

function prazoFuturoLocal(): string {
  const date = new Date(Date.now() + 2 * 60 * 60 * 1000)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function renderCriar() {
  return render(
    <MemoryRouter initialEntries={['/tarefas/nova']}>
      <Routes>
        <Route path="/tarefas/nova" element={<TarefaFormPage />} />
        <Route path="/tarefas" element={<p>Lista de tarefas</p>} />
      </Routes>
    </MemoryRouter>,
  )
}

function renderEditar() {
  return render(
    <MemoryRouter initialEntries={['/tarefas/t-1']}>
      <Routes>
        <Route path="/tarefas/:id" element={<TarefaFormPage />} />
        <Route path="/tarefas" element={<p>Lista de tarefas</p>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('TarefaFormPage', () => {
  beforeEach(() => {
    vi.mocked(tarefaApi.criarTarefa).mockReset()
    vi.mocked(tarefaApi.buscarTarefa).mockReset()
    vi.mocked(tarefaApi.atualizarTarefa).mockReset()
    vi.mocked(tarefaApi.excluirTarefa).mockReset()
    vi.mocked(tarefaApi.restaurarTarefa).mockReset()
  })

  it('no create não exibe select de status e cria tarefa válida', async () => {
    vi.mocked(tarefaApi.criarTarefa).mockResolvedValue({
      id: 't-1',
      titulo: 'Estudar',
      descricao: 'Spring Boot',
      prioridade: 'MEDIA',
      prazo: '2030-01-01T12:00:00Z',
      status: 'PENDENTE',
      duracaoMinutos: 120,
      createdAt: '2026-01-01T10:00:00Z',
      updatedAt: '2026-01-01T10:00:00Z',
      deletedAt: null,
      deletada: false,
    })

    const user = userEvent.setup()
    renderCriar()

    expect(screen.queryByLabelText(/^status$/i)).not.toBeInTheDocument()

    await user.type(screen.getByLabelText(/^título$/i), 'Estudar')
    await user.type(screen.getByLabelText(/descrição/i), 'Spring Boot')
    await user.selectOptions(screen.getByLabelText(/^prioridade$/i), 'MEDIA')
    await user.type(screen.getByLabelText(/prazo/i), prazoFuturoLocal())
    await user.type(screen.getByLabelText(/duração/i), '120')
    await user.click(screen.getByRole('button', { name: /criar tarefa/i }))

    await waitFor(() => {
      expect(tarefaApi.criarTarefa).toHaveBeenCalledWith(
        expect.objectContaining({
          titulo: 'Estudar',
          descricao: 'Spring Boot',
          prioridade: 'MEDIA',
          duracaoMinutos: 120,
        }),
      )
    })
    expect(await screen.findByText('Lista de tarefas')).toBeInTheDocument()
  })

  it('T4: permite criar tarefa sem prazo', async () => {
    const user = userEvent.setup()
    renderCriar()

    await user.type(screen.getByLabelText(/^título$/i), 'Sem prazo')
    await user.selectOptions(screen.getByLabelText(/^prioridade$/i), 'MEDIA')
    await user.type(screen.getByLabelText(/duração/i), '60')
    await user.click(screen.getByRole('button', { name: /criar tarefa/i }))

    await waitFor(() => {
      expect(tarefaApi.criarTarefa).toHaveBeenCalledWith(
        expect.objectContaining({
          titulo: 'Sem prazo',
          prioridade: 'MEDIA',
          prazo: null,
          duracaoMinutos: 60,
        }),
      )
    })
  })

  it('mostra erro de validação quando título está vazio', async () => {
    const user = userEvent.setup()
    renderCriar()

    await user.type(screen.getByLabelText(/descrição/i), 'Algo')
    await user.selectOptions(screen.getByLabelText(/^prioridade$/i), 'BAIXA')
    await user.type(screen.getByLabelText(/prazo/i), prazoFuturoLocal())
    await user.type(screen.getByLabelText(/duração/i), '60')
    await user.click(screen.getByRole('button', { name: /criar tarefa/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/título/i)
    expect(tarefaApi.criarTarefa).not.toHaveBeenCalled()
  })

  it('no edit carrega tarefa, mostra status e salva via PATCH', async () => {
    vi.mocked(tarefaApi.buscarTarefa).mockResolvedValue({
      id: 't-1',
      titulo: 'Estudar',
      descricao: 'Spring',
      prioridade: 'ALTA',
      prazo: '2030-06-01T15:00:00Z',
      status: 'PENDENTE',
      duracaoMinutos: 240,
      createdAt: '2026-01-01T10:00:00Z',
      updatedAt: '2026-01-01T10:00:00Z',
      deletedAt: null,
      deletada: false,
    })
    vi.mocked(tarefaApi.atualizarTarefa).mockResolvedValue({
      id: 't-1',
      titulo: 'Estudar mais',
      descricao: 'Spring',
      prioridade: 'ALTA',
      prazo: '2030-06-01T15:00:00Z',
      status: 'EM_PROGRESSO',
      duracaoMinutos: 240,
      createdAt: '2026-01-01T10:00:00Z',
      updatedAt: '2026-01-02T10:00:00Z',
      deletedAt: null,
      deletada: false,
    })

    const user = userEvent.setup()
    renderEditar()

    expect(await screen.findByDisplayValue('Estudar')).toBeInTheDocument()
    expect(screen.getByLabelText(/^status$/i)).toBeInTheDocument()

    await user.clear(screen.getByLabelText(/^título$/i))
    await user.type(screen.getByLabelText(/^título$/i), 'Estudar mais')
    await user.selectOptions(screen.getByLabelText(/^status$/i), 'EM_PROGRESSO')
    await user.click(screen.getByRole('button', { name: /^salvar$/i }))

    await waitFor(() => {
      expect(tarefaApi.atualizarTarefa).toHaveBeenCalledWith(
        't-1',
        expect.objectContaining({
          titulo: 'Estudar mais',
          status: 'EM_PROGRESSO',
        }),
      )
    })
    expect(await screen.findByText('Lista de tarefas')).toBeInTheDocument()
  })

  it('exclui tarefa ativa e volta à lista', async () => {
    vi.mocked(tarefaApi.buscarTarefa).mockResolvedValue({
      id: 't-1',
      titulo: 'Estudar',
      descricao: 'Spring',
      prioridade: 'ALTA',
      prazo: '2030-06-01T15:00:00Z',
      status: 'PENDENTE',
      duracaoMinutos: 240,
      createdAt: '2026-01-01T10:00:00Z',
      updatedAt: '2026-01-01T10:00:00Z',
      deletedAt: null,
      deletada: false,
    })
    vi.mocked(tarefaApi.excluirTarefa).mockResolvedValue(undefined)

    const user = userEvent.setup()
    renderEditar()

    expect(await screen.findByDisplayValue('Estudar')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /excluir tarefa/i }))

    await waitFor(() => {
      expect(tarefaApi.excluirTarefa).toHaveBeenCalledWith('t-1')
    })
    expect(await screen.findByText('Lista de tarefas')).toBeInTheDocument()
  })

  it('mostra restaurar quando a tarefa está deletada e restaura', async () => {
    vi.mocked(tarefaApi.buscarTarefa).mockResolvedValue({
      id: 't-1',
      titulo: 'Estudar',
      descricao: 'Spring',
      prioridade: 'ALTA',
      prazo: '2030-06-01T15:00:00Z',
      status: 'PENDENTE',
      duracaoMinutos: 240,
      createdAt: '2026-01-01T10:00:00Z',
      updatedAt: '2026-01-01T10:00:00Z',
      deletedAt: '2026-02-01T10:00:00Z',
      deletada: true,
    })
    vi.mocked(tarefaApi.restaurarTarefa).mockResolvedValue({
      id: 't-1',
      titulo: 'Estudar',
      descricao: 'Spring',
      prioridade: 'ALTA',
      prazo: '2030-06-01T15:00:00Z',
      status: 'PENDENTE',
      duracaoMinutos: 240,
      createdAt: '2026-01-01T10:00:00Z',
      updatedAt: '2026-02-02T10:00:00Z',
      deletedAt: null,
      deletada: false,
    })

    const user = userEvent.setup()
    renderEditar()

    expect(await screen.findByRole('status')).toHaveTextContent(/excluída/i)
    expect(screen.queryByRole('button', { name: /excluir tarefa/i })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /restaurar tarefa/i }))

    await waitFor(() => {
      expect(tarefaApi.restaurarTarefa).toHaveBeenCalledWith('t-1')
    })
    expect(await screen.findByRole('button', { name: /^salvar$/i })).toBeInTheDocument()
  })

  it('bloqueia ATRASADA com prazo futuro na validação local', async () => {
    vi.mocked(tarefaApi.buscarTarefa).mockResolvedValue({
      id: 't-1',
      titulo: 'Estudar',
      descricao: 'Spring',
      prioridade: 'ALTA',
      prazo: '2030-06-01T15:00:00Z',
      status: 'PENDENTE',
      duracaoMinutos: 240,
      createdAt: '2026-01-01T10:00:00Z',
      updatedAt: '2026-01-01T10:00:00Z',
      deletedAt: null,
      deletada: false,
    })

    const user = userEvent.setup()
    renderEditar()

    expect(await screen.findByDisplayValue('Estudar')).toBeInTheDocument()
    await user.selectOptions(screen.getByLabelText(/^status$/i), 'ATRASADA')
    await user.click(screen.getByRole('button', { name: /^salvar$/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/ATRASADA/i)
    expect(tarefaApi.atualizarTarefa).not.toHaveBeenCalled()
  })

  it('mostra detalhe de erro da API de status/prazo no save', async () => {
    vi.mocked(tarefaApi.buscarTarefa).mockResolvedValue({
      id: 't-1',
      titulo: 'Estudar',
      descricao: 'Spring',
      prioridade: 'ALTA',
      prazo: '2030-06-01T15:00:00Z',
      status: 'PENDENTE',
      duracaoMinutos: 240,
      createdAt: '2026-01-01T10:00:00Z',
      updatedAt: '2026-01-01T10:00:00Z',
      deletedAt: null,
      deletada: false,
    })
    vi.mocked(tarefaApi.atualizarTarefa).mockRejectedValue(
      new ApiError(400, 'O prazo não pode estar no passado.'),
    )

    const user = userEvent.setup()
    renderEditar()

    expect(await screen.findByDisplayValue('Estudar')).toBeInTheDocument()
    await user.clear(screen.getByLabelText(/^título$/i))
    await user.type(screen.getByLabelText(/^título$/i), 'Estudar API')
    await user.click(screen.getByRole('button', { name: /^salvar$/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/prazo não pode estar no passado/i)
  })
})
