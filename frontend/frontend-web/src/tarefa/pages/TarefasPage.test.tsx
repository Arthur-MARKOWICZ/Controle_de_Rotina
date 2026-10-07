import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as tarefaApi from '../api/tarefaApi'
import { TarefasPage } from './TarefasPage'

vi.mock('../api/tarefaApi', () => ({
  listarTarefas: vi.fn(),
  excluirTarefa: vi.fn(),
  restaurarTarefa: vi.fn(),
}))

const pagina = {
  content: [
    {
      id: 't-1',
      titulo: 'Estudar Spring',
      descricao: 'Revisão',
      prioridade: 'ALTA' as const,
      prazo: '2030-01-01T12:00:00Z',
      status: 'ATRASADA' as const,
      duracaoHoras: 3,
      createdAt: '2026-01-01T10:00:00Z',
      updatedAt: '2026-01-01T10:00:00Z',
      deletedAt: null,
      deletada: false,
    },
  ],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 20,
}

function renderLista() {
  return render(
    <MemoryRouter initialEntries={['/tarefas']}>
      <Routes>
        <Route path="/tarefas" element={<TarefasPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('TarefasPage', () => {
  beforeEach(() => {
    vi.mocked(tarefaApi.listarTarefas).mockReset()
    vi.mocked(tarefaApi.excluirTarefa).mockReset()
    vi.mocked(tarefaApi.restaurarTarefa).mockReset()
    vi.mocked(tarefaApi.listarTarefas).mockResolvedValue(pagina)
  })

  it('lista tarefas autenticadas e mostra status visualmente', async () => {
    renderLista()

    expect(await screen.findByText('Estudar Spring')).toBeInTheDocument()
    expect(document.querySelector('.status-atrasada')).toHaveTextContent('Atrasada')
    expect(tarefaApi.listarTarefas).toHaveBeenCalledWith(
      expect.objectContaining({
        page: 0,
        size: 20,
        sort: 'prazo',
        direction: 'asc',
      }),
    )
  })

  it('mostra só status e prioridade até expandir mais filtros', async () => {
    renderLista()
    await screen.findByText('Estudar Spring')

    expect(screen.getByLabelText(/^pendente$/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/^alta$/i)).toBeInTheDocument()
    expect(screen.queryByLabelText(/ordenar por/i)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/^excluídas$/i)).not.toBeInTheDocument()
  })

  it('aplica filtros multi-valor e ordenação na listagem', async () => {
    const user = userEvent.setup()
    renderLista()
    await screen.findByText('Estudar Spring')

    await user.click(screen.getByLabelText(/^pendente$/i))
    await user.click(screen.getByLabelText(/^alta$/i))
    await user.click(screen.getByRole('button', { name: /mostrar mais filtros/i }))
    await user.selectOptions(screen.getByLabelText(/ordenar por/i), 'duracao')
    await user.selectOptions(screen.getByLabelText(/^direção$/i), 'desc')
    await user.click(screen.getByRole('button', { name: /aplicar filtros/i }))

    await waitFor(() => {
      expect(tarefaApi.listarTarefas).toHaveBeenLastCalledWith(
        expect.objectContaining({
          status: ['PENDENTE'],
          prioridade: ['ALTA'],
          sort: 'duracao',
          direction: 'desc',
          page: 0,
        }),
      )
    })
  })

  it('exclui tarefa da lista e recarrega', async () => {
    vi.mocked(tarefaApi.excluirTarefa).mockResolvedValue(undefined)
    vi.mocked(tarefaApi.listarTarefas)
      .mockResolvedValueOnce(pagina)
      .mockResolvedValueOnce({ ...pagina, content: [], totalElements: 0, totalPages: 0 })

    const user = userEvent.setup()
    renderLista()
    await screen.findByText('Estudar Spring')

    await user.click(screen.getByRole('button', { name: /^excluir$/i }))

    await waitFor(() => {
      expect(tarefaApi.excluirTarefa).toHaveBeenCalledWith('t-1')
    })
    await waitFor(() => {
      expect(tarefaApi.listarTarefas).toHaveBeenCalledTimes(2)
    })
  })

  it('filtra somente excluídas e restaura na lista', async () => {
    const deletada = {
      ...pagina.content[0],
      deletada: true,
      deletedAt: '2026-02-01T10:00:00Z',
    }
    vi.mocked(tarefaApi.listarTarefas)
      .mockResolvedValueOnce(pagina)
      .mockResolvedValueOnce({
        ...pagina,
        content: [deletada],
      })
      .mockResolvedValueOnce(pagina)
    vi.mocked(tarefaApi.restaurarTarefa).mockResolvedValue({
      ...deletada,
      deletada: false,
      deletedAt: null,
    })

    const user = userEvent.setup()
    renderLista()
    await screen.findByText('Estudar Spring')

    await user.click(screen.getByRole('button', { name: /mostrar mais filtros/i }))
    await user.selectOptions(screen.getByLabelText(/^excluídas$/i), 'somente')
    await user.click(screen.getByRole('button', { name: /aplicar filtros/i }))

    await waitFor(() => {
      expect(tarefaApi.listarTarefas).toHaveBeenLastCalledWith(
        expect.objectContaining({
          somenteDeletadas: true,
        }),
      )
    })

    expect(await screen.findByText('Excluída')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /^restaurar$/i }))

    await waitFor(() => {
      expect(tarefaApi.restaurarTarefa).toHaveBeenCalledWith('t-1')
    })
  })
})
